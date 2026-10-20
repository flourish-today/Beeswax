# Setup

## How it works

<details> <summary><b>Click here to see how it works</b></summary> <br>

-   **Beeswax restricts the phone you already use.** It's the device owner of the Owner profile, the standard way to manage a device on Android. You can't create other user profiles or private spaces.
-   **The lock hides your browsers.** When you lock Beeswax, it hides every app that is a browser or can open any web link. Hidden apps disappear from the home screen and can't be opened. 
-   **New apps are blocked until Beeswax is unlocked.** The apps you have when you lock are not affected by Beeswax. When locked, every app installed afterward is hidden until it's allowed by entering the lock code in Beeswax. 
-   **The lock code is the only way back.** The first Lock creates a random 16 character lock code. Unlocking web browsers, allowing newly installed apps, and deactivating Beeswax require the code. 
- **No bypasses.** Restarts, safe mode, private spaces, user profiles, and ADB cannot be used to bypass Beeswax.

</details>

## Before you start

-   You need an Android phone, a USB-C cable, and a computer with [ADB installed](https://developer.android.com/tools/adb). Beeswax is tested on GrapheneOS. Other versions of Android haven't been tested yet.
-   **Back up all important data off your device.** You should do this even if you weren't using Beeswax, in case your phone gets lost or breaks. If you ever got locked out of Beeswax, you could still export all your data before factory resetting.
-   Android only allows a device owner on a phone with no accounts, no other users, and no Private Space. Remove every account, other user, and your Private Space. If you aren't able to get step 5 working, a factory reset will always work.
-   The lock code is what keeps Beeswax locked. Pick a way to keep it that you can't easily get to yourself. For example, have someone you trust keep it, or store it somewhere inconvenient. Whoever is holding the phone at the first Lock sees the code, so the person keeping it should do that step.
-   **If the lock code is lost, the only way back is a factory reset.** Not even ADB can remove Beeswax while it's locked.

## Instructions

1.  Install Beeswax.
2.  Enable Developer options: open Settings → About phone, tap Build number seven times, and enter your PIN or password.
3.  In Settings → System → Developer options, turn on USB debugging.
4.  Connect the phone to your computer. When the phone asks whether to allow USB debugging, tap Allow.
5.  On your computer, open a terminal (Command Prompt on Windows) and run the command Beeswax shows on its screen: `adb shell dpm set-device-owner org.getflourish.beeswax/.Receiver` Beeswax should now say "Device owner: active."
6.  Turn off Developer options in Settings → System → Developer options (this will also turn off USB debugging).
7.  Install the apps you want to keep. Every app you have when you lock stays usable, so look through them first and uninstall any that let you browse the web (see [Usability limitations](#usability-limitations)).
8.  In Beeswax, tap Lock. Beeswax shows your new lock code.
9.  Save the lock code, tap "I've saved it somewhere safe!", type the code back, and tap Lock. If the code doesn't match, Beeswax shows the same code again so you can check the copy you saved.
10. Beeswax should now say "Beeswax is locked" and list your browsers after "Hidden browsers." Check that they're gone from the home screen.

## Troubleshooting

-   **The `set-device-owner` command fails with an error about accounts or users.** An account, another user, or Private Space still exists. Remove it and run the command again. A reliable way to set up Beeswax is after a factory reset.
-   **ADB says no devices were found, or that the device is unauthorized.** Check that the cable is connected and USB debugging is on, then unplug and reconnect the phone and tap Allow when it asks.
-   **Beeswax says it can't be locked while another device admin app is on.** Android won't let Beeswax hide a device admin app. Turn it off in Settings → Security & privacy → More security & privacy → Device admin, then tap Lock again.
-   **Beeswax says it can't be locked while the phone has other users.** Each user and guests, have their own browser. Delete them in Settings → System → Users, then tap Lock again.

## Adding apps

While Beeswax is locked, every app you install is hidden right away and listed in Beeswax. To allow one:

1.  Install the app as usual.
2.  Open Beeswax and enter the lock code.
3.  Tap Allow next to the app.

If Beeswax says an app "opens web links, so it may be a browser," think twice before allowing it. Once you allow an app, the only way to block it again is to uninstall it. If you uninstall an allowed app and install it again, it has to be allowed again.

## Unlocking and locking again

-   To use a browser or install several apps at once, enter the lock code and tap Unlock. Everything Beeswax hid comes back, and its restrictions are lifted.
-   To lock again, tap Lock, then Lock. Beeswax uses the same lock code without asking for it, so only lock again if you or someone you trust still has it. To get a new code instead, tap New code.
-   Apps installed while Beeswax was unlocked are allowed at the next Lock, unless they're browsers.

## To undo it

1.  Open Beeswax, enter the lock code, and tap Unlock. Deactivate only shows while Beeswax is unlocked.
2.  Tap Deactivate and confirm.
3.  You can now uninstall Beeswax.

To set up Beeswax again, you'll need the ADB command, and the phone must again have no accounts, no other users, and no Private Space.

## Usability limitations

-   Links that would normally open in a browser won't open while Beeswax is locked, because there is none.
-   WebView:
    -   **Vanadium System WebView and Vanadium Config aren't hidden**, so apps that need WebView keep working. But any app with a built-in browser may still let you browse the web, for example through a help link or an in-app browser. Choose the apps you allow with that in mind. You may need to uninstall an app if it's letting you bypass the restrictions. If an app lets you browse sites beyond the ones it needs, consider asking its developer to follow [Android security best practices](https://developer.android.com/privacy-and-security/security-best-practices#webview).
    -   **Wi-Fi sign-in pages still open.** Android's sign-in screen for networks like hotel or café Wi-Fi shows web pages too, and Beeswax doesn't hide it.
-   Beeswax finds browsers by asking Android which apps open web links. A few browsers don't declare that (for example Surf and Lite++), so Beeswax can't tell they're browsers. One installed before you lock stays usable. One installed after is hidden like any new app, but its Allow button won't warn you.
-   New users can't be added while Beeswax is locked, and Private Space is unavailable while Beeswax is the device owner.

## Screenshots
