# Setup

You need a phone with Android 13 or newer, and ADB on a computer or on the phone itself.

1.  Remove every account, other user profile and Private Space from your phone, or factory reset it.
2.  Install Beeswax.
3.  Turn on Developer options: in Settings → About phone, tap Build number seven times.
4.  Connect ADB, from a computer or from the phone itself. See [Connecting ADB](#connecting-adb) below.
5.  Run this command to activate Beeswax as the device owner:
    ```
    adb shell dpm set-device-owner org.getflourish.beeswax/.Receiver
    ```
    Beeswax now lists your apps.
6.  **Turn off Developer options.** This also turns off ADB and is important for your device's security.
7.  In Beeswax, switch off every app you want blocked. Rotating the phone resets the switches, so check them just before you lock.
8.  Tap Lock, save the lock code, type it back and tap Lock again. Save it somewhere you can't easily get to, or give it to someone you trust.

Congrats, you're done! The apps you switched off can no longer be opened without your lock code. If something doesn't work, see the [FAQ](FAQ.md).

## Connecting ADB

### USB Debugging

1.  Install [ADB](https://developer.android.com/tools/releases/platform-tools) on your computer.
2.  On the phone, turn on Settings → System → Developer options → USB debugging.
3.  Connect the phone with a USB cable, and tap Allow on the phone.
4.  Open a terminal on the computer (Command Prompt on Windows) and run step 5's command there.

### Wireless Debugging

No cable needed. The phone and computer need to be on the same Wi-Fi.

1.  Install [ADB](https://developer.android.com/tools/releases/platform-tools) on your computer, and open a terminal (Command Prompt on Windows).
2.  On the phone, turn on Settings → System → Developer options → Wireless debugging, then tap Pair device with pairing code.
3.  On the computer, run `adb pair` followed by the IP address and port shown under the code, then type the code.
4.  Run `adb connect` followed by the IP address and port under "IP address & Port" on the Wireless debugging screen.
5.  Run step 5's command.

### Without a computer

You can use [Termux](https://f-droid.org/packages/com.termux/), a terminal app, and wireless debugging to activate Beeswax without a second device.

1.  Install Termux, open it and run `pkg install android-tools`.
2.  Turn on Settings → System → Developer options → Wireless debugging.
3.  Put Settings and Termux side by side in split screen. In Wireless debugging, tap Pair device with pairing code. In Termux, run `adb pair localhost:` followed by the port shown under the code, then type the code.
4.  Run `adb connect localhost:` followed by the port under "IP address & Port" on the Wireless debugging screen.
5.  Run step 5's command in Termux.

After setup, uninstall Termux or switch it off in Beeswax, because it can browse the web.
