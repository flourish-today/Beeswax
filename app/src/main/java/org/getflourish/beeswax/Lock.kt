// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

// The lock. While locked, the apps blocked at Lock (by default the browsers it finds) and the apps hidden
// from the list stay hidden, and every app installed after that is hidden until it's shown. Updates keep
// an app's first install time, so they go through.
object Lock {
    private const val LOCKED = "locked"
    private const val BLOCKED = "blocked"
    private const val APPROVED = "approved"
    private const val QUARANTINED = "quarantined"

    fun isLocked(context: Context) = prefs(context).getBoolean(LOCKED, false)

    // Test builds leave debugging on, so the test phone stays reachable over adb while locked.
    fun isTestBuild(context: Context) = context.packageName.endsWith(".test")

    fun blocked(context: Context) = set(context, BLOCKED)

    fun quarantined(context: Context) = set(context, QUARANTINED)

    // Synchronized, with the "already locked" check inside, so two Lock dialogs confirmed at once lock only once.
    // A new code and the plan from the list before Lock (package name to switch, only the switches changed)
    // are applied here, in the same step: browsers switched on stay visible, and apps switched off are hidden
    // by name, like the list's hide(). With no plan, every browser is hidden. Essential apps never are.
    @Synchronized
    fun lock(context: Context, code: String, plan: Map<String, Boolean>) {
        if (isLocked(context)) return
        Code.save(context, code)
        val blocked = browsers(context) - plan.filterValues { it }.keys + plan.filterValues { !it }.keys - essential(context)
        val approved = userApps(context).filter { it.packageName !in blocked }.map { fingerprint(it) }.toSet()
        // No new users, since a new user would have a browser and an app store.
        for (restriction in restrictions(context)) dpm(context).addUserRestriction(admin(context), restriction)
        prefs(context).edit()
            .putBoolean(LOCKED, true)
            .putStringSet(BLOCKED, blocked)
            .putStringSet(APPROVED, approved)
            .putStringSet(QUARANTINED, emptySet())
            .commit()
        // Android starts Guard when its component is enabled, and keeps it running from then on.
        setGuard(context, true)
        enforce(context)
    }

