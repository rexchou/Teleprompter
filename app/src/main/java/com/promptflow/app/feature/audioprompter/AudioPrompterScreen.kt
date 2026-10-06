package com.promptflow.app.feature.audioprompter

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.core.util.TextHighlightHelper
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.DarkCard
import com.promptflow.app.ui.theme.DarkSurface
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.StudioBlack
import com.promptflow.app.ui.theme.SuccessMint

@Composable
fun AudioPrompterScreen(
    script: Script,
    viewModel: AudioPrompterViewModel = viewModel()
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsState()
    val currentAmplitude by viewModel.currentAmplitude.collectAsState()
    val isScrolling by viewModel.scrollEngine.isScrolling.collectAsState()
    val scrollOffset by viewModel.scrollEngine.scrollOffset.collectAsState()
    val scrollSpeed by viewModel.scrollEngine.scrollSpeed.collectAsState()

    val scrollState = rememberScrollState()
    var fontSizeSp by remember { mutableIntStateOf(24) }

    // Dynamic amplitude history (24 segments)
    val waveformHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(currentAmplitude) {
        val normalized = (currentAmplitude / 32767f).coerceIn(0.08f, 1f)
        if (waveformHistory.size > 24) {
            waveformHistory.removeAt(0)
        }
        waveformHistory.add(normalized)
    }

    LaunchedEffect(scrollOffset) {
        scrollState.scrollTo(scrollOffset.toInt())
    }

    LaunchedEffect(scrollState.maxValue) {
        viewModel.scrollEngine.maxScrollHeight = scrollState.maxValue.toFloat()
    }

    val isRecording = recordingState is RecordingState.Recording

    val durationMs = when (val state = recordingState) {
        is RecordingState.Recording -> state.durationMs
        is RecordingState.Paused -> state.durationMs
        is RecordingState.Finished -> state.durationMs
        else -> 0L
    }
    val seconds = (durationMs / 1000).toInt()
    val timerText = String.format("%02d:%02d", seconds / 60, seconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBlack)
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        // 1. Compact Top Status Bar (Height ~44dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isRecording) RecordRed else SuccessMint, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.width(180.dp)
                )
            }

            // Big Timer
            Text(
                text = timerText,
                color = if (isRecording) AccentCoral else Color(0xFF94A3B8),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        }

        // 2. Immersive Full-Screen Teleprompter Viewport (Takes 70%+ of screen!)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Subtle Left Focus Margin Cursor (Outside of text! Never overlaps words!)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(AccentCoral)
            )

            // Very subtle full-width reading band behind text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.Center)
                    .background(Color(0x0DFFFFFF))
            )

            // Spacious Full Text Viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 180.dp, bottom = 220.dp) // Generous top/bottom margin for lookahead
            ) {
                Text(
                    text = TextHighlightHelper.formatScriptText(script.content, Color(0xFFF1F5F9)),
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.65f).sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }

            // Top Dissolve Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(StudioBlack, StudioBlack.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
            )

            // Bottom Dissolve Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, StudioBlack.copy(alpha = 0.8f), StudioBlack)
                        )
                    )
            )
        }

        // 3. Compact Bottom Studio Visualizer & Deck (Height ~140dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Mini Waveform & dB Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Waveform bars
                Canvas(
                    modifier = Modifier
                        .width(160.dp)
                        .height(24.dp)
                ) {
                    val barCount = waveformHistory.size
                    if (barCount > 0) {
                        val barWidth = 4.dp.toPx()
                        val spacing = (size.width - barCount * barWidth) / (barCount + 1).coerceAtLeast(1)
                        val centerY = size.height / 2

                        waveformHistory.forEachIndexed { index, amp ->
                            val halfHeight = ((size.height / 2) * amp).coerceAtLeast(1.5.dp.toPx())
                            val x = spacing + index * (barWidth + spacing)
                            val barColor = if (amp > 0.85f) RecordRed else AccentCoral

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, centerY - halfHeight),
                                size = Size(barWidth, halfHeight * 2),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                }

                // Audio Status Tag
                val db = if (currentAmplitude > 0) {
                    (20 * Math.log10(currentAmplitude.toDouble() / 32767.0)).toInt()
                } else -60
                Text(
                    text = "$db dB · AAC 256k",
                    color = if (db > -6) AccentAmber else Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Control Buttons Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE6141824))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(28.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Scroll
                IconButton(
                    onClick = { viewModel.scrollEngine.toggle() },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Scroll Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Bookmark Marker Button
                IconButton(
                    onClick = {
                        if (isRecording) {
                            Toast.makeText(context, "📍 已在 $timerText 添加标记点", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请在录音中添加标记", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Marker",
                        tint = AccentAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Big Mic Record / Stop Button
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(if (isRecording) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(AccentCoral)
                        .clickable {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Font Size Selector (18 -> 24 -> 30)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26FFFFFF))
                        .clickable {
                            fontSizeSp = when (fontSizeSp) {
                                18 -> 24
                                24 -> 30
                                else -> 18
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${fontSizeSp}sp",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Speed Slider Mini
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp)
                ) {
                    Text(
                        text = "${String.format("%.1f", scrollSpeed)}x 滚速",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { viewModel.scrollEngine.setSpeed(it) },
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCoral,
                            activeTrackColor = AccentCoral,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}
