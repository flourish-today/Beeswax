# FAQ

## Does Beeswax use AI?

Yes. This app was developed with Claude Opus 5.5. Its code was reviewed with Opus 5.5 and all issues were fixed or found inconsequential. More reviews are welcome.

## Is Beeswax secure?

Beeswax uses the intended way to restrict devices on Android. It's tiny, requests one permission, and only does what it says. See more info in [PRIVACY.md](PRIVACY.md). You can verify you got the real Beeswax APK using the [SHA256 Signing Certificate](PRIVACY.md#verifying-a-release), and verify that its source code matches the released APK through [reproducible builds](PRIVACY.md#reproducible-builds).

## Is Beeswax privacy respecting?

Yes. There's no telemetry, one permission, and no network access.

## What are the risks?

One risk with Beeswax, and device-owner apps in general, is the amount of trust you have to put in them. Device-owner apps are given a huge amount of permissions by Android. Beeswax uses only the few listed in [PRIVACY.md](PRIVACY.md#device-management-apis-used), and they can be undone. See [Risks to using Beeswax](PRIVACY.md#risks-to-using-beeswax) for more.

## Will I always need ADB on GrapheneOS?

In the future, depending on GrapheneOS, Beeswax may be updated so you can install it and make it the device owner without ADB, through QR code provisioning during initial device setup ([issue](https://github.com/GrapheneOS/platform_packages_apps_SetupWizard2/issues/35) / [earlier attempt, closed](https://github.com/GrapheneOS/platform_packages_apps_SetupWizard2/pull/40)). You can add reactions to the issue to show support, but please only comment if you have something meaningful to add.

If GrapheneOS adds support for QR code provisioning, it will likely be added and then work on other Android devices.

## What if I have an iPhone or iPad that I want to achieve the same result on?

You should use Apple Configurator ([guide1](https://redlib.catsarch.com/r/nosurf/comments/1731ozp/how_to_turn_your_your_iphone_into_dumb_phone/) / [guide2](https://stopa.io/post/297)) to manage your devices, remove the browser and prevent new app installs. A macOS device and a factory reset of your iPhone/iPad are required for the initial setup. A [new app](https://news.ycombinator.com/item?id=49982974) that claims to allow this setup without a factory reset has been released but I have not looked into it at all and this is not a recommendation.

## There's not been much activity recently, is it still safe?

This is a relatively simple app which will not require many updates. Beeswax works on Android 17-based GrapheneOS. If something breaks after an update, please open an [issue](https://github.com/flourish-today/beeswax/issues).
