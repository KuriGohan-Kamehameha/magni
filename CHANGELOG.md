# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.5-alpha] - 2026-03-15

### Fixed
- URL preservation: history, bookmarks, and session no longer silently upgrade HTTP→HTTPS when HTTPS-Only mode is disabled.
- Tracking parameter stripping: WebTrends (`wt.*`) parameters now correctly matched after lower-casing normalization.
- WebView scroll listener no longer lost during tab-switch animations when the view is temporarily detached.
- SharedPreferences history and bookmark writes switched to `apply()` (async) to eliminate main-thread stalls during heavy browsing.

## [0.1.4-alpha] - 2026-03-14

### Changed
- Updated app version to `0.1.4-alpha` (`versionCode` 5).
- GitHub release workflow now skips signing gracefully when `SIGNING_KEY_BASE64` is not configured, falling back to unsigned APK.
- Pre-release tags (`-alpha`, `-beta`, `-rc`) are automatically published as GitHub pre-releases.

## [0.1.3] - 2026-03-14

### Changed
- Updated app version to `0.1.3` (`versionCode` 4).
- Enabled resource shrinking for release builds to keep distribution APKs lean.
- Added release workflow checks that require tag/app/changelog version consistency.
- Release automation now publishes both debug and release APK assets plus SHA-256 checksums.

### Fixed
- Improved Obtainium compatibility by standardizing GitHub release asset naming and publishing flow.

## [0.1.2] - 2026-03-14

### Changed
- Updated app version to `0.1.2` (`versionCode` 3).
- Moved default homepage from bundled asset to `https://github.com/kurigohan-kamehameha/magni`.
- Updated homepage settings hint to match the new default.

### Fixed
- Improved dual-display handoff stability between `OverviewActivity` and `ZoomActivity` to avoid duplicate-feeling concurrent app launches.
- Reduced task-stack churn during screen-role swaps by reusing existing activity tasks.
- Hardened swap lock handling so rapid repeated swaps are less likely to race.
- Reduced overview snapshot memory pressure by tightening snapshot size budgets and max edge caps.

### Removed
- Removed legacy bundled `start_page.html` home asset.

## [0.1.0] - 2026-03-06

### Added
- Initial release of Magni Web Browser for AYN Thor.
- Dual-screen browsing interface mimicking Nintendo DSi/3DS flow.
- Top screen overview with full-page mini-map and interactive viewport box.
- Bottom screen zoom view with interactive WebView and retro controls.
- Retro control buttons: BACK, FWD, RELOAD, URL, HOME, zoom in/out.
- Drag/tap on top map to reposition zoomed viewport on bottom screen.
- Privacy-first defaults:
  - HTTPS-only mode (HTTP disabled, cleartext blocked).
  - Third-party tracker domain blocking.
  - Optional "Block all cookies" setting.
  - Private browsing mode support.
  - Secure window flag to prevent OS-level screenshots.
- Cookie and geolocation controls.
- Anti-abuse guards (JS spam suppression, download rate limiting, popup blocking).
- Theme support including "DSi Classic" retro preset.
- Bookmark and history management.
- Download capability with privacy considerations.
- Responsive design adapting to single or dual-screen configurations.

### Technical
- Built with Kotlin and AndroidX.
- Targets Android API 26+ (minSdk 26, targetSdk 35).
- Uses WebView `capturePicture()` for full-page snapshots.
- Automatic fallback to standard single-display launch when secondary display unavailable.
- GitHub Actions CI/CD workflow for automated builds.

[0.1.3]: https://github.com/kurigohan-kamehameha/magni/releases/tag/v0.1.3
[0.1.2]: https://github.com/kurigohan-kamehameha/magni/releases/tag/v0.1.2
[0.1.0]: https://github.com/kurigohan-kamehameha/magni/releases/tag/v0.1.0