    // Run by Deactivate. The lists are cleared only after everything is visible again.
    @Synchronized
    fun unlock(context: Context) {
        // Unlock first, so the "package added" broadcasts from unhiding don't hide anything again.
        prefs(context).edit().putBoolean(LOCKED, false).commit()
        setGuard(context, false)
        for (restriction in restrictions(context)) dpm(context).clearUserRestriction(admin(context), restriction)
        // Every hidden app, not only the saved lists, so nothing stays hidden if a list was lost.
        val all = context.packageManager.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()),
        )
        for (info in all) {
            if (dpm(context).isApplicationHidden(admin(context), info.packageName)) setHidden(context, info.packageName, false)
        }
        prefs(context).edit().clear().commit()
    }

    @Synchronized
    fun show(context: Context, packageName: String) {
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES) ?: return
        // Approve before unhiding, because unhiding sends a "package added" broadcast that Guard checks.
        prefs(context).edit()
            .putStringSet(APPROVED, set(context, APPROVED) + fingerprint(info))
            .putStringSet(BLOCKED, blocked(context) - packageName)
            .putStringSet(QUARANTINED, quarantined(context) - packageName)
            .commit()
        setHidden(context, packageName, false)
    }

    // Hidden by name, like the browsers found at lock time, so it's hidden again after a restart or a
    // reinstall. Never an essential app.
    @Synchronized
    fun hide(context: Context, packageName: String) {
        if (!isLocked(context) || packageName in essential(context)) return
        prefs(context).edit()
            .putStringSet(BLOCKED, blocked(context) + packageName)
            .putStringSet(QUARANTINED, quarantined(context) - packageName)
            .commit()
        setHidden(context, packageName, true)
    }

    // New code, from the list. Synchronized so it can't run in the middle of Deactivate's unlock.
    @Synchronized
    fun newCode(context: Context, code: String) {
        if (isLocked(context)) Code.save(context, code)
    }

    // The list: every installed app that isn't part of the system, every browser and every app with a
    // home-screen icon (built-in ones too, so they can be blocked) and anything Beeswax hid, hidden or not.
    // Never an essential app.
    fun listed(context: Context): List<String> {
        val hidden = PackageManager.MATCH_UNINSTALLED_PACKAGES
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val builtIn = browsers(context, hidden) + activities(context, launcher, hidden).map { it.activityInfo.packageName } +
            blocked(context) + quarantined(context)
        val installed = context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(hidden.toLong()))
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0 }
        return installed.filter { isUserApp(context, it) || it.packageName in builtIn }.map { it.packageName } -
            essential(context)
    }

    fun isHidden(context: Context, packageName: String) = dpm(context).isApplicationHidden(admin(context), packageName)

    // Runs whenever Guard starts, which catches anything installed while it wasn't running.
    @Synchronized
    fun enforce(context: Context) {
        if (!isLocked(context)) return
        for (packageName in blocked(context) - essential(context)) setHidden(context, packageName, true)
        for (info in userApps(context)) check(context, info)
    }

    // Runs for each "package added" broadcast, for new installs and updates alike.
    @Synchronized
    fun check(context: Context, packageName: String) {
        if (!isLocked(context)) return
        if (packageName in blocked(context)) {
            if (packageName !in essential(context)) setHidden(context, packageName, true)
            return
        }
        // Include hidden apps: Android re-hides a reinstalled app that a device owner hid before,
        // and it still needs to be listed so it can be shown.
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES) ?: return
        if (isUserApp(context, info)) check(context, info)
    }

    fun label(context: Context, packageName: String): CharSequence {
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        return info?.applicationInfo?.loadLabel(context.packageManager) ?: packageName
    }

    fun icon(context: Context, packageName: String): Drawable? {
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        return info?.applicationInfo?.loadIcon(context.packageManager)
    }

    // Other apps that are active device admins. Android won't hide them, so Lock refuses while any exist.
    fun otherAdmins(context: Context) = dpm(context).activeAdmins.orEmpty().map { it.packageName }.distinct().filter {
        packageInfo(context, it, PackageManager.MATCH_UNINSTALLED_PACKAGES)?.let { info -> isUserApp(context, info) } == true
    }

    // The browsers Lock blocks by default, hidden or not, so the list starts the same ones switched off.
    fun detectedBrowsers(context: Context) = browsers(context, PackageManager.MATCH_UNINSTALLED_PACKAGES)

    private fun check(context: Context, info: PackageInfo) {
        if (fingerprint(info) in set(context, APPROVED)) return
        prefs(context).edit().putStringSet(QUARANTINED, quarantined(context) + info.packageName).commit()
        setHidden(context, info.packageName, true)
    }

    // An app's name plus when it was first installed. Uninstalling an allowed app and installing
    // something else under the same name changes the time, so the new app is quarantined.
    private fun fingerprint(info: PackageInfo) = "${info.packageName}:${info.firstInstallTime}"

    // Browsers: apps that call themselves a browser, plus installed apps that can open any web link.
    // Built-in apps that open web links without being browsers, like Carrier Communications for
    // carrier sign-in pages, are left alone. Disabled apps count too, since they could be switched
    // on again in Settings after locking.
    private fun browsers(context: Context, extraFlags: Int = 0): Set<String> {
        val (main, web) = browserTests(context, extraFlags)
        val found = main + web.filter { !isSystem(it.activityInfo.applicationInfo) }
        return found.map { it.activityInfo.packageName }.toSet() - context.packageName
    }

    // Apps that call themselves a browser, and apps that can open any web link.
    private fun browserTests(context: Context, extraFlags: Int): Pair<List<ResolveInfo>, List<ResolveInfo>> {
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        return activities(context, main, extraFlags) to activities(context, web, extraFlags)
    }

    // Disabled apps and components count too, since they could be switched on again in Settings after locking.
    private fun activities(context: Context, intent: Intent, extraFlags: Int): List<ResolveInfo> {
        val flags = PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or extraFlags
        return context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    }

    // Installed, visible apps that aren't part of the system and aren't Beeswax. System apps come
    // only with OS updates, so they're never quarantined; browsers among them are hidden by name.
    private fun userApps(context: Context) = context.packageManager
        .getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        .filter { isUserApp(context, it) }

    private fun isUserApp(context: Context, info: PackageInfo) = info.packageName != context.packageName &&
        !isSystem(info.applicationInfo)

    private fun isSystem(info: ApplicationInfo?) = info == null || info.flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun packageInfo(context: Context, packageName: String, flags: Int) = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun restrictions(context: Context) = if (isTestBuild(context)) {
        listOf(UserManager.DISALLOW_ADD_USER)
    } else {
        listOf(UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_DEBUGGING_FEATURES)
    }

    // Never listed or blocked: Beeswax and enabled keyboards, so the code can always be typed, and the built-in
    // phone, SMS and home apps in use and Settings, so calls, texts and the home screen keep working and factory
    // reset stays reachable. Read each time, so a newly chosen default counts at once. Only built-in, visible
    // ones count, so an installed app can't be made unblockable by choosing it as a default.
    private fun essential(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val settings = Intent(Settings.ACTION_SETTINGS)
        val only = { flag: Int -> PackageManager.ResolveInfoFlags.of(flag.toLong()) }
        // A phone without calls or texts may have no default for them, or throw. Guard runs this, so it mustn't.
        val defaults = listOfNotNull(
            runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
            context.packageManager.resolveActivity(home, only(PackageManager.MATCH_DEFAULT_ONLY))?.activityInfo?.packageName,
        ) + context.packageManager.queryIntentActivities(settings, only(PackageManager.MATCH_SYSTEM_ONLY)).map { it.activityInfo.packageName }
        val builtIn = defaults.filter { packageInfo(context, it, 0)?.let { info -> isSystem(info.applicationInfo) } == true }
        val keyboards = context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        return (builtIn + keyboards + context.packageName).toSet()
    }

    private fun setHidden(context: Context, packageName: String, hidden: Boolean) {
        dpm(context).setApplicationHidden(admin(context), packageName, hidden)
    }

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context) = ComponentName(context, Receiver::class.java)

    private fun setGuard(context: Context, on: Boolean) = context.packageManager.setComponentEnabledSetting(
        ComponentName(context, Guard::class.java),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )

    // The screen listens to these to refresh as soon as Guard quarantines an app.
    fun prefs(context: Context) = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    private fun set(context: Context, key: String): Set<String> = prefs(context).getStringSet(key, null)?.toSet() ?: emptySet()
}
