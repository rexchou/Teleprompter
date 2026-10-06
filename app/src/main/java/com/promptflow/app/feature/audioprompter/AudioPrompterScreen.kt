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
    var fontSizeSp by remember { mutableIntStateOf(22) }

    // Dynamic amplitude history (24 segments)
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
            .background(DarkSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Info Deck
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
                            .background(if (isRecording) SuccessMint else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "正在录制 · AAC 256k" else "准备就绪 · 纯音频模式",
                        color = if (isRecording) SuccessMint else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Duration Counter Deck
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "录制耗时",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timerText,
                    color = AccentCoral,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Middle Teleprompter Box with Active Reading Line Guide
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            // Active Reading Focus Guide Line with Anchoring Arrows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFF5E3A))
                    .border(1.dp, Color(0x4DFF5E3A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "▶", color = AccentCoral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "当前阅读基准线",
                    color = Color(0x66FF5E3A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = "◀", color = AccentCoral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Scrollable Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 120.dp) // breathing room so first line starts at center
            ) {
                Text(
                    text = TextHighlightHelper.formatScriptText(script.content, Color(0xFFF1F5F9)),
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.55f).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Top fade gradient mask
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(DarkCard, Color.Transparent)
                        )
                    )
            )

            // Bottom fade gradient mask
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, DarkCard)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Studio Dual-Sided Waveform & dB Meter Card
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
                        text = "● 48kHz 24-bit AAC 立体声采集",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val db = if (currentAmplitude > 0) {
                        (20 * Math.log10(currentAmplitude.toDouble() / 32767.0)).toInt()
                    } else -60

                    val dbColor = when {
                        db > -3 -> RecordRed
                        db > -12 -> AccentAmber
                        else -> Color(0xFF10B981)
                    }

                    Text(
                        text = "$db dB 实时电平",
                        color = dbColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Studio Mirror Waveform Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    val barCount = waveformHistory.size
                    if (barCount > 0) {
                        val barWidth = 6.dp.toPx()
                        val spacing = (size.width - barCount * barWidth) / (barCount + 1).coerceAtLeast(1)
                        val centerY = size.height / 2

                        // Center axis line
                        drawLine(
                            color = Color(0x26FFFFFF),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 1.dp.toPx()
                        )

                        waveformHistory.forEachIndexed { index, amp ->
                            val halfHeight = ((size.height / 2) * amp).coerceAtLeast(2.dp.toPx())
                            val x = spacing + index * (barWidth + spacing)
                            val barColor = if (amp > 0.85f) RecordRed else AccentCoral

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, centerY - halfHeight),
                                size = Size(barWidth, halfHeight * 2),
                                cornerRadius = CornerRadius(3.dp.toPx())
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

            // Bookmark Marker Button
            IconButton(
                onClick = {
                    if (isRecording) {
                        Toast.makeText(context, "📍 已在 $timerText 添加标记点", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "录制中方可添加段落标记点", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = "Add Marker",
                    tint = AccentAmber
                )
            }

            // Big Microphone Record / Stop Button
            val infiniteTransition = rememberInfiniteTransition()
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
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

            // Font Size Selector (Aa)
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .clickable {
                        fontSizeSp = when (fontSizeSp) {
                            18 -> 22
                            22 -> 26
                            else -> 18
                        }
                    },
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
                modifier = Modifier.width(85.dp)
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
