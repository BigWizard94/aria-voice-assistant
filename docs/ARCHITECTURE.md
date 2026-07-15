# Aria — Technical Architecture

## Overview

Aria is built on a clean, layered architecture optimized for:
- **Low memory footprint** (Android Go / 1-2GB RAM devices)
- **Offline-first operation** (Vosk STT, Android TTS)
- **Reactive UI** (Kotlin Flows + Jetpack Compose)
- **Extensibility** (command plugin system)

---

## Layer Diagram

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  MainActivity  OnboardingScreen  SettingsActivity       │
│  HistoryActivity  AriaOrb  MessageBubble                │
│              (Jetpack Compose)                          │
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow / collectAsState
┌────────────────────────▼────────────────────────────────┐
│                  ViewModel Layer                        │
│                  MainViewModel                          │
│         (AndroidViewModel + Coroutines)                 │
└────────────────────────┬────────────────────────────────┘
                         │ Service Binding / Intents
┌────────────────────────▼────────────────────────────────┐
│                  Service Layer                          │
│  AriaListenerService (Foreground — voice pipeline)      │
│  AriaVoiceInteractionService (Default assistant reg.)   │
└──────┬──────────────┬──────────────┬────────────────────┘
       │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌────▼────────────────────┐
│  STT Engine │ │  AI Engine │ │      TTS Engine          │
│   (Vosk)    │ │  (REST)    │ │  (Android native)        │
│  Offline    │ │  BYOK      │ │  Zero overhead           │
└──────┬──────┘ └─────┬──────┘ └────┬────────────────────┘
       │              │              │
┌──────▼──────────────▼──────────────▼────────────────────┐
│                   Data Layer                            │
│  Room Database (messages, sessions)                     │
│  DataStore Preferences (settings, API config)           │
└─────────────────────────────────────────────────────────┘
```

---

## Voice Pipeline (Detailed)

```
1. USER SPEAKS
   └─► Microphone (Android AudioRecord)
       └─► Vosk SpeechService (16kHz, mono)
           ├─► onPartialResult() → UI shows live text
           └─► onResult() → final transcript

2. COMMAND PARSING
   └─► CommandParser.parse(transcript)
       ├─► Regex patterns checked in priority order
       ├─► System commands → CommandExecutor (no AI needed)
       └─► Unknown → AiEngine

3. AI PROCESSING (if needed)
   └─► AiEngine.complete(config, conversationHistory)
       ├─► POST /chat/completions (OpenAI-compatible)
       ├─► Conversation history maintained (last 20 messages)
       └─► Result → TTS

4. SPEECH OUTPUT
   └─► TextToSpeechEngine.speak(response)
       ├─► Android TTS (built-in, zero APK size)
       ├─► Adjustable speed + pitch
       └─► Chunked for long responses

5. PERSISTENCE
   └─► Room DB saves every message
       └─► Conversation history screen
```

---

## Memory Optimization (Android Go)

| Component | Memory Usage | Strategy |
|---|---|---|
| Vosk small model | ~50MB RAM | Loaded once, kept in memory |
| Android TTS | ~5MB | System service, shared |
| Room DB | ~2MB | SQLite, minimal footprint |
| Compose UI | ~15MB | Lazy lists, no image loading |
| OkHttp client | ~3MB | Single instance, connection pooling |
| **Total** | **~75MB** | Well within 1GB RAM budget |

---

## Security Model

- **API keys**: Stored in Android DataStore (encrypted on Android 6+)
- **Conversation history**: Local SQLite only, never synced
- **Voice data**: Processed by Vosk on-device, never transmitted
- **Network**: Only outbound HTTPS to user-configured AI endpoint
- **Permissions**: Requested at runtime, gracefully degraded if denied
- **Backups**: API keys and conversation history excluded from cloud backup

---

## Adding New Voice Commands

To add a new command type:

1. **Add pattern to `CommandParser.kt`**:
```kotlin
private val MY_PATTERNS = listOf(
    Regex("""(?:my trigger phrase)\s+(.+)""", RegexOption.IGNORE_CASE)
)
// Add to parse() function
```

2. **Add sealed class to `Models.kt`**:
```kotlin
sealed class VoiceCommand {
    data class MyCommand(val param: String) : VoiceCommand()
    // ...
}
```

3. **Handle in `AriaListenerService.kt`**:
```kotlin
is VoiceCommand.MyCommand -> {
    speak("Executing my command")
    commandExecutor.myAction(command.param)
}
```

4. **Implement in `CommandExecutor.kt`**:
```kotlin
fun myAction(param: String) {
    // Android Intent or system call
}
```

---

## CI/CD Pipeline

```
Push to main/develop
    └─► GitHub Actions triggered
        ├─► Unit tests (./gradlew test)
        ├─► Lint check (./gradlew lint)
        └─► Debug APK build + artifact upload

Push tag v*.*.*
    └─► All above PLUS:
        ├─► Decode keystore from GitHub Secret
        ├─► Build signed release APK
        ├─► Create GitHub Release automatically
        └─► Upload APK to release assets
```

### Required GitHub Secrets for Release
| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

---

## Supported AI Endpoints

Aria uses the standard OpenAI Chat Completions API format:

```
POST {baseUrl}/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json

{
  "model": "model-name",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "max_tokens": 512,
  "temperature": 0.7,
  "stream": false
}
```

Any server implementing this interface works with Aria.