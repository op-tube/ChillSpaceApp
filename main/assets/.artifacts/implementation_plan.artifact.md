# Implementation Plan — Auto-Delete Story Media & Prevent Profile Video Resets

Automatically delete media for ephemeral Story posts in `feed.html` and `feedko.html`, and prevent `profile.html` from randomly re-rendering/refreshing while a user is watching a video.

---

## Proposed Changes

### 1. Feed Components (`feed.html` & `feedko.html`)
#### [MODIFY] [feed.html](file:///C:/Users/ollie/AndroidStudioProjects/ChillSpace/app/src/main/assets/feed.html)
#### [MODIFY] [feedko.html](file:///C:/Users/ollie/AndroidStudioProjects/ChillSpace/app/src/main/assets/feedko.html)

* In `postBtn` click listener:
  * When `postType === 'story'`, invoke `window.AndroidUtils.deleteLastCapturedFile()` to automatically purge temporary media files from device storage (`Pictures`/`Movies`).
* In media remove buttons (`imageRemoveBtn` / `videoRemoveBtn`): Invoke `window.AndroidUtils.deleteLastCapturedFile()` when clearing preview selections.

---

### 2. Profile Page (`profile.html`)
#### [MODIFY] [profile.html](file:///C:/Users/ollie/AndroidStudioProjects/ChillSpace/app/src/main/assets/profile.html)

* **Prevent Video Interruption & Random Refreshing**:
  * In `visibilitychange` listener: Do NOT call `render()` if any video element is currently playing (`document.querySelector('video:not(:paused)')`).
  * In `storage` event listener: Compare new storage content with current posts; only call `render()` if data actually changed.
  * In `render()`: Avoid redundant full DOM rebuilds if posts haven't changed.

---

## Verification Plan

### Automated Build
- Run `gradle_build("app:assembleDebug")` to verify compilation.

### Manual Verification
1. Post a video as a **Story** -> Check device storage (`Pictures`/`Movies`) to confirm the file was deleted.
2. Open **Profile** (`profile.html`), start playing a video post, switch tabs or trigger visibility change -> Confirm video continues playing smoothly without resetting or refreshing.
