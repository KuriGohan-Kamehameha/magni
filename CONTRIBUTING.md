# Contributing to Magni

Thank you for your interest in contributing to Magni! This document provides guidelines and instructions for contributing.

## Getting Started

### Prerequisites
- Android Studio (latest version)
- Android SDK (API 26+)
- Java 17 or later
- Gradle (included with Android Studio)

### Setup
1. Clone the repository
2. Open the project folder in Android Studio
3. Let Gradle sync automatically
4. Connect an AYN Thor device or use an appropriate Android emulator
5. Build and install:
   ```bash
   ./gradlew :app:assembleDebug :app:installDebug
   ```

## Development Workflow

### Before Making Changes
1. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Keep commits focused and atomic
3. Write clear commit messages

### Code Style
- Follow Kotlin naming conventions and best practices
- Use meaningful variable and function names
- Keep functions focused and reasonably sized
- Add comments for complex logic
- Ensure no debug logging in production code

### Testing Your Changes
1. Build the debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Run lint checks:
   ```bash
   ./gradlew :app:lintDebug
   ```
3. Manually test on device, especially:
   - Basic browsing functionality
   - Viewport and zoom interaction
   - Privacy settings
   - Theme changes

### Submitting Changes
1. Push your feature branch to GitHub
2. Create a Pull Request with:
   - Clear title describing the change
   - Description of what and why
   - Testing instructions
   - Any relevant issue numbers

## Reporting Issues
When reporting bugs, please include:
- Clear description of the issue
- Steps to reproduce
- Expected behavior
- Actual behavior
- Device/Android version details
- Screenshots if applicable

## Code of Conduct
- Be respectful and constructive
- Provide helpful feedback
- Focus on the code, not individuals
- Maintain a welcoming environment

## Questions?
Feel free to open an issue or discussion for questions about the project.
