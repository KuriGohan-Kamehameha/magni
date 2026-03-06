# magni

Magni Web Browser for AYN Thor dual-screen handheld.

Android browser prototype that mimics the old Nintendo DSi/3DS browsing flow:

- Top screen (`OverviewActivity`): full-page mini-map + orange viewport box.
- Bottom screen (`ZoomActivity`): interactive `WebView` with retro controls (`BACK`, `FWD`, `RELOAD`, `URL`, `HOME`, `-`, `+`).
- Drag/tap the top map to reposition the lower zoomed viewport.

## Project layout

- `app/src/main/java/com/ayn/magni/OverviewActivity.kt`
- `app/src/main/java/com/ayn/magni/ZoomActivity.kt`
- `app/src/main/java/com/ayn/magni/ui/OverviewMapView.kt`
- `app/src/main/java/com/ayn/magni/sync/BrowserSyncBus.kt`

## Build

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Build/install to your Thor device.

If Thor exposes the second screen as a secondary Android display, the app launches the zoom activity there using `ActivityOptions.setLaunchDisplayId`.

## Controls

- Touch webpage directly on the bottom screen.
- Touch/drag on top mini-map to move visible region.
- Pinch on top mini-map to zoom the map without changing viewport box size.
- Pinch on the bottom screen (or use `-`/`+`) to change browser magnification and viewport box size.
- `URL` opens an address/search dialog.
- Long-press `URL` to copy the current page URL.
- `HOME` loads a local retro start page.
- Settings -> `Theme` includes a `DSi Classic` preset that applies DSi-inspired chrome colors and a themed home page.

## Notes

- Uses WebView `capturePicture()` (deprecated, but useful here) to generate the top-screen full-page snapshot.
- If no secondary display is available, the app falls back to standard single-display launch.
- Privacy-first defaults:
  - HTTP is disabled (`https` only, cleartext blocked in manifest + network config).
  - Private browsing mode can be enabled in settings (no history writes, isolated session cleanup).
  - Optional `Block all cookies` setting disables first-party cookie acceptance in the WebView.
  - Known third-party tracker domains are blocked at request time.
  - Third-party cookies are blocked and private mode disables first-party cookie persistence.
  - `HTTPS-Only mode` (default on) blocks insecure HTTP navigations/downloads and keeps top-level browsing encrypted.
  - Navigation to non-HTTPS/non-asset schemes is blocked.
  - Website permission requests (geolocation/media) are denied.
  - Secure window flag is enabled to prevent OS-level screenshots/recording.
  - Anti-abuse guards: JS dialog spam is suppressed, automatic downloads are blocked/rate-limited, popup windows are denied, render-process crashes are recovered, and tab count is capped.
