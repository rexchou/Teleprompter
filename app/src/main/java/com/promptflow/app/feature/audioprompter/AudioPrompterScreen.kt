package com.promptflow.app.feature.audioprompter

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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.DarkCard
import com.promptflow.app.ui.theme.DarkSurface
import com.promptflow.app.ui.theme.SuccessMint

@Composable
fun AudioPrompterScreen(
    script: Script,
    viewModel: AudioPrompterViewModel = viewModel()
) {
    val recordingState by viewModel.recordingState.collectAsState()
    val currentAmplitude by viewModel.currentAmplitude.collectAsState()
    val isScrolling by viewModel.scrollEngine.isScrolling.collectAsState()
    val scrollOffset by viewModel.scrollEngine.scrollOffset.collectAsState()
    val scrollSpeed by viewModel.scrollEngine.scrollSpeed.collectAsState()

    val scrollState = rememberScrollState()

    // Smooth history list for sound waveform animation
    val waveformHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(currentAmplitude) {
        val normalized = (currentAmplitude / 32767f).coerceIn(0.08f, 1f)
        if (waveformHistory.size > 28) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SuccessMint, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "纯音频录音提词",
                        color = SuccessMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Duration Counter
            val durationMs = when (val state = recordingState) {
                is RecordingState.Recording -> state.durationMs
                is RecordingState.Paused -> state.durationMs
                is RecordingState.Finished -> state.durationMs
                else -> 0L
            }
            val seconds = (durationMs / 1000).toInt()
            val timerText = String.format("%02d:%02d", seconds / 60, seconds % 60)

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "REC DURATION",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timerText,
                    color = AccentCoral,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Middle Teleprompter Box with Reading Line Guide
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            // Active Reading Focus Guide Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFF6B4A))
                    .border(1.dp, Color(0x4DFF6B4A), RoundedCornerShape(8.dp))
            )

            // Scrollable text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 120.dp) // breathing room so text aligns with center
            ) {
                Text(
                    text = script.content,
                    color = Color(0xFFF1F5F9),
                    fontSize = 20.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Realtime Dynamic Waveform Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkCard)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● 48kHz 24-bit AAC",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val db = if (currentAmplitude > 0) {
                        (20 * Math.log10(currentAmplitude.toDouble() / 32767.0)).toInt()
                    } else -60
                    Text(
                        text = "$db dB 拾音指示",
                        color = AccentCoral,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Waveform Bars
                Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    val barCount = waveformHistory.size
                    if (barCount > 0) {
                        val barWidth = 6.dp.toPx()
                        val spacing = (size.width - barCount * barWidth) / (barCount + 1).coerceAtLeast(1)
                        waveformHistory.forEachIndexed { index, amp ->
                            val barHeight = (size.height * amp).coerceAtLeast(4.dp.toPx())
                            val x = spacing + index * (barWidth + spacing)
                            val y = (size.height - barHeight) / 2
                            drawRoundRect(
                                color = AccentCoral,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Deck Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause Scroll
            IconButton(
                onClick = { viewModel.scrollEngine.toggle() },
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Scroll Toggle",
                    tint = Color.White
                )
            }

            // Big Microphone Button
            val isRecording = recordingState is RecordingState.Recording
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .size(68.dp)
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
                    modifier = Modifier.size(32.dp)
                )
            }

            // Speed Slider Mini
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(90.dp)
            ) {
                Text(
                    text = "${String.format("%.1f", scrollSpeed)}x 滚速",
                    color = Color.LightGray,
                    fontSize = 11.sp
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
