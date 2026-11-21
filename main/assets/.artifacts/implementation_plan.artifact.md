# Implementation Plan — Remove Notifications, Add Profile Header Icon & Explore Tab

Remove the notification system from `feed.html`, replace the notification bell with a profile button (person SVG + "Your Profile"), replace the bottom navigation Profile tab with an Explore tab (`nav_explore`), and create `explore.html` linking to `exploresky.html`, `exploreroad.html`, and `exploreocean.html`.

---

## Technical Details

### 1. Remove Notifications & Add Profile Button (`feed.html`)
* **Remove Notification System**:
  - Remove `#bellBtn`, `#bell-badge`, `#notif-panel`, `setupBell()`, and notification item click/render logic from `feed.html`.
* **Add Profile Icon Button**:
  - In `#bell-wrap` (header), replace the bell button with a profile button (`#profileHeaderBtn`):
    ```html
    <button id="profileHeaderBtn" class="header-btn profile-btn" onclick="location.href='profile.html'" aria-label="Your Profile" style="display:flex; flex-direction:column; align-items:center; gap:2px; background:none; border:none; cursor:pointer; padding:4px;">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:#65676b;"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
        <span style="font-size:10px; font-weight:600; color:#65676b; line-height:1;">Your Profile</span>
    </button>
    ```

### 2. Create Explore Hub Page (`explore.html`)
* **New Asset File (`explore.html`)**:
  - Styled matching `gaming.html` (dark theme, glassmorphism cards).
  - Provides direct entry buttons/cards for:
    - **Explore Sky** (`exploresky.html`) ☁️
    - **Explore Road** (`exploreroad.html`) 🛣️
    - **Explore Ocean** (`exploreocean.html`) 🌊

### 3. Bottom Navigation & MainActivity Updates
* **`bottom_nav_menu.xml`**: Replace `nav_profile` with `nav_explore` (Title: "Explore", Icon: compass or explore drawable).
* **`activity_main.xml`**: Replace `webViewProfile` with `webViewExplore` loading `file:///android_asset/explore.html`.
* **`MainActivity.kt`**: Replace `webViewProfile` logic with `webViewExplore`, handling `R.id.nav_explore` in `bottomNav`.

---

## Verification Plan

### Automated Build
- Run `gradle_build("app:assembleDebug")` to verify compilation.

### Manual Verification
1. Open Feed (`feed.html`) -> Verify notification bell is gone and replaced by the Profile person SVG icon with "Your Profile" underneath, linking to `profile.html`.
2. Tap Bottom Nav "Explore" tab -> Verify `explore.html` opens and displays cards/buttons for Sky, Road, and Ocean explore modes.
