# Interactive Slideshow Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review these tasks. Track completion with the checkboxes below.

**Goal:** Start Frame on demand, tap for photo details with a temporary pause, swipe through photos, and close through an overlay button.

**Architecture:** Keep the existing screensaver trampoline and slideshow Activity/View controller. A separate details overlay owns presentation and its inactivity deadline; the controller owns playback and gestures. Settings launches the Activity normally, while the dream marks its independent task so Close uses the correct exit behavior.

**Tech Stack:** Kotlin, Android Views for the slideshow, Jetpack Compose settings, JUnit 4, Gradle Android build.

**Spec:** Approved in this conversation: Start slideshow; tap to show date/caption/album/source and pause pan/zoom and progression; fade after 10 seconds idle and resume with a fresh interval; tap again to resume; swipe left/right next/previous and refresh details timeout; show details for both paired photos; visible Close button; identical interactions for manual and screensaver launches.

## Global Constraints

- Preserve existing uncommitted Celsius changes in SlideshowController.kt and Weather.kt.
- Work in the current checkout; do not commit, push, or install on a device as part of these tasks.
- Android minSdk 28, targetSdk 29; preserve Portal clock/note editing and low-light behavior.
- Use only existing photo metadata; do not infer location or camera details, or expose raw image URLs.
- Pause lasts until details disappear. Swipes still work during pause. Background album/upload updates must not unexpectedly replace the photo being inspected.
- Existing network and provider security restrictions stay in force.
- Build tools: JAVA_HOME=/private/tmp/frame-tools/jdk-21.0.12.1+1/Contents/Home, ANDROID_HOME=/private/tmp/frame-tools/android, GRADLE_USER_HOME=/private/tmp/frame-tools/gradle.

## Task 1 — Details overlay and inactivity deadline (Sol agent: overlay)

**Files:** DetailsTimeout.kt, new PhotoDetailsOverlay.kt, app/tests/com/portalhacks/frame/DetailsTimeoutTest.kt. Do not edit controller or activities.

**Contract produced:**
```kotlin
internal class PhotoDetailsOverlay(
    context: Context,
    onPauseChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val view: View // bottom-aligned panel, initially GONE; add above gesture surface
    val isOpen: Boolean
    fun show(slides: List<Slide>)
    fun update(slides: List<Slide>) // metadata refresh only; no reopening
    fun interact() // reset 10s deadline, cancel an in-progress fade
    fun hide() // immediate hide and resume, cancels deadline/fade
    fun dispose() // cancel callbacks/fade without resuming
}
```

- [ ] Complete the DetailsTimeout implementation against the existing 4 tests. RED confirmed: 3 tests fail in Gradle and standalone JUnit against the stub. Use monotonic milliseconds, a 10000ms deadline, no expiry while closed.
```kotlin
fun open(now: Long) { isOpen = true; deadline = now + 10000L }
fun interact(now: Long) { if (isOpen) open(now) }
fun expired(now: Long) = isOpen && now >= deadline
fun close() { isOpen = false }
```
- [ ] Implement a compact dark translucent bottom panel with readable text, pause/resume hint, and accessible Close button. Non-button space must allow gestures through to the controller. A short fade ends with onPauseChanged(false); show calls onPauseChanged(true). Cancel obsolete fade completion on interaction or dispose.
- [ ] Resolve album/source via Albums.enabled, AlbumCache, and PhotoSources.providerFor; include a full date formatted in UTC (stored timestamps are wall-clock-adjusted), optional caption, and graceful unknown-date state. Label both members of a pair. Avoid URL display and unbounded text layout.
- [ ] Run timer tests; report exact results and any limitations.

## Task 2 — Controller pause and gestures (Sol agent: playback)

**Files:** SlideshowController.kt and focused tests/helpers if needed. Consume the Task 1 contract exactly; do not edit its files or activities.

