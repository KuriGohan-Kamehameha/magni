# magni

> **Prototype / experimental** – Magni is a dual-screen Android web browser built for the [AYN Thor](https://www.ayn.hk/thor) handheld.  
> It is not a production browser; expect rough edges.

[![Android CI](https://github.com/KuriGohan-Kamehameha/magni/actions/workflows/android-ci.yml/badge.svg)](https://github.com/KuriGohan-Kamehameha/magni/actions/workflows/android-ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Overview

Magni reimagines the Nintendo DSi/3DS browsing experience on a modern dual-screen Android device:

| Screen | Activity | Role |
|--------|----------|------|
| **Overview (top)** | `OverviewActivity` | Full-page mini-map with an orange viewport indicator box |
| **Zoom (bottom)** | `ZoomActivity` | Interactive `WebView` with retro-style navigation controls |

Tapping or dragging on the overview map repositions the zoomed viewport on the lower screen, giving an intuitive bird's-eye view of any web page.

## Screenshot

![Magni browser screenshot](magni.png)

## Features

- **Dual-screen browsing** – overview map and interactive viewport on separate displays.
- **Retro controls** – `BACK`, `FWD`, `RELOAD`, `URL`, `HOME`, `-` / `+` zoom buttons.
- **DSi Classic theme** – DSi-inspired chrome colors and a themed home page preset.
- **Privacy-first defaults** – HTTPS-only, tracker blocking, no third-party cookies, and more (see [Privacy & Security](#privacy--security-defaults) below).
- **Graceful fallback** – runs in single-display mode when no secondary display is present.

## Project layout

```
app/src/main/java/com/ayn/magni/
├── OverviewActivity.kt      # Top-screen mini-map host
├── ZoomActivity.kt          # Bottom-screen WebView host
├── ui/OverviewMapView.kt    # Custom view for the mini-map
└── sync/BrowserSyncBus.kt   # Event bus syncing scroll/zoom state
```

## Build instructions

### Android Studio

1. Open this folder in Android Studio (Electric Eel or later).
2. Allow Gradle to sync.
3. Select your AYN Thor device as the deployment target.
4. Run **▶ Run 'app'**.

### Command line

```bash
# Lint + debug build
./gradlew :app:lintDebug :app:assembleDebug
```

The resulting APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Dual-screen behavior

When the AYN Thor exposes its second screen as a secondary Android display, Magni launches `ZoomActivity` on that display using:

```kotlin
ActivityOptions.setLaunchDisplayId(secondaryDisplayId)
```

If no secondary display is detected at launch time, both activities fall back to the primary display so the app remains usable on single-screen devices.

## Controls

| Input | Action |
|-------|--------|
| Touch bottom screen | Interact directly with the web page |
| Touch / drag top mini-map | Move the zoomed viewport |
| Pinch on top mini-map | Zoom the overview map (viewport box size unchanged) |
| Pinch on bottom screen, or `-` / `+` | Change browser magnification and viewport box size |
| `URL` | Open address / search dialog |
| Long-press `URL` | Copy current page URL to clipboard |
| `HOME` | Load local retro start page |
| Settings → Theme | Switch to **DSi Classic** preset |

## Privacy & security defaults

> **Note:** Magni is a prototype. These defaults are best-effort and have not been audited.

- **HTTPS-only** – cleartext HTTP is blocked in the manifest and network security config; `HTTPS-Only mode` (default on) also blocks insecure navigations and downloads.
- **Tracker blocking** – known third-party tracker domains are blocked at request time.
- **Cookie controls** – third-party cookies are blocked; *Block all cookies* option disables first-party acceptance; private mode clears cookies on session end.
- **Private browsing** – optional mode that disables history writes and uses an isolated session.
- **Permissions denied** – geolocation and media permission requests are denied automatically.
- **Secure window flag** – prevents OS-level screenshots and screen recording of the app.
- **Anti-abuse guards** – JS dialog spam suppressed, automatic downloads blocked/rate-limited, popup windows denied, render-process crashes recovered, tab count capped.

## Forks / improvements

You are welcome to fork this project and adapt it. A few things to keep in mind:

- Magni is licensed under **GPL-3.0**. Any version you distribute (modified or not) must be released under the same license with source available.
- If you improve or fix something, please consider opening a pull request upstream so everyone benefits.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md) for how to report vulnerabilities.

## License

Copyright © 2026 KuriGohan-Kamehameha.  
Licensed under the [GNU General Public License v3.0](LICENSE).
