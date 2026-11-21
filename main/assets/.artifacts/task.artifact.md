# Tasks: Fix Infinite Scroll & Filter Korean Content

- [x] 1. Update `feed.html`
  - [x] Add `let isKo = false;` globally and set it in `loadLangXml()` based on `navigator.language`
  - [x] Update `loadFeed()` and `loadMorePosts()` to check `isKo` and omit story prompt cards, polls, and political/debate posts when `isKo` is true
  - [x] Update `setupInfiniteScroll()` with dual-layer trigger (`IntersectionObserver` + `window.scroll` scroll listener fallback)
- [x] 2. Build and Verify (`app:assembleDebug` success)
