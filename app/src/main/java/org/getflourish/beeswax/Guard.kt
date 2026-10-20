// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.admin.DeviceAdminService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

// Enabled only while locked. Android keeps a device owner's DeviceAdminService
// running and restarts it after a crash, so Guard hears every "package added" broadcast.
// Only the system can bind it (BIND_DEVICE_ADMIN), and its receiver isn't exported.
class Guard : DeviceAdminService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            Lock.check(context, packageName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        Lock.enforce(this)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
