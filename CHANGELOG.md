# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] – 2026-03-06

### Added

- **Dual-screen layout**: `OverviewActivity` (full-page mini-map with orange viewport indicator) and `ZoomActivity` (interactive `WebView`) launched on separate displays via `ActivityOptions.setLaunchDisplayId`.
- **Retro controls**: `BACK`, `FWD`, `RELOAD`, `URL`, `HOME`, `-`, `+` navigation buttons.
- **DSi Classic theme**: DSi-inspired chrome colors with a themed local home page.
- **Privacy-first defaults**: HTTPS-only mode, tracker domain blocking, third-party cookie blocking, optional private browsing mode, geolocation/media permission denial, secure window flag, and anti-abuse guards.
- **Single-display fallback**: graceful degradation when no secondary Android display is available.
- **Overview map interaction**: tap or drag on the mini-map to reposition the zoomed viewport; pinch to zoom the overview without changing viewport box size.
- **`BrowserSyncBus`**: lightweight event bus synchronising scroll and zoom state between activities.
- **GitHub Actions CI**: lint + debug build on every push and pull request to `main`.
