# Tasks: Non-Intrusive Media Storage (`MainActivity.kt` & `profile.html` only)

- [x] 1. Update `feed.html`
  - [x] Add missing "Record Video" button and video capture input to `#composer-media-row` in `feed.html` (leave JS untouched)
- [x] 2. Update `MainActivity.kt`
  - [x] Copy picked/captured media files into `Android/data/com.chillspace.app/files/Pictures` or `Movies` in `fileChooserLauncher`
  - [x] Expose `AndroidUtils` bridge methods (`deleteFile`, `getLastCapturedUri`) with `try/catch` safety
- [x] 3. Update `profile.html`
  - [x] Render `mediaUrl` directly from `file:///...` URIs with lazy loading (`loading="lazy"`, `preload="metadata"`)
  - [x] Update `deletePost()` to delete physical files via `AndroidUtils.deleteFile()`
- [x] 4. Build and Verify
  - [x] Run `gradle_build("app:assembleDebug")`
