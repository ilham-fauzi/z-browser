# Browser-Z

A light, fast Android browser that blocks ads on every site (including Google AdSense), lets you pick
your search engine, and works fully on phones, tablets and foldables.

Requirements and design live in two docs (kept outside the repo):

- **PRD** — product requirements: https://claude.ai/code/artifact/93340d73-9bd0-4f52-88c3-51d03f2d00e8
- **TRD** — technical design, deviations and measured results: https://claude.ai/code/artifact/50356774-872e-462f-8a07-02f67a58e3b0

## Requirement traceability

| PRD feature | Code |
| --- | --- |
| F1 Android browser | `feature/browser`, `core/webview` (`WebViewBrowserSession`), `feature/library` (bookmarks with folders: `BookmarkFolders` in `core/domain`, `PrefsBookmarkRepository`; "Bookmark saved · Choose folder" banner) |
| Pin tab | `TabOrder` in `core/domain` (pinned first), `setPinned` in `WebViewBrowserSession` (persisted), compact chip in `TabStrip`, menu entry and long-press in `TabSwitcherDialog` / `TabSidebar` |
| F2 Ad blocking incl. AdSense, pop-ups and redirects | `engine/adblock` (`FilterEngine`, `FilterListUpdater`, `builtin.txt`; lists: EasyList, EasyPrivacy, HaGeZi Light and Pop-Up Ads, ABPindo), `core/domain` (`PopupPolicy`), request interception, pop-up windows, redirect blocking and CSS injection in `WebViewBrowserSession` |
| Ad blocking (round 3) | Element-hide exceptions `#@#` and `$generichide` / `$elemhide` (`FilterEngine.siteCss`, `genericSkipHosts`); site-specific hiding now reaches the page (`__bz_site` style, separate from the start script's `__bz_css`); procedural rules `:has-text` / `:-abp-contains` / `:-abp-has` / `:has()` with procedural argument / `:upward` / `:remove` (`ProceduralSelector` → `ProceduralScript`); YouTube video and feed ads (`YouTubeAdScript`); saved engine for fast start (`FilterEngine.writeTo` / `readFrom`, `engine.bin` in `FilterListUpdater`) |
| F3 Multiple search engines | `core/domain` (`DefaultSearchEngines`, `ResolveInput`), `core/data` (`PrefsSearchEngineRepository`), engine picker in `feature/browser`, `feature/search` |
| F4 Light app | R8 release build ≈ 1.4 MB; no analytics or ad SDKs |
| F5 High performance | RAM-aware live-WebView cap and tab suspension (`WebViewBrowserSession`), compiled-on-demand filter rules |
| F6 Full tablet support | `core/ui` (`AdaptiveShell`: toolbar on top or bottom, tabs as a top strip or side list, bar hides on scroll), `BrowserToolbar` (Chrome-style address pill), `TabStrip` / `TabSidebar`, Ctrl shortcuts in `MainActivity` / `BrowserZRoot` |
| Fullscreen video | `WebViewBrowserSession` (`onShowCustomView`, media-size hook for every frame), `FullscreenHost` in `app` (immersive mode, orientation follows the video: wide = landscape, tall = portrait), `VideoShape` in `core/domain` |
| F8 Chrome-style features | Home (`EngineHome`), tab groups (`TabGroups`, `TabOrder.arrange`), find in page (`FindBar`), reader mode (`ReaderScript`), offline pages (`SavedPageStore`), bookmark import/export (`BookmarkHtml`), share / add to home / downloads / translate (`PageActions` in `app`), block counter (`PrefsBlockStats`), cookie-banner lists (`FilterListUpdater`) |
| Chrome-style pages | `BookmarksScreen` (folders open one level at a time, search, multi-select), `HistoryScreen` (grouped by day via `HistoryDays`, search, clear), `SavedPagesScreen`, new-tab page with shortcuts (`NtpShortcuts`, `PrefsShortcutRepository`) and quick links |
| Chrome-parity (round 2) | Recently closed tabs + Undo (`ClosedTabs`), site info and per-site camera / microphone / location permissions (`SiteInfoSheet`, `PermissionBroker`, `PrefsSitePermissionRepository`), HTTPS-first (`HttpsOnly`), print / save as PDF, default-browser button, app shortcuts and search widget, page text size, passkey switch (`applyPasskeys`) |
| Browsing UX (round 3) | Pull to refresh (`TabWebView`, `PullRefreshIndicator`, setting "Pull to refresh"), page dialogs alert / confirm / prompt / leave site with "don't show more" (`JsDialog`, `PageDialog`), file upload through the system picker (`setFileChooser`, `PageActions.pickedFiles`), swipe the address bar to change tab (`TabSwipe`), grid tab switcher with page thumbnails and site icons (`TabSwitcherDialog`, `captureThumbnail`, `onReceivedIcon`) |
| Video app features (round 4) | Background play (Settings, off by default): `MediaPlaybackService` (foreground media service, notification and lock-screen controls via `MediaSession`), `PlaybackScript` (playback detection and remote control in every frame), `TabWebView.keepPlayingHidden`, tab kept alive in `WebViewBrowserSession` (`onHostPaused`, `resumeHiddenVideo`). Picture-in-picture (Settings, off by default): `MainActivity` (auto-enter on Android 12+, play/pause actions, video shape), page kept at full size and scaled in `BrowserZRoot`. YouTube and YouTube Music ads: `YouTubeAdScript` |
| F7 Cache, cookies, site data | cookie modes and clear-data in `feature/settings`, `core/webview` (`WebViewSiteDataManager`) |

## Build and test

```bash
./gradlew testDebugUnitTest        # unit tests (domain incl. tab order and bookmark folders, pop-up policy, video shape, filter engine, layout rule)
./gradlew :app:lintDebug           # lint
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # R8 build, signed with the debug key (local testing only)
./gradlew :app:generateBaselineProfile   # regenerate the baseline profile (needs a device or emulator)
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # cold-start A/B, with and without the profile
```

Needs JDK 17 and the Android SDK (compileSdk 35). `local.properties` points at the SDK.

## Modules

```
app                 wiring (AppContainer), activity, root screen
core:domain         models, ports, use cases (pure Kotlin)
core:data           SharedPreferences-backed repositories
core:webview        WebView tabs, request blocking, cookies, downloads
core:ui             theme, adaptive shell, shared components
engine:adblock      filter-list parser/matcher, list updater
feature:*           browser, search, library, settings, filters (UI only; depend on domain + ui)
baselineprofile     Macrobenchmark module: profile generator and startup benchmarks
```

Dependency rule: features never import `core:data`, `core:webview` or `engine:adblock`; `app` binds ports to adapters.

## Known limits

- Ad blocking works through WebView request interception: WebSocket traffic and most in-stream video ads are not blocked. YouTube is the exception: `YouTubeAdScript` strips the ad entries from the player data (uBlock Origin's json-prune approach) and skips any ad that still starts. It needs upkeep when YouTube changes its formats, and ads stitched into the video stream itself (server-side insertion) are not removed.
- Procedural hiding rules run only on the sites they name (generic ones would run on every page); `:xpath`, `:matches-css`, `:style()`, `:min-text-length`, HTML filters (`^`) and scriptlets (`##+js`, `#$#`) are skipped. Ad networks that rotate domains can slip through until the lists catch up; the pop-up and redirect blocker works on behaviour, not lists.
- Fake system-notification and "you won" scam overlays that ad networks inject are removed by behaviour (`OVERLAY_GUARD_JS` in `WebViewBrowserSession`), so rotating ad domains do not bring them back.
- After launch the built-in list is active at once and the full lists as soon as the saved engine (`files/filters/engine.bin`, about 6 MB) is loaded: 1.2 s on the 2 GB emulator, against 2.9 s for parsing the lists again in the same run (earlier measurements of up to 15 s were taken under other conditions). The saved engine is rebuilt whenever a list or the app changes.
- Page thumbnails in the tab switcher are taken when a tab leaves the screen, kept only in memory (about 60 KB each, dropped under memory pressure) and are not restored after a restart; pages drawn only by the GPU (video, WebGL) can come out blank.
- Background play and picture-in-picture are off by default and work on every site, YouTube included; YouTube offers them to Premium members, so check that this fits how the app is distributed. Notification controls need the notification permission (asked when the switch is turned on). The engine pauses video when the screen turns off; the app starts it again within about a second, so a short gap is possible. Swiping the app away from Recents stops playback. Only the tab that plays is kept alive.
- Pull to refresh starts only from an overscroll at the top that the page itself did not use, so maps, games and inner scrollers keep their gestures.
- Incognito needs a system WebView with multi-profile support; otherwise the menu entry is hidden.
- Startup: on a 2 GB emulator the window is displayed about 2–3.5 s after launch, with or without the baseline profile (the profile gains ~3% in a Macrobenchmark A/B). App code reaches its first draw in 1.1–1.4 s; the rest looks like the emulator's display pipeline. Not yet measured on a physical phone.
- The baseline profile is checked in under `app/src/release/generated/baselineProfiles/`; regenerate it after large UI changes.
- Translate opens Google Translate with the page URL only when chosen from the menu; nothing is sent automatically. Offline pages are stored in app-private storage and not available in incognito.
- DuckDuckGo is defined but hidden (`HIDDEN_PRESET_IDS` in `DefaultSearchEngines`).
