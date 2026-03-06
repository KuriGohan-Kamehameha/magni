# Contributing to Magni

Thank you for your interest in contributing! Magni is a small prototype project, so contributions of any size are welcome.

---

## Table of contents

1. [Setup](#setup)
2. [Build & lint](#build--lint)
3. [Project goals](#project-goals)
4. [Code style](#code-style)
5. [Submitting a pull request](#submitting-a-pull-request)
6. [Reporting bugs](#reporting-bugs)

---

## Setup

1. **Clone** the repository:
   ```bash
   git clone https://github.com/KuriGohan-Kamehameha/magni.git
   cd magni
   ```
2. Open the project in **Android Studio** (Electric Eel or later).
3. Allow Gradle to sync and download dependencies automatically.
4. You will need a device or emulator running **Android 12+** (API 31+).  
   For dual-screen testing, an AYN Thor device (or a multi-display emulator configuration) is recommended.

---

## Build & lint

```bash
# Lint check + debug build (mirrors CI)
./gradlew :app:lintDebug :app:assembleDebug
```

Please ensure the above command completes without new lint warnings before opening a PR. The CI pipeline runs the same command automatically on every push and pull request.

---

## Project goals

- Keep the retro DSi/3DS browsing metaphor intact.
- Maintain privacy-first defaults (HTTPS-only, tracker blocking, etc.).
- Avoid introducing heavy third-party dependencies — the codebase should stay small and auditable.
- Support single-display fallback so the app is usable without a secondary screen.

---

## Code style

- **Kotlin** – follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Formatting** – use the Android Studio default formatter (4-space indent, no trailing spaces).
- **Comments** – prefer self-documenting code; add comments only where the intent is not obvious.
- **Scope** – keep changes focused; avoid mixing unrelated refactors in a single PR.

---

## Submitting a pull request

1. Fork the repository and create a feature branch:
   ```bash
   git checkout -b fix/my-bug-fix
   ```
2. Make your changes and ensure `./gradlew :app:lintDebug :app:assembleDebug` passes.
3. Write a clear PR description explaining *what* changed and *why*.
4. Reference any related issue with `Fixes #<number>` or `Relates to #<number>`.
5. Open the PR against the `main` branch.

If your fork redistributes Magni (even as a modified version), the **GPL-3.0** license requires you to make your source code available under the same license. Opening a PR upstream is the easiest way to satisfy this requirement and share improvements with everyone.

---

## Reporting bugs

Please open a [bug report issue](https://github.com/KuriGohan-Kamehameha/magni/issues/new?template=bug_report.md) and include:

- Device model and Android version.
- Steps to reproduce the issue.
- Expected vs. actual behaviour.
- Logcat output or a screenshot if applicable.
