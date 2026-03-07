# Magni - Web Browser for AYN Thor

Magni is a privacy-first Android web browser designed for the AYN Thor dual-screen handheld. It reimagines web browsing with a nostalgic Nintendo DSi/3DS-inspired dual-screen interface.

## Features

### Innovative Dual-Screen Interface
- **Top Screen**: Full-page overview mini-map with interactive viewport box showing the visible area
- **Bottom Screen**: Interactive WebView with intuitive zoom controls and retro buttons
- Seamless viewport synchronization between screens

### Privacy & Security
- **HTTPS-Only**: HTTP disabled and cleartext traffic blocked by default
- **Tracker Blocking**: Comprehensive list of known third-party tracker domains filtered at request time
- **Cookie Control**: Fine-grained control over first-party and third-party cookies
- **Private Mode**: Optional incognito browsing with isolated session cleanup
- **Permission Denial**: Geolocation and media permissions blocked by default
- **Secure Rendering**: Window FLAG_SECURE enabled to prevent OS-level screenshots
- **Anti-Abuse**: Protection against JS spam, rate-limited downloads, popup blocking, and render-process recovery

### Retro Controls
- `BACK` / `FWD`: Navigate history
- `RELOAD`: Refresh page
- `URL`: Open address/search dialog (long-press to copy)
- `HOME`: Load retro start page with custom themes
- `-` / `+`: Zoom in and out

### Themes
- Includes "DSi Classic" preset with authentic Nintendo DSi-inspired chrome colors and themed home page

## Requirements

- **Android SDK**: API 26 or higher
- **Java**: Version 17 or later
- **Gradle**: 8.0 or higher (included with Android Studio)
- **Hardware**: AYN Thor device recommended (app adapts to single-screen if needed)

## Installation

### From Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/ds-browser.git
   cd ds-browser
   ```

2. **Open in Android Studio**:
   - Open the project folder in Android Studio
   - Let Gradle sync automatically

3. **Build and run**:
   ```bash
   # Debug build and install
   ./gradlew :app:assembleDebug :app:installDebug
   
   # Release build (minified)
   ./gradlew :app:assembleRelease
   ```

### Configuration

Set your Android SDK location in `local.properties`:
```properties
sdk.dir=/path/to/android/sdk
```

## Project Structure

```
app/src/main/
├── java/com/ayn/magni/
│   ├── OverviewActivity.kt       # Top screen - full-page overview
│   ├── ZoomActivity.kt           # Bottom screen - interactive viewer
│   ├── ui/OverviewMapView.kt     # Mini-map rendering and interaction
│   └── sync/BrowserSyncBus.kt    # Screen synchronization
├── res/
│   ├── values/strings.xml        # UI strings
│   ├── values/colors.xml         # Theme colors
│   └── xml/network_security_config.xml  # Network policies
└── AndroidManifest.xml
```

## Usage

### Basic Navigation
1. Tap directly on the bottom screen webpage to interact
2. Drag on the top mini-map to move the visible region
3. Pinch on the top mini-map to zoom it without changing the viewport box

### Zoom Control
- Use `-`/`+` buttons or pinch on bottom screen to adjust magnification
- Viewport box resizes in sync with zoom level

### Settings
- Access theme options including "DSi Classic" preset
- Configure privacy settings (cookies, private mode)
- Customize start page and home button behavior

## Technical Details

- **Language**: Kotlin with AndroidX framework
- **Target API**: 35 (Android 15)
- **Min API**: 26 (Android 8.0)
- **WebView Technique**: Uses `capturePicture()` (deprecated but functionally ideal) for full-page snapshots on overview screen
- **Dual-Screen Support**: Automatically uses `ActivityOptions.setLaunchDisplayId` if secondary display available; falls back to single-screen mode otherwise
- **Obfuscation**: ProGuard minification enabled in release builds to protect privacy logic and reduce APK size

## Privacy Notes

Magni prioritizes user privacy with sensible defaults:
- Encrypted HTTPS-only browsing by default
- No tracking or telemetry
- Cookies are opt-in (disabled by default)
- IP address protection through network policies
- Open-source code for transparency and auditability

## Architecture

- **OverviewActivity**: Manages top-screen lifecycle and receives viewport updates from ZoomActivity
- **ZoomActivity**: Manages bottom-screen WebView, handles user gestures, and broadcasts viewport changes
- **BrowserSyncBus**: Shared event bus for coordinating state between screens
- **OverviewMapView**: Custom canvas-based view rendering full-page mini-map with interactive viewport box

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on submitting issues and pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by the Nintendo DSi and 3DS web browsers
- Built for the AYN Thor handheld gaming device
- Powered by Android WebView and Kotlin
