package com.bigwizard.aria.stt

/**
 * States of the offline speech-to-text engine, shared by
 * [SpeechRecognitionEngine] and the listener service.
 */
sealed class SttState {
    object Idle : SttState()
    object LoadingModel : SttState()

    /** First-run model download. [progress] is 0f..1f, or null when the size is unknown. */
    data class Downloading(val progress: Float?) : SttState()

    object Ready : SttState()
    object Listening : SttState()
    object Timeout : SttState()

    data class Result(val text: String) : SttState()
    data class Error(val message: String) : SttState()
}
