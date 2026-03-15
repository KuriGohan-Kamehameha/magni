# Security Successor Handoff

Date: 2026-03-14
Repo: magni

## Completed in this pass (top 7)

1. Release workflow no longer publishes unsigned release APKs.
2. CI workflow no longer uploads unsigned release APK artifacts.
3. Workflow hardening applied:
   - GitHub Actions usages pinned to immutable commit SHAs.
   - CI workflow permissions explicitly restricted to `contents: read`.
4. Private mode now disables WebView DOM storage and continues to wipe WebStorage/cookies on private-mode transitions and clear-data flows.
5. Exported deep-link trust narrowed with a trusted-host policy and explicit user override path (Open Once or Trust & Open).
6. Reader mode now serves generated HTML with strict CSP/no-referrer isolation to block script execution and active content.
7. Lifecycle-safe data wipe semantics now clear on stronger boundaries (`onStop`/`TRIM_MEMORY_UI_HIDDEN`) with bounded async cookie-clear callback verification.

## Files changed in this pass

- `.github/workflows/release.yml`
- `.github/workflows/android-ci.yml`
- `app/src/main/java/com/ayn/magni/ZoomActivity.kt`
- `app/src/main/res/values/strings.xml`

## Validation status

- Local app build status before this pass was green.
- This pass includes app runtime hardening in `ZoomActivity` (private-mode DOM storage policy).
- Next operator should run workflow lint/check in PR and verify GitHub Actions execution on:
  - push to feature branch (CI)
  - tag push in test repo/fork (release)

## Remaining security/privacy work (ordered)

1. Download abuse throttling tuning
   - `app/src/main/java/com/ayn/magni/ZoomActivity.kt`
   - Revisit window/attempt constants and consider per-origin quotas.

2. ProGuard/R8 keep rules minimization
   - `app/proguard-rules.pro`
   - Remove blanket `-keep class com.ayn.magni.**` and keep only required reflection/entry points.

3. Network trust hardening beyond cleartext block
   - `app/src/main/res/xml/network_security_config.xml`
   - Evaluate certificate pinning strategy for high-value domains/endpoints.

4. Clipboard privacy UX
   - `app/src/main/java/com/ayn/magni/ZoomActivity.kt`
   - Add warning/confirmation for paste-and-go on non-URL content; optional auto-clear guidance.

5. Tracker list update strategy
   - `app/src/main/java/com/ayn/magni/data/TrackerBlocker.kt`
   - Move from static in-app list to signed periodic update feed.

6. URL tracking parameter stripping precision
    - `app/src/main/java/com/ayn/magni/data/UrlPrivacySanitizer.kt`
    - Reassess broad keys (`ref`, `si`, `pp`, `spm`) to reduce functional breakage.

## Suggested next execution plan

1. Implement items 1-2 in one branch and run:
   - `./gradlew :app:assembleDebug :app:assembleRelease`
2. Implement items 3-4 and run same build + smoke test on device.
3. Implement items 5-6 with regression tests for URL handling and bookmark/session behavior.

## Notes

- Encrypted preferences migration is already in place (`SecureBrowserPrefs`).
- App now defaults JavaScript to disabled.
- Keep release signing secrets mandatory for release workflow.
