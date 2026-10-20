# Privacy and security

Beeswax collects and shares no personal data. It has no telemetry, one permission, and no network access. It has reproducible builds so you can verify the source code is what runs on your device.

Beeswax uses its Device Owner capability responsibly by only using these few Device Management APIs it needs to function:

## Device management APIs used

| What it does | Android API | When |
|---|---|---|
| Blocks adding new users, since a new user would have its own browser and app store | `DevicePolicyManager.addUserRestriction` with `DISALLOW_ADD_USER` | When you tap Lock |
| Blocks debugging features, so ADB can't be used to get around the lock | `DevicePolicyManager.addUserRestriction` with `DISALLOW_DEBUGGING_FEATURES` | When you tap Lock |
| Hides browsers and new apps, and brings them back | `DevicePolicyManager.setApplicationHidden` | Hides them when you tap Lock, when an app is installed while locked, and when the phone restarts while locked. Brings them back when you tap Allow, Unlock, or Deactivate |
| Turns Guard, Beeswax's background service that watches for new apps, on and off | `PackageManager.setComponentEnabledSetting` (Guard only) | On when you tap Lock, off when you tap Unlock or Deactivate |
| Allows adding new users again | `DevicePolicyManager.clearUserRestriction` with `DISALLOW_ADD_USER` | When you tap Unlock or Deactivate |
| Allows debugging features again | `DevicePolicyManager.clearUserRestriction` with `DISALLOW_DEBUGGING_FEATURES` | When you tap Unlock or Deactivate |
| Turns the backup service on, which Android turns off when a device owner is set | `DevicePolicyManager.setBackupServiceEnabled` | Whenever you open Beeswax |
| Removes itself as device owner | `DevicePolicyManager.clearDeviceOwnerApp` | When you tap Deactivate |

## Risks to using Beeswax

-   Beeswax is a device-owner app, which gives it powers far beyond a normal app. Beeswax uses only the few listed above, but a future update signed with the same key could, for example, give Beeswax the ability to wipe your device.
-   If the lock code is lost while Beeswax is locked, the only way to get your browsers back or allow new apps is a factory reset. Not even ADB can remove Beeswax while it's locked.
-   By using Beeswax, you're using Android in a non-standard way. Beeswax hides the browsers, including the default browser (Vanadium on GrapheneOS). GrapheneOS recognizes device owners as the standard way to manage devices, and ADB is currently the only way to set one up. However, GrapheneOS advises against apps that use these APIs to "disallow a bunch of functionality." [1]

## Features

-   Beeswax has no network access and one permission, and other apps can't use its powers.
-   Its one permission, `QUERY_ALL_PACKAGES`, lets it see the apps installed on your phone, so it can find browsers and new apps. Without it, Android hides other apps from Beeswax and Beeswax can't hide them.
-   Its only exported component is its launcher screen, and its only library is the Kotlin standard library. Guard, its background service, isn't exported, only Android can start it, and it's only on while Beeswax is locked. You can check the components and the permission in `app/src/main/AndroidManifest.xml`.
-   The lock code is never stored. Beeswax keeps only a salted hash of it, in its private storage, which Settings can't clear while Beeswax is the device owner.
-   Beeswax blocks screenshots and screen recording of its own screen, so the lock code can't be recorded as it's typed.
-   Beeswax has reproducible builds. See [Reproducible builds](#reproducible-builds).
-   Beeswax has a simple design with a single screen.
-   Beeswax is tiny; it's about 300 kB installed.

## Verifying a release

Use [AppVerifier](https://github.com/soupslurpr/AppVerifier) with the package name and hash below. Beeswax has also been [domain verified](https://github.com/privacyguides/verified-apps/issues/6777) and [submitted](https://github.com/privacyguides/verified-apps/issues/6778) to Privacy Guides' [verified-apps-android](https://github.com/privacyguides/verified-apps-android) database.

```
org.getflourish.beeswax
F5:37:A3:91:23:40:95:AC:42:53:85:D4:9E:B0:AA:43:C8:ED:84:4D:FC:84:15:F2:1C:C4:BF:63:52:86:34:54
```

## Reproducible builds

This confirms the released APK was built from the public source code. Your build will match the release in every file; only the signature differs. Being open source with reproducible builds doesn't mean a third party has checked that the code is safe or that the builds match.

You need:

-   JDK 21, for example [Temurin 21](https://adoptium.net/temurin/releases/?version=21). If you have more than one Java installed, set `JAVA_HOME` to the Java 21 folder. Check that `java -version` shows 21.
-   [Gradle 9.5.1](https://services.gradle.org/distributions/gradle-9.5.1-bin.zip), unzipped, with its `bin` folder on your `PATH` so the `gradle` command works. Package managers usually install a newer version, which won't match.
-   The Android SDK with platform 37.0 and build-tools 37.0.0, from Android Studio or Google's [command-line tools](https://developer.android.com/studio#command-line-tools-only). Set `ANDROID_HOME` to the SDK folder. With the command-line tools, install the parts with `sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-37.0" "build-tools;37.0.0"`.
    
1. Get the source for the release and build it:

   ```
   git clone https://github.com/flourish-today/beeswax.git
   cd beeswax
   git checkout v0.09
   gradle --no-daemon :app:assembleRelease -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/37.0.0/aapt2"
   ```

2. Download `Beeswax-0.09.apk` from the [v0.09 release](https://github.com/flourish-today/beeswax/releases/tag/v0.09) into the `beeswax` folder.

3. Unpack both APKs and compare them:

   ```
   mkdir rel mine
   unzip -q Beeswax-0.09.apk -d rel
   unzip -q app/build/outputs/apk/release/app-release-unsigned.apk -d mine
   diff -rq rel mine && echo MATCH
   ```

If it prints `MATCH`, the release contains exactly what you built.

```
sha256sum app/build/outputs/apk/release/app-release-unsigned.apk
```

Please consider sharing the result in an issue no matter what result you got.

## Notes

[1] Relevant GrapheneOS posts about apps like Beeswax. They are not specific to Beeswax but device management apps in general.

[Tweet 1 (August 2021)](https://xxcancel.com/GrapheneOS/status/1422158415728627715)

> Device management APIs do work well already. You just need to grant those privileges to a device management app. It's possible Play supports it. We have no setup wizard integrating for setting a device policy manager as a device owner but that isn't an issue for work profiles.

[Tweet 2 (February 2026)](https://xxcancel.com/GrapheneOS/status/2023608038637146442)

> MDM apps are still sandboxed and can only do what's allowed by specific device management apps. ADB shell gives far more access than the device policy owner privileges. Just don't install and activate an app using APIs which mainly exist to disallow a bunch of functionality.
