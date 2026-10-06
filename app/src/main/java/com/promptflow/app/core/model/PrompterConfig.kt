package com.promptflow.app.core.model

/**
 * Configuration options for the teleprompter window.
 */
data class PrompterConfig(
    val scrollSpeed: Float = 1.2f,          // 0.5f to 3.0f
    val fontSizeSp: Int = 18,               // 14 to 32
    val backgroundOpacity: Float = 0.70f,   // 0.2f to 1.0f
    val isMirroredHorizontal: Boolean = false, // for physical teleprompter split glass
    val isMirroredVertical: Boolean = false,
    val eyeContactOffsetTopPercent: Float = 0.08f, // text proximity to front camera
    val maxWidthPercent: Float = 0.75f      // narrower width avoids eye darting
)

/**
 * Generic state of audio/video recording.
 */
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val durationMs: Long, val amplitude: Int = 0) : RecordingState
    data class Paused(val durationMs: Long) : RecordingState
    data class Finished(val durationMs: Long, val outputUri: String) : RecordingState
    data class Error(val message: String) : RecordingState
}
