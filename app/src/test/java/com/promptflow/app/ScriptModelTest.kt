package com.promptflow.app

import com.promptflow.app.core.model.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptModelTest {

    @Test
    fun testCharacterCount() {
        val script = Script(
            title = "测试台本",
            content = "今天我们要聊聊在 2026 年，独立创作者如何用极简工具实现高效率。"
        )
        assertEquals(37, script.characterCount)
    }

    @Test
    fun testEstimatedDurationCalculation() {
        // 220 chars per minute = 3.66 chars per second
        // 110 chars should be roughly 30 seconds
        val content = "一".repeat(110)
        val script = Script(
            title = "测试朗读耗时",
            content = content,
            speed = 1.0f
        )
        // 110 / (220/60) = 30 seconds
        assertEquals(30, script.estimatedDurationSeconds)
        assertEquals("00:30", script.formattedDuration)
    }

    @Test
    fun testSpeedAffectsDuration() {
        val content = "一".repeat(220)
        val normalScript = Script(title = "原速", content = content, speed = 1.0f)
        val fastScript = Script(title = "倍速", content = content, speed = 2.0f)

        assertEquals(60, normalScript.estimatedDurationSeconds)
        assertEquals(30, fastScript.estimatedDurationSeconds)
    }
}
