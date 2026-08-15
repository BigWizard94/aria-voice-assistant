package com.bigwizard.aria.stt

/**
 * Represents the various states of the speech-to-text engine used across
 * SpeechRecognitionEngine and AriaListenerService.
 *
 * Adding this sealed class fixes CI compilation errors where the symbol
 * `SttState` was missing.
 */
sealed class SttState {
    object Idle : SttState()
    object LoadingModel : SttState()
    object Ready : SttState()
    object Listening : SttState()
    object Timeout : SttState()

    data class Result(val text: String) : SttState()
    data class Error(val message: String) : SttState()
}
