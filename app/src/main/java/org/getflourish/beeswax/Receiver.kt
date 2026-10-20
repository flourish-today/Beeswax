// SPDX-License-Identifier: CC0-1.0
package org.getflourish.beeswax

import android.app.admin.DeviceAdminReceiver

// Android needs a device admin receiver to make an app device owner. Beeswax does all its
// work from its screen and Guard, so this receiver adds nothing of its own.
class Receiver : DeviceAdminReceiver()
