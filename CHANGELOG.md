# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.1.0]: https://github.com/yourusername/ds-browser/releases/tag/v0.1.0