- [ ] Add overlay above buildTouchOverlay. Wire onPauseChanged to stop/restart autoTick and pause/resume Ken Burns. Preserve running lifecycle state separately from details visibility. Use onClose to invoke existing onDismiss.
```kotlin
if (running && !details.isOpen && !clockOnly && items.size > 1) {
    handler.postDelayed(autoTick, intervalMs)
}
```
- [ ] Replace photo tap dismissal with show/hide details. Maintain actual displayed Slide snapshots for metadata, including pairs. Keep current long-press clock/settings and note drag gestures. Call interact on touch and navigation while open; guard low-light and empty states.
- [ ] Preserve manual navigation while paused. Refresh metadata when the incoming image actually displays. Prevent late decodes, an in-flight crossfade, or newly started Ken Burns from defeating pause. A tap during a transition must settle or safely finish the displayed frame without mismatched metadata.
- [ ] Defer background setItems/setItemsShowing while details are visible; apply the latest pending update on resume while preserving the inspected photo if it still exists. Clear stale work on stop, blank, and low-light transition. Invalidate in-flight image callbacks on lifecycle stop.
- [ ] Ensure previous undoes next for portrait pairs and normal frames; test any extracted navigation logic with hand-checked fixtures. Avoid unrelated slideshow refactoring.
- [ ] Self-review timer scheduling, image callback generations, rotation, and cleanup. Report tests and integration assumptions.

## Task 3 — Start/Close flow and documentation (Sol agent: entry)

**Files:** SettingsActivity.kt, SlideshowComposeActivity.kt, FrameDreamService.kt, README.md, INSTALL.md. Do not edit controller, overlay, tests, or build config.

- [ ] Add prominent Start slideshow button in settings using the existing PrimaryBtn style.
```kotlin
startActivity(Intent(this@SettingsActivity, SlideshowComposeActivity::class.java))
```
- [ ] Add launch provenance via Activity companion constant EXTRA_FROM_DREAM = "from_dream". Dream passes true on its existing new/clear task Intent. Close uses finishAndRemoveTask for dream launches and finish for manual launches. Keep Android Back available.
- [ ] Prevent background fetch callbacks from reactivating playback after Activity pause: record foreground state and ignore UI apply callbacks while paused; caches may still update. Keep existing provider fetching and refresh behavior.
- [ ] Update README/INSTALL touch instructions and describe on-demand launch. Remove misleading comments about tap-to-dismiss.
- [ ] Self-review manual settings stack, dream exit, empty/loading, and no screensaver configuration dependency for manual launch.

## Task 4 — Integration and review (root + Sol reviewer)

- [ ] Inspect all agent results; resolve shared-interface issues with the owning agent.
- [ ] Run `./gradlew testDebugUnitTest assembleDebug lintDebug detekt ktlintCheck` with temporary tool env above. Fix newly introduced findings without touching unrelated user edits or regenerating baselines.
- [ ] Have a Sol reviewer inspect the full diff for spec compliance and code quality; send material fixes to implementers.
- [ ] Re-run affected tests/checks after fixes. Document hardware-only verification limits if no Portal/emulator is connected.
- [ ] Deliver concise behavior summary and APK path if the build passes.

## Device acceptance checks

No Portal or emulator was connected at initial verification (`adb devices` returned an empty list). These checks require Android UI execution:

| Scenario | Expected result |
| --- | --- |
| Start slideshow without configuring Frame as screensaver | Fullscreen playback opens; Close returns to settings |
| Idle launches Frame over another app | Tap shows details; Close returns to the previously active app |
| Tap during normal playback or crossfade | Correct displayed photo details; no further automatic progression or pan/zoom |
| Leave details untouched | Details fade after 10 seconds; next automatic advance occurs after a fresh configured interval |
| Swipe while details open | New photo and matching details; another 10 seconds to read |
| Tap again while details open | Details disappear; playback resumes |
| Interact while details are fading | Fade cancels; details stay readable and playback stays paused |
| Next then previous across paired photos | Return to the prior frame, without skipping or duplicating a member |
| Photo arrives or album refresh completes during details | Inspected photo remains stable until details close |
| Rotate or enter low-light mode, then return | No stuck pause, stale details, or hidden-photo progression |
| Exit while an image is loading | No callback restarts background playback |
| Long-press clock / drag note | Existing editing interactions remain usable |

## Execution evidence

- Initial timer RED: `testDebugUnitTest --tests com.portalhacks.frame.DetailsTimeoutTest` ran 4 tests; 3 failed against the placeholder, proving missing pause/deadline behavior is detected.
- Temporary JDK 21 and Android SDK installed under `/private/tmp/frame-tools`; no machine-wide SDK configuration was changed.
