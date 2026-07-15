# Contributing to Aria 🎙️

First off — thank you! Aria is built by people who believe everyone deserves
a private, open voice assistant. Every contribution matters.

---

## 🚀 Ways to Contribute

### 🐛 Report Bugs
Found something broken? [Open an issue](https://github.com/BigWizard94/aria-voice-assistant/issues/new?template=bug_report.md)

Please include:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if possible

### 💡 Request Features
Have an idea? [Start a discussion](https://github.com/BigWizard94/aria-voice-assistant/discussions/new)

### 🌍 Add Language Support
Aria uses Vosk for offline STT. To add a new language:
1. Download the Vosk model for your language from [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)
2. Add the model name constant to `SpeechRecognitionEngine.kt`
3. Add the language option to `SettingsActivity.kt`
4. Submit a PR with documentation

### 🔌 Build a Voice Command Plugin
Want to add a new voice command? See [ARCHITECTURE.md](docs/ARCHITECTURE.md#adding-new-voice-commands)

### 📖 Improve Documentation
Fix typos, add examples, translate the README — all welcome!

### ⭐ Star & Share
The simplest contribution — star the repo and tell others about Aria!

---

## 🛠️ Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator (API 26+)
- Git

### Clone & Setup
```bash
git clone https://github.com/BigWizard94/aria-voice-assistant.git
cd aria-voice-assistant

# Download Vosk model (required for STT)
cd app/src/main/assets
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
rm vosk-model-small-en-us-0.15.zip
cd ../../../..

# Open in Android Studio
# Run on device (emulator mic support is limited)
```

### Project Structure
See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for full details.

---

## 📋 Pull Request Process

1. **Fork** the repository
2. **Create a branch**: `git checkout -b feature/your-feature-name`
3. **Make your changes** following the code style below
4. **Test** on a real device if possible (especially for voice features)
5. **Commit** with a clear message: `git commit -m 'feat: add wake word detection'`
6. **Push**: `git push origin feature/your-feature-name`
7. **Open a PR** with a clear description of what you changed and why

### Commit Message Format
We use [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add wake word detection
fix: resolve crash on Android 8 devices
docs: update README with Ollama setup guide
refactor: simplify CommandParser regex patterns
test: add unit tests for AiEngine
chore: update Vosk to 0.3.48
```

---

## 🎨 Code Style

- **Kotlin** — follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Compose** — follow [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md)
- **Comments** — document public APIs and complex logic
- **No hardcoded strings** — use `strings.xml`
- **No hardcoded colors** — use theme colors from `Theme.kt`
- **Memory conscious** — always consider Android Go (1-2GB RAM) devices

### Key Principles
1. **Privacy first** — never add any tracking, analytics, or cloud sync without explicit user consent
2. **Offline capable** — core features must work without internet
3. **Android Go compatible** — test on low-end hardware or emulator with 1GB RAM
4. **Battery friendly** — minimize background work, use efficient APIs

---

## 🧪 Testing

```bash
# Run unit tests
./gradlew test

# Run lint
./gradlew lint

# Build debug APK
./gradlew assembleDebug
```

For voice features, test on a real device — emulator microphone support is unreliable.

---

## 📜 License

By contributing to Aria, you agree that your contributions will be licensed
under the [Apache License 2.0](LICENSE).

---

## 💬 Community

- **Discussions**: [GitHub Discussions](https://github.com/BigWizard94/aria-voice-assistant/discussions)
- **Issues**: [GitHub Issues](https://github.com/BigWizard94/aria-voice-assistant/issues)

---

*Built with ❤️ by the community. Thank you for making Aria better for everyone.*