package com.promptflow.app.core.model

/**
 * Domain model representing a teleprompter script.
 */
data class Script(
    val id: Long = 0,
    val title: String,
    val content: String,
    val speed: Float = 1.0f,
    val fontSizeSp: Int = 18,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Estimated word/character count.
     */
    val characterCount: Int
        get() = content.trim().length

    /**
     * Estimated speaking time in seconds based on ~220 characters per minute standard speaking rate.
     */
    val estimatedDurationSeconds: Int
        get() {
            if (characterCount == 0) return 0
            val charactersPerSecond = 220.0f / 60.0f
            return (characterCount / (charactersPerSecond * speed)).toInt().coerceAtLeast(1)
        }

    val formattedDuration: String
        get() {
            val minutes = estimatedDurationSeconds / 60
            val seconds = estimatedDurationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
