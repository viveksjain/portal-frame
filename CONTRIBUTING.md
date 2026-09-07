# Contributing to Frame

Thanks for your interest! Frame is a hobby/open-source slideshow + screensaver for the
Meta Portal. Contributions, bug reports, and ideas are welcome.

## Building

The app is Android (Kotlin with Jetpack Compose and Android Views) and builds with Gradle:

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

CI runs unit tests and static analysis on every push/PR — mirror it locally before you push:

```bash
./gradlew testDebugUnitTest              # JVM unit tests
./gradlew lintDebug detekt ktlintCheck   # Android Lint + detekt + ktlint
./gradlew ktlintFormat                   # auto-fix formatting on your changes
```

New findings fail the build; pre-existing ones are grandfathered by baselines
(`app/detekt-baseline.xml`, `app/config/ktlint/baseline.xml`) — only regenerate those when you
mean to accept new debt.

Requirements:
- **JDK 17–21** (AGP needs a JDK in this range; a newer/GraalVM JDK can fail the build's
  `JdkImageTransform`). Point Gradle at one with `JAVA_HOME` or `org.gradle.java.home` if your
  default `java` is newer.
- An **Android SDK** — point to it with `ANDROID_SDK_ROOT`, or create a `local.properties`
  with `sdk.dir=/path/to/Android/sdk` (this file is git-ignored; never commit it).
- The compile/target SDKs and dependency versions are pinned in `app/build.gradle.kts`.

The Gradle build generates a debug keystore automatically, so you don't need to (and shouldn't)
commit one.

> The legacy `build.sh` (a dependency-free `aapt2 → javac → d8 → zipalign → apksigner` pipeline)
> predates the Compose migration and only compiles the Java sources — it won't produce a working
> APK of the current app. Use Gradle.

## Installing / testing on a Portal

See the [Install & User Guide](INSTALL.md). In short:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then either launch the **Frame** app icon (setup/settings) or set it as the screensaver. The album,
weather city, and temperature unit can also be set over ADB for quick testing — see
`ConfigReceiver` and `CLAUDE.md`.

## Project layout

- `app/src/com/portalhacks/frame/` — sources (all Kotlin).
  - `SlideshowComposeActivity` / `SlideshowController` — the full-screen slideshow, including its
    hourly album refresh, interactive details, pause, and gesture integration.
  - `PhotoDetailsOverlay` / `PhotoMetadataCache` — bounded photo details and downloaded Google
    Photos filename metadata.
  - `SlideshowNavigation` / `SlideshowState` / `DetailsTimeout` — testable navigation,
    refresh/transition/weather state, and details timing helpers.
  - `FrameDreamService` — the screensaver trampoline that launches the slideshow.
  - `SettingsActivity` (Compose) — the home-screen setup/settings UI.
  - `PhotosActivity` (Android Views) — camera QR scanner + manual link entry.
  - `PhotoProvider` / `PhotoSources` — provider abstraction + registry (route a link → `Album`).
  - `GooglePhotosSource` / `GooglePhotosPagination`, `ApplePhotosSource` — the providers; Google
    Photos continuation pages are fetched, capped, and de-duplicated.
  - `Weather` — Open-Meteo city lookup and current conditions in the configured temperature unit.
  - `ImageLoader` / `AlbumCache` — decoding/caching and the persisted photo cache. `ImageLoader`
    also decodes locally-pushed photos and honours their EXIF orientation.
  - **Add photos from a phone** ("AirDrop for Portal"): `DropServerService` runs a foreground
    LAN HTTP server (`LocalDropServer`, raw `ServerSocket` + hand-rolled multipart) that a phone's
    browser posts photos to; `LocalUploads` persists them under `filesDir/uploads`; `DropAuth`
    (token), `DropServerStatus` (bound port/URL) and `QrCodes` (ZXing encode) back the on-screen QR;
    `UploadsActivity` is the on-device grid to view/delete them. Plaintext + LAN-only + token-gated
    by design — see `SECURITY.md`.
- `app/res/`, `app/assets/` — resources, fonts, sample slides.
- `config/detekt/`, `app/*-baseline.xml`, `app/config/ktlint/` — static-analysis config/baselines.
- `tools/` — pure-stdlib Python helpers that generate the sample slides and launcher icon.

## Code style

- All Kotlin. Idiomatic Compose for new UI, Android Views for the existing slideshow/scanner.
  Keep the dark Portal palette/typography (`Ui` / `PortalColors`).
- **Adding a photo provider:** implement `PhotoProvider` (`matches` + `fetch` → `Album`) and add it
  to the `PhotoSources` list. Don't special-case a provider in the UI — it routes by URL.
- Providers are unofficial (they use public share endpoints) — fail closed (return empty / throw
  rather than accepting unexpected data), and only fetch over **HTTPS** (see `SECURITY.md`).

## Pull requests

Keep PRs focused, describe what you changed and how you tested it on-device (or why that wasn't
possible). By contributing you agree your work is licensed under the project's [MIT License](LICENSE).
