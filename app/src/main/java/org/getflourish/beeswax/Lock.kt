// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserManager

// The lock. While locked, the browsers found at lock time stay hidden, and every app installed
// after that is hidden until it's allowed. Updates keep an app's first install time, so they go through.
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

    // Quarantined apps that are still installed, for the screen's Allow list.
    fun waiting(context: Context) = quarantined(context).filter {
        packageInfo(context, it, PackageManager.MATCH_UNINSTALLED_PACKAGES) != null
    }

    // Synchronized, with the "already locked" check inside, so two Lock dialogs confirmed at once lock only once.
    // A new code is saved here, in the same step.
    @Synchronized
    fun lock(context: Context, code: String? = null) {
        if (isLocked(context)) return
        if (code != null) Code.save(context, code)
        val blocked = browsers(context)
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

    // Also run by Deactivate. The lists are cleared only after everything is visible again.
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

    fun allow(context: Context, packageName: String) {
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES) ?: return
        // Approve before unhiding, because unhiding sends a "package added" broadcast that Guard checks.
        prefs(context).edit()
            .putStringSet(APPROVED, set(context, APPROVED) + fingerprint(info))
            .putStringSet(QUARANTINED, quarantined(context) - packageName)
            .commit()
        setHidden(context, packageName, false)
    }

    // Runs whenever Guard starts, which catches anything installed while it wasn't running.
    fun enforce(context: Context) {
        if (!isLocked(context)) return
        for (packageName in blocked(context)) setHidden(context, packageName, true)
        for (info in userApps(context)) check(context, info)
    }

    // Runs for each "package added" broadcast, for new installs and updates alike.
    fun check(context: Context, packageName: String) {
        if (!isLocked(context)) return
        if (packageName in blocked(context)) {
            setHidden(context, packageName, true)
            return
        }
        // Include hidden apps: Android re-hides a reinstalled app that a device owner hid before,
        // and it still needs to be listed so it can be allowed.
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES) ?: return
        if (isUserApp(context, info)) check(context, info)
    }

    fun label(context: Context, packageName: String): CharSequence {
        val info = packageInfo(context, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        return info?.applicationInfo?.loadLabel(context.packageManager) ?: packageName
    }

    // Other apps that are active device admins. Android won't hide them, so Lock refuses while any exist.
    fun otherAdmins(context: Context) = dpm(context).activeAdmins.orEmpty().map { it.packageName }.distinct().filter {
        packageInfo(context, it, PackageManager.MATCH_UNINSTALLED_PACKAGES)?.let { info -> isUserApp(context, info) } == true
    }

    // The same web-link test as browsers(), for one app, hidden or not.
    fun opensWeb(context: Context, packageName: String) = context.packageManager.queryIntentActivities(
        webIntent().setPackage(packageName),
        PackageManager.ResolveInfoFlags.of((browserFlags() or PackageManager.MATCH_UNINSTALLED_PACKAGES).toLong()),
    ).isNotEmpty()

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
    private fun browsers(context: Context): Set<String> {
        val flags = PackageManager.ResolveInfoFlags.of(browserFlags().toLong())
        val web = webIntent()
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        val found = context.packageManager.queryIntentActivities(main, flags) +
            context.packageManager.queryIntentActivities(web, flags).filter { !isSystem(it.activityInfo.applicationInfo) }
        return found.map { it.activityInfo.packageName }.toSet() - context.packageName
    }

    private fun browserFlags() = PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS

    private fun webIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)

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
