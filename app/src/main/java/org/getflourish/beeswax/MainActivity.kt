// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    // Set only by typing the right code, and cleared whenever the screen is left.
    private var listOpen = false
    private val switches = mutableListOf<Switch>()

    // The switches changed before the first Lock: package name to on. Only a plan, kept in memory and applied
    // by Lock. Cleared once locked or no longer device owner.
    private val plan = mutableMapOf<String, Boolean>()

    // Refresh as soon as Guard quarantines an app, an app is uninstalled, or Beeswax becomes
    // device owner, not only on reopening.
    private val lockListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        // Release builds block screenshots and screen recording, so the code can't be recorded as it's typed.
        // Test builds allow them, for testing.
        if (!Lock.isTestBuild(this)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dpm = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, Receiver::class.java)
        // Android draws apps edge to edge, so keep the content clear of the system bars and keyboard.
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            val bars = insets.getInsets(types)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.title).text = applicationInfo.loadLabel(packageManager)
        findViewById<TextView>(R.id.adb_command).text = "adb shell dpm set-device-owner ${admin.flattenToShortString()}"
        findViewById<Button>(R.id.deactivate).setOnClickListener { confirmDeactivate() }
        findViewById<Button>(R.id.lock).setOnClickListener {
            if (canLock()) showNewCode()
        }
        findViewById<Button>(R.id.open_list).setOnClickListener { openList() }
        findViewById<Button>(R.id.close_list).setOnClickListener {
            closeList()
            refresh()
        }
        findViewById<Button>(R.id.new_code).setOnClickListener { showNewCode() }
    }

    override fun onResume() {
        super.onResume()
        Lock.prefs(this).registerOnSharedPreferenceChangeListener(lockListener)
        registerReceiver(refreshReceiver, IntentFilter(DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        val removed = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        registerReceiver(refreshReceiver, removed, Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onPause() {
        Lock.prefs(this).unregisterOnSharedPreferenceChangeListener(lockListener)
        unregisterReceiver(refreshReceiver)
        closeList()
        super.onPause()
    }

    // Leaving the screen, or Lock at the top of the open list, closes the list and clears the code.
    // Beeswax stays locked.
    private fun closeList() {
        listOpen = false
        findViewById<EditText>(R.id.code).text.clear()
    }

    private fun refresh() {
        val owner = dpm.isDeviceOwnerApp(packageName)
        if (!owner) plan.clear()
        findViewById<View>(R.id.status).visibility = shownIf(!owner)
        findViewById<View>(R.id.adb_command).visibility = shownIf(!owner)
        findViewById<View>(R.id.owner_controls).visibility = shownIf(owner)
        if (owner) {
            val backupsOn = try {
                dpm.setBackupServiceEnabled(admin, true)
                true
            } catch (e: Exception) {
                false
            }
            findViewById<View>(R.id.backups_failed).visibility = if (backupsOn) View.GONE else View.VISIBLE
            refreshLock()
        }
    }

    private fun refreshLock() {
        val locked = Lock.isLocked(this)
        if (locked) plan.clear() else listOpen = false
        findViewById<TextView>(R.id.lock_status).setText(if (locked) R.string.lock_on else R.string.lock_off)
        findViewById<View>(R.id.lock_status).visibility = shownIf(!listOpen)
        findViewById<View>(R.id.lock_test_note).visibility = shownIf(Lock.isTestBuild(this))
        // Before the first Lock, the list shows as the plan for it. While locked, only the code field shows
        // until the right code opens the list. Deactivate shows before the first Lock and in the open list.
        findViewById<View>(R.id.close_list).visibility = shownIf(listOpen)
        findViewById<View>(R.id.lock).visibility = shownIf(!locked)
        findViewById<View>(R.id.code).visibility = shownIf(locked && !listOpen)
        findViewById<View>(R.id.open_list).visibility = shownIf(locked && !listOpen)
        findViewById<View>(R.id.new_code).visibility = shownIf(listOpen)
        findViewById<View>(R.id.deactivate).visibility = shownIf(!locked || listOpen)
        val list = findViewById<LinearLayout>(R.id.app_list)
        list.removeAllViews()
        switches.clear()
        if (locked && !listOpen) return
        val browsers = Lock.detectedBrowsers(this)
        val apps = Lock.listed(this).sortedBy { Lock.label(this, it).toString().lowercase() }.map { it to (it in browsers) }
        // Locked, a switch is on while the app is visible. Before the first Lock it shows the plan, and browsers start off.
        val (visible, hidden) = apps.partition { (packageName, web) ->
            if (locked) !Lock.isHidden(this, packageName) else plan[packageName] ?: !web
        }
        addHeading(list, R.string.list_hidden)
        for ((packageName, web) in hidden) addRow(list, packageName, web, false)
        addHeading(list, R.string.list_visible)
        for ((packageName, web) in visible) addRow(list, packageName, web, true)
    }

    private fun shownIf(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

    private fun addHeading(list: LinearLayout, text: Int) {
        val heading = TextView(this)
        heading.setText(text)
        heading.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
        heading.setPadding(0, dp(24), 0, dp(8))
        list.addView(heading)
    }

    // One app: its icon, its name with its package name below (and a note if it's a browser), and its switch.
    private fun addRow(list: LinearLayout, packageName: String, web: Boolean, visible: Boolean) {
        val icon = ImageView(this)
        icon.setImageDrawable(Lock.icon(this, packageName))
        val name = TextView(this)
        name.text = Lock.label(this, packageName)
        val details = TextView(this)
        details.text = if (web) packageName + "\n" + getString(R.string.app_opens_web) else packageName
        details.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        text.addView(name)
        text.addView(details)
        val switch = Switch(this)
        switch.isChecked = visible
        switch.contentDescription = name.text
        switch.setOnCheckedChangeListener { _, on ->
            when {
                !Lock.isLocked(this) -> {
                    plan[packageName] = on
                    refresh()
                }
                !listOpen -> refresh()
                else -> inBackground {
                    if (on) Lock.show(this, packageName) else Lock.hide(this, packageName)
                    null
                }
            }
        }
        switches.add(switch)
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(8), 0, dp(8))
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch)
        list.addView(row)
    }

    // The right code opens the list. Checking it is one hash, quick enough for the main thread.
    private fun openList() {
        val field = findViewById<EditText>(R.id.code)
        if (Code.matches(this, field.text.toString())) {
            listOpen = true
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        } else {
            Toast.makeText(this, R.string.code_wrong, Toast.LENGTH_LONG).show()
        }
        field.text.clear()
        refresh()
    }

    // Android won't hide an app that is a device admin, and other users have their own browsers.
    private fun canLock(): Boolean {
        val admins = Lock.otherAdmins(this)
        val message = when {
            admins.isNotEmpty() -> getString(R.string.lock_other_admins, admins.map { Lock.label(this, it) }.joinToString())
            dpm.getSecondaryUsers(admin).isNotEmpty() -> getString(R.string.lock_other_users)
            else -> return true
        }
        show(AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok, null))
        return false
    }

    // A new code is shown, then has to be typed back before anything locks or it's saved, so it's known to be
    // saved. If the typed code doesn't match, the same code is shown again so the saved copy can be checked.
    private fun showNewCode(code: String = Code.create()) {
        val view = TextView(this)
        view.text = code
        view.typeface = Typeface.MONOSPACE
        view.textSize = 24f
        view.setTextIsSelectable(true)
        view.setPadding(dp(24), dp(12), dp(24), 0)
        // From New code in the list, it also says the current code stays until the new one is confirmed.
        val message = getString(R.string.code_message)
        show(AlertDialog.Builder(this)
            .setTitle(R.string.code_title)
            .setMessage(if (Lock.isLocked(this)) getString(R.string.new_code_message) + "\n\n" + message else message)
            .setView(view)
            .setPositiveButton(R.string.code_saved) { _, _ -> checkNewCode(code) }
            .setNegativeButton(android.R.string.cancel, null))
    }

    // Each dialog checks again when confirmed, since it may have been left open while another copy locked,
    // or the list closed.
    private fun checkNewCode(code: String) {
        val fromList = Lock.isLocked(this)
        val field = EditText(this)
        field.typeface = Typeface.MONOSPACE
        field.setHint(R.string.code_hint)
        field.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val frame = FrameLayout(this)
        frame.setPadding(dp(24), 0, dp(24), 0)
        frame.addView(field)
        show(AlertDialog.Builder(this)
            .setTitle(R.string.code_check_title)
            .setView(frame)
            .setPositiveButton(if (fromList) R.string.code_save else R.string.lock) { _, _ ->
                if (Lock.isLocked(this) != fromList || (fromList && !listOpen)) {
                    refresh()
                } else if (Code.same(code, field.text.toString())) {
                    // A copy, so the background step gets the plan as it is now.
                    val plan = plan.toMap()
                    inBackground {
                        if (fromList) Lock.newCode(this, code) else Lock.lock(this, code, plan)
                        null
                    }
                } else {
                    Toast.makeText(this, R.string.code_mismatch, Toast.LENGTH_LONG).show()
                    showNewCode(code)
                }
            }
            .setNegativeButton(android.R.string.cancel, null))
    }

    // Dialogs have their own windows, so each needs the screen's block on screenshots too.
    private fun show(builder: AlertDialog.Builder) {
        val dialog = builder.create()
        if (!Lock.isTestBuild(this)) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // Locking goes through every app, so it and the list's actions run off the main thread.
    // The task returns a message to show, or null.
    private fun inBackground(task: () -> String?) {
        setButtonsEnabled(false)
        Thread {
            val message = try {
                task()
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                findViewById<EditText>(R.id.code).text.clear()
                setButtonsEnabled(true)
                if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                refresh()
            }
        }.start()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        findViewById<View>(R.id.lock).isEnabled = enabled
        findViewById<View>(R.id.new_code).isEnabled = enabled
        for (switch in switches) switch.isEnabled = enabled
    }

    @Suppress("DEPRECATION") // Still the only way for a device owner to remove itself.
    private fun confirmDeactivate() {
        show(AlertDialog.Builder(this)
            .setTitle(R.string.deactivate_confirm_title)
            .setMessage(R.string.deactivate_confirm_message)
            .setPositiveButton(R.string.deactivate) { _, _ ->
                if (Lock.isLocked(this) && !listOpen) return@setPositiveButton refresh()
                try {
                    // Only the device owner can unhide apps, so make sure nothing is left hidden
                    // before giving that up.
                    Lock.unlock(this)
                    Code.clear(this)
                    dpm.clearDeviceOwnerApp(packageName)
                } catch (e: Exception) {
                    val error = e.message ?: e.javaClass.simpleName
                    Toast.makeText(this, getString(R.string.deactivate_failed, error), Toast.LENGTH_LONG).show()
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null))
    }
}
