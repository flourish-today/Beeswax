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
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

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
            if (!canLock()) return@setOnClickListener
            if (Code.exists(this)) confirmLock() else showNewCode()
        }
        // Unlock keeps the code, so the next Lock can use it again.
        findViewById<Button>(R.id.unlock).setOnClickListener { withCode { Lock.unlock(this) } }
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
        super.onPause()
    }

    private fun refresh() {
        val owner = dpm.isDeviceOwnerApp(packageName)
        findViewById<TextView>(R.id.status).setText(if (owner) R.string.status_device_owner else R.string.status_not_device_owner)
        findViewById<View>(R.id.adb_command).visibility = if (owner) View.GONE else View.VISIBLE
        findViewById<View>(R.id.owner_controls).visibility = if (owner) View.VISIBLE else View.GONE
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
        val browsers = Lock.blocked(this).map { Lock.label(this, it) }.sortedBy { it.toString() }
        findViewById<TextView>(R.id.lock_status).text = if (locked) {
            getString(R.string.lock_on, browsers.joinToString().ifEmpty { getString(R.string.lock_none) })
        } else {
            getString(R.string.lock_off)
        }
        findViewById<View>(R.id.lock_test_note).visibility = if (Lock.isTestBuild(this)) View.VISIBLE else View.GONE
        // While locked, only the code field, Unlock and Allow are shown. Deactivate needs Unlock first.
        val unlockedOnly = if (locked) View.GONE else View.VISIBLE
        val lockedOnly = if (locked) View.VISIBLE else View.GONE
        findViewById<View>(R.id.code).visibility = lockedOnly
        findViewById<View>(R.id.lock).visibility = unlockedOnly
        findViewById<View>(R.id.exit_controls).visibility = unlockedOnly
        findViewById<View>(R.id.unlock).visibility = lockedOnly
        val list = findViewById<LinearLayout>(R.id.quarantine)
        list.removeAllViews()
        for (packageName in Lock.waiting(this).sorted()) {
            val button = Button(this)
            val text = if (Lock.opensWeb(this, packageName)) R.string.allow_app_web else R.string.allow_app
            button.text = getString(text, Lock.label(this, packageName), packageName)
            button.setOnClickListener { withCode { Lock.allow(this, packageName) } }
            list.addView(button)
        }
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

    // Each dialog checks again when confirmed, since it may have been left open while another copy locked.
    private fun confirmLock() {
        show(AlertDialog.Builder(this)
            .setTitle(R.string.relock_title)
            .setMessage(R.string.relock_message)
            .setPositiveButton(R.string.lock) { _, _ ->
                if (Lock.isLocked(this)) return@setPositiveButton refresh()
                inBackground {
                    Lock.lock(this)
                    null
                }
            }
            .setNeutralButton(R.string.new_code) { _, _ -> showNewCode() }
            .setNegativeButton(android.R.string.cancel, null))
    }

    // A new code is shown, then has to be typed back before anything locks, so it's known to be saved.
    // If the typed code doesn't match, the same code is shown again so the saved copy can be checked.
    private fun showNewCode(code: String = Code.create()) {
        val view = TextView(this)
        view.text = code
        view.typeface = Typeface.MONOSPACE
        view.textSize = 24f
        view.setTextIsSelectable(true)
        view.setPadding(dialogPadding(), dialogPadding() / 2, dialogPadding(), 0)
        show(AlertDialog.Builder(this)
            .setTitle(R.string.code_title)
            .setMessage(R.string.code_message)
            .setView(view)
            .setPositiveButton(R.string.code_saved) { _, _ -> checkNewCode(code) }
            .setNegativeButton(android.R.string.cancel, null))
    }

    private fun checkNewCode(code: String) {
        val field = EditText(this)
        field.typeface = Typeface.MONOSPACE
        field.setHint(R.string.code_hint)
        field.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val frame = FrameLayout(this)
        frame.setPadding(dialogPadding(), 0, dialogPadding(), 0)
        frame.addView(field)
        show(AlertDialog.Builder(this)
            .setTitle(R.string.code_check_title)
            .setMessage(R.string.code_check_message)
            .setView(frame)
            .setPositiveButton(R.string.lock) { _, _ ->
                if (Lock.isLocked(this)) {
                    refresh()
                } else if (Code.same(code, field.text.toString())) {
                    inBackground {
                        Lock.lock(this, code)
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

    private fun dialogPadding() = (24 * resources.displayMetrics.density).toInt()

    // Runs action only if the typed lock code is right.
    private fun withCode(action: () -> Unit) {
        val typed = findViewById<EditText>(R.id.code).text.toString()
        inBackground {
            if (Code.matches(this, typed)) {
                action()
                null
            } else {
                getString(R.string.code_wrong)
            }
        }
    }

    // Locking and unlocking go through every app, so they run off the main thread.
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
        findViewById<View>(R.id.unlock).isEnabled = enabled
        val list = findViewById<LinearLayout>(R.id.quarantine)
        for (i in 0 until list.childCount) list.getChildAt(i).isEnabled = enabled
    }

    @Suppress("DEPRECATION") // Still the only way for a device owner to remove itself.
    private fun confirmDeactivate() {
        show(AlertDialog.Builder(this)
            .setTitle(R.string.deactivate_confirm_title)
            .setMessage(R.string.deactivate_confirm_message)
            .setPositiveButton(R.string.deactivate) { _, _ ->
                if (Lock.isLocked(this)) return@setPositiveButton refresh()
                try {
                    // Deactivate is only shown while unlocked, but only the device owner can unhide
                    // apps, so make sure nothing is left hidden before giving that up.
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
