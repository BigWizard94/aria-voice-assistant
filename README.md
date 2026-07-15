<div align="center">

# 🎙️ Aria — Open Voice Assistant

### The free, private, open-source Android voice assistant that replaces Google Assistant

[![License](https://img.shields.io/badge/License-Apache%202.0-7C4DFF.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-00E5FF.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7C4DFF.svg)](https://kotlinlang.org)
[![Build](https://github.com/BigWizard94/aria-voice-assistant/actions/workflows/build.yml/badge.svg)](https://github.com/BigWizard94/aria-voice-assistant/actions)
[![Stars](https://img.shields.io/github/stars/BigWizard94/aria-voice-assistant?color=7C4DFF)](https://github.com/BigWizard94/aria-voice-assistant/stargazers)

**No Google account. No cloud. No tracking. Just your voice and your AI.**

[📲 Download APK](#-install) • [🚀 Features](#-features) • [🛠️ Build](#️-build-from-source) • [🤝 Contribute](#-contributing)

---

![Aria Demo](docs/aria-demo.gif)

</div>

---

## 🤔 Why Aria?

You've felt it. You pick up your phone, long-press the home button, and Google Assistant pops up — asking you to sign in, sending your voice to Google's servers, and giving you results shaped by their algorithms and policies.

**Aria is different.**

Built by [@BigWizard94](https://github.com/BigWizard94) on a budget Android phone running Android Go — because the people who need a better assistant the most are often the ones with the least powerful hardware.

| Feature | Google Assistant / Gemini Go | **Aria** |
|---|---|---|
| Google account required | ✅ Yes | ❌ **Never** |
| Voice data sent to cloud | ✅ Always | ❌ **Never** |
| Works offline | ❌ No | ✅ **Yes** |
| Open source | ❌ No | ✅ **100%** |
| Choose your AI model | ❌ No | ✅ **Any model** |
| Android Go compatible | ⚠️ Limited | ✅ **Optimized** |
| Free forever | ❌ Freemium | ✅ **Always free** |
| Minimum Android version | Android 13 Go | ✅ **Android 8.0+** |

---

## ✨ Features

### 🔒 Privacy First
- **Offline speech recognition** via [Vosk](https://alphacephei.com/vosk/) — your voice never leaves your device
- **Zero telemetry** — no analytics, no crash reporting to third parties
- **Local conversation history** — stored only on your device
- **API keys encrypted** — stored in Android's secure DataStore

### 🧠 Universal AI Brain (BYOK)
Aria works with **any OpenAI-compatible API**. You're never locked in:

| Provider | Cost | Speed | Notes |
|---|---|---|---|
| [Groq](https://console.groq.com) | 🆓 Free tier | ⚡ Ultra-fast | **Recommended for beginners** |
| [OpenRouter](https://openrouter.ai) | 🆓 Free models available | Fast | 100+ models |
| [Ollama](https://ollama.ai) | 🆓 Free (local) | Varies | 100% offline AI |
| [LM Studio](https://lmstudio.ai) | 🆓 Free (local) | Varies | Easy local setup |
| [OpenAI](https://platform.openai.com) | 💰 Paid | Fast | GPT-4o, etc. |
| [Together AI](https://api.together.xyz) | 💰 Low cost | Fast | Many open models |
| **Your own endpoint** | ❓ Yours | Yours | Full control |

### 📱 True Android Assistant
- **Replaces Google Assistant** — set as default, activated by long-pressing home button
- **Works on Android Go** — optimized for 1-2GB RAM devices
- **Supports Android 8.0+** — covers 97%+ of active Android devices
- **Starts on boot** — always ready when you need it

### 🎯 Voice Commands
| Say... | Aria does... |
|---|---|
| *"Call Mom"* | Calls your contact |
| *"Text John saying I'll be late"* | Opens SMS with message |
| *"Set alarm for 7:30 AM"* | Creates alarm |
| *"Timer for 10 minutes"* | Starts countdown |
| *"Open YouTube"* | Launches app |
| *"Search for pasta recipes"* | Opens browser search |
| *"Play jazz music"* | Opens Spotify/YouTube Music |
| *"What's the capital of France?"* | AI answers instantly |
| *"Tell me a joke"* | AI responds conversationally |
| *"Remind me in 30 minutes"* | Sets timer |

### 🌍 Multi-Language Support
Vosk supports 20+ languages. Switch your speech model for:
`English` • `Spanish` • `French` • `German` • `Portuguese` • `Chinese` • `Russian` • `Arabic` • `Hindi` • `Japanese` • and more

---

## 📲 Install

### Option 1: Download APK (Easiest)
1. Go to the [**Releases page**](https://github.com/BigWizard94/aria-voice-assistant/releases)
2. Download the latest `Aria-vX.X.X.apk`
3. On your Android device: **Settings → Security → Install unknown apps** → Allow
4. Tap the downloaded APK to install
5. Open Aria, complete the 2-minute setup, and start talking!

### Option 2: Build from Source
See [Build from Source](#️-build-from-source) below.

---

## 🚀 Quick Setup (2 minutes)

### Step 1: Get a Free AI Key
The easiest way to start is with **Groq** — it's free and incredibly fast:
1. Go to [console.groq.com](https://console.groq.com)
2. Sign up (free)
3. Create an API key
4. Copy it

### Step 2: Configure Aria
1. Open Aria
2. The setup wizard will guide you through:
   - Select **"Groq (Free & Ultra-Fast)"**
   - Paste your API key
   - Model: `llama3-8b-8192` (pre-filled)
3. Tap **"Let's Go!"**

### Step 3: Set as Default Assistant (Optional but Recommended)
1. **Settings → Apps → Default Apps → Digital Assistant**
2. Select **Aria**
3. Long-press your home button — Aria appears instead of Google!

---

## 🛠️ Build from Source

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK (API 26+)

### Clone & Build
```bash
git clone https://github.com/BigWizard94/aria-voice-assistant.git
cd aria-voice-assistant
```

### Download Vosk Speech Model
```bash
# Download the small English model (~50MB)
mkdir -p app/src/main/assets/vosk-model-small-en-us-0.15
cd app/src/main/assets
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
rm vosk-model-small-en-us-0.15.zip
```

### Build Debug APK
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK
```bash
# Set up signing (see docs/SIGNING.md)
./gradlew assembleRelease
```

---

## 🏗️ Architecture

```
aria-voice-assistant/
├── app/src/main/kotlin/com/bigwizard/aria/
│   ├── AriaApplication.kt          # App singleton, DI root
│   ├── ai/
│   │   ├── AiEngine.kt             # Universal OpenAI-compatible REST client
│   │   └── CommandParser.kt        # Voice command pattern matching
│   ├── stt/
│   │   └── SpeechRecognitionEngine.kt  # Vosk offline STT
│   ├── tts/
│   │   └── TextToSpeechEngine.kt   # Android native TTS
│   ├── service/
│   │   ├── AriaListenerService.kt  # Core foreground service (voice pipeline)
│   │   └── AriaVoiceInteractionService.kt  # Default assistant registration
│   ├── data/
│   │   ├── local/
│   │   │   ├── AriaDatabase.kt     # Room database
│   │   │   └── PreferencesManager.kt  # DataStore preferences
│   │   └── model/
│   │       └── Models.kt           # Data classes & sealed states
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── MainActivity.kt     # Main Compose UI
│   │   │   ├── MainViewModel.kt    # UI state management
│   │   │   ├── OnboardingScreen.kt # First-run setup wizard
│   │   │   ├── SettingsActivity.kt # Settings screen
│   │   │   └── HistoryActivity.kt  # Conversation history
│   │   ├── components/
│   │   │   ├── AriaOrb.kt          # Animated voice orb (Canvas)
│   │   │   └── MessageBubble.kt    # Chat message component
│   │   └── theme/
│   │       ├── Theme.kt            # Material3 color schemes
│   │       └── Typography.kt       # Text styles
│   ├── util/
│   │   └── CommandExecutor.kt      # System command execution
│   └── receiver/
│       └── BootReceiver.kt         # Auto-start on device boot
└── .github/workflows/
    └── build.yml                   # CI/CD: auto-build & release APKs
```

### Voice Pipeline
```
Microphone
    ↓
Vosk STT (offline, on-device)
    ↓
CommandParser (regex pattern matching)
    ↓
┌─────────────────────────────────────┐
│  System Command?  │   AI Query?     │
│  Call/SMS/Alarm   │   AiEngine      │
│  Timer/App/Search │   (BYOK REST)   │
└─────────────────────────────────────┘
    ↓
Android TTS (on-device speech output)
    ↓
Speaker
```

---

## 🤝 Contributing

Aria is built by the community, for the community. All contributions welcome!

### Ways to Contribute
- 🐛 **Report bugs** — [Open an issue](https://github.com/BigWizard94/aria-voice-assistant/issues)
- 💡 **Request features** — [Start a discussion](https://github.com/BigWizard94/aria-voice-assistant/discussions)
- 🌍 **Add language support** — Add a Vosk model for your language
- 🔌 **Build plugins** — Extend Aria's command system
- 📖 **Improve docs** — Fix typos, add examples
- ⭐ **Star the repo** — Helps others discover Aria!

### Development Setup
```bash
git clone https://github.com/BigWizard94/aria-voice-assistant.git
cd aria-voice-assistant
# Open in Android Studio
# Download Vosk model (see Build from Source)
# Run on device or emulator
```

### Pull Request Guidelines
1. Fork the repo
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## 🗺️ Roadmap

### v1.0 — MVP ✅
- [x] Offline STT via Vosk
- [x] Universal BYOK AI (OpenAI-compatible)
- [x] Android TTS output
- [x] Default assistant registration
- [x] Basic voice commands (call, SMS, alarm, timer, apps, search)
- [x] Conversation history
- [x] Onboarding wizard
- [x] CI/CD pipeline

### v1.1 — Smart Features 🔜
- [ ] Wake word detection ("Hey Aria")
- [ ] Conversation memory across sessions
- [ ] Read notifications aloud
- [ ] Offline AI mode (Phi-3 Mini via ONNX)
- [ ] Widget for home screen

### v1.2 — Power Features 🔮
- [ ] Plugin/skill system
- [ ] Tasker integration
- [ ] F-Droid listing
- [ ] Wear OS companion app
- [ ] Custom wake word training
- [ ] Multi-language auto-detection

### v2.0 — Ecosystem 🌟
- [ ] Aria Skills Marketplace
- [ ] Smart home integration
- [ ] Cross-device sync (optional, E2E encrypted)
- [ ] Desktop companion (Windows/Linux/Mac)

---

## 📄 License

```
Copyright 2026 Chase Lucas | Bigwizard Media

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 🙏 Acknowledgments

- [**Vosk**](https://alphacephei.com/vosk/) — Incredible offline speech recognition
- [**Alpha Cephei**](https://alphacephei.com/) — For making Vosk free and open source
- [**Groq**](https://groq.com/) — For the fastest free AI inference
- [**Jetpack Compose**](https://developer.android.com/jetpack/compose) — Modern Android UI
- Every open source developer who made privacy-first software possible

---

<div align="center">

**Built with ❤️ by [@BigWizard94](https://github.com/BigWizard94) | Bigwizard Media**

*"The best assistant is one that works for you — not for a corporation."*

⭐ **Star this repo if Aria helped you!** ⭐

</div>