package com.promptflow.app.core.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.promptflow.app.ui.theme.HighlightGold

/**
 * Parses scripts and formats highlighted keywords (enclosed in 【...】 or [...])
 * with a warm accent color and subtle background highlight.
 */
object TextHighlightHelper {

    fun formatScriptText(text: String, defaultColor: Color = Color.White): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            val regex = Regex("""(【[^】]+】|\[[^\]]+\])""")
            val matches = regex.findAll(text)

            for (match in matches) {
                val range = match.range
                if (range.first > currentIndex) {
                    withStyle(SpanStyle(color = defaultColor, fontWeight = FontWeight.Normal)) {
                        append(text.substring(currentIndex, range.first))
                    }
                }

                // Highlighted segment
                withStyle(
                    SpanStyle(
                        color = HighlightGold,
                        fontWeight = FontWeight.Bold,
                        background = Color(0x26FFD166)
                    )
                ) {
                    append(match.value)
                }
                currentIndex = range.last + 1
            }

            if (currentIndex < text.length) {
                withStyle(SpanStyle(color = defaultColor, fontWeight = FontWeight.Normal)) {
                    append(text.substring(currentIndex))
                }
            }
        }
    }
}
