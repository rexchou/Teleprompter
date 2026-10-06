package com.promptflow.app.feature.audioprompter

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import com.promptflow.app.core.model.VoiceFilter
import com.promptflow.app.core.util.TextHighlightHelper
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.DarkCard
import com.promptflow.app.ui.theme.DarkSurface
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.StudioBlack
import com.promptflow.app.ui.theme.SuccessMint

@OptIn(ExperimentalMaterial3Api::class)
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
    val voiceFilter by viewModel.currentVoiceFilter.collectAsState()

    var showVoiceFilterSheet by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    var fontSizeSp by remember { mutableIntStateOf(24) }

    // Focus line vertical position: 0.10f (top line - prompts from very first word) vs 0.32f vs 0.50f
    var focusPositionRatio by remember { mutableFloatStateOf(0.10f) }

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
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        // 1. Compact Top Status Bar (Height ~44dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isRecording) RecordRed else SuccessMint, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Big Timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RecordRed)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = timerText,
                    color = if (isRecording) AccentCoral else Color(0xFF94A3B8),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 2. Immersive Full-Screen Teleprompter Viewport with Calibrated Upper-35% Focus Line
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            val focusY = maxHeight * focusPositionRatio

            // Subtle full-width reading band behind text at exact calibrated height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .offset(y = focusY - 26.dp)
                    .background(Color(0x0DFFFFFF))
            )

            // Subtle Left Focus Margin Cursor (Aligned to upper 35% focus line!)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .offset(y = focusY - 24.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(AccentCoral)
            )

            // Spacious Full Text Viewport (Starts aligned with focus line so Line 1 is immediately prompted)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = (focusY - 20.dp).coerceAtLeast(14.dp),
                        bottom = maxHeight - focusY + 120.dp
                    )
            ) {
                Text(
                    text = TextHighlightHelper.formatScriptText(script.content, Color(0xFFF1F5F9)),
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.65f).sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }

            // Top Dissolve Scrim (Only visible when scrolled up to never mask the top line)
            val topScrimAlpha by animateFloatAsState(
                targetValue = if (scrollOffset > 10f) 1f else 0f,
                label = "topScrimAlpha"
            )
            if (topScrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    StudioBlack.copy(alpha = topScrimAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

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
            // Mini Waveform & dB Strip (Clickable to switch Voice Filter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, Color(voiceFilter.accentColorHex).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .clickable { showVoiceFilterSheet = true }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filterColor = Color(voiceFilter.accentColorHex)

                // Waveform bars
                Canvas(
                    modifier = Modifier
                        .width(130.dp)
                        .height(24.dp)
                ) {
                    val barCount = waveformHistory.size
                    if (barCount > 0) {
                        val barWidth = 3.5.dp.toPx()
                        val spacing = (size.width - barCount * barWidth) / (barCount + 1).coerceAtLeast(1)
                        val centerY = size.height / 2

                        waveformHistory.forEachIndexed { index, amp ->
                            val halfHeight = ((size.height / 2) * amp).coerceAtLeast(1.5.dp.toPx())
                            val x = spacing + index * (barWidth + spacing)
                            val barColor = if (amp > 0.85f) RecordRed else filterColor

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, centerY - halfHeight),
                                size = Size(barWidth, halfHeight * 2),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                }

                // Voice Filter VIP Badge & dB Indicator
                val db = if (currentAmplitude > 0) {
                    (20 * Math.log10(currentAmplitude.toDouble() / 32767.0)).toInt()
                } else -60

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(filterColor.copy(alpha = 0.18f))
                            .border(0.5.dp, filterColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🪄 ${voiceFilter.displayName}",
                            color = filterColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$db dB",
                        color = if (db > -6) AccentAmber else Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Control Buttons Deck (Symmetrical, spacious 5-control layout)
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
                // 1. Play / Pause Scroll
                IconButton(
                    onClick = { viewModel.scrollEngine.toggle() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Scroll Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Focus Line Position Selector (Top 10% vs Upper 32% vs Center 50%)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26FFFFFF))
                        .clickable {
                            focusPositionRatio = when {
                                focusPositionRatio <= 0.15f -> 0.32f
                                focusPositionRatio <= 0.35f -> 0.50f
                                else -> 0.10f
                            }
                            val label = when {
                                focusPositionRatio <= 0.15f -> "顶端首行 (从第1行起提)"
                                focusPositionRatio <= 0.35f -> "中上预瞄 (32%)"
                                else -> "屏幕居中 (50%)"
                            }
                            Toast.makeText(context, "提词基准: $label", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = when {
                        focusPositionRatio <= 0.15f -> "顶端首行"
                        focusPositionRatio <= 0.35f -> "中上32%"
                        else -> "居中50%"
                    }
                    Text(
                        text = label,
                        color = AccentAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Big Mic Record / Stop Button (Center Hero)
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

                // 4. Font Size Selector (18 -> 24 -> 30)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
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

                // 5. Speed Selector Pill (1-Tap Cycle 0.8x -> 1.0x -> 1.2x -> 1.5x -> 2.0x)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26FFFFFF))
                        .clickable {
                            val nextSpeed = when {
                                scrollSpeed < 1.0f -> 1.0f
                                scrollSpeed < 1.2f -> 1.2f
                                scrollSpeed < 1.4f -> 1.5f
                                scrollSpeed < 1.8f -> 2.0f
                                else -> 0.8f
                            }
                            viewModel.scrollEngine.setSpeed(nextSpeed)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${String.format("%.1f", scrollSpeed)}x速",
                        color = AccentCoral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Voice Filter Selector Bottom Sheet
        if (showVoiceFilterSheet) {
            VoiceFilterSelectorSheet(
                sheetState = rememberModalBottomSheetState(),
                currentFilter = voiceFilter,
                onFilterSelected = { selected ->
                    viewModel.setVoiceFilter(selected)
                    showVoiceFilterSheet = false
                    Toast.makeText(context, "已切换声音美化: ${selected.displayName}", Toast.LENGTH_SHORT).show()
                },
                onDismissRequest = { showVoiceFilterSheet = false }
            )
        }

        // Finished Audio Review & Audition Dialog
        val finishedState = recordingState as? RecordingState.Finished
        if (finishedState != null) {
            AudioReviewDialog(
                filePath = finishedState.outputUri,
                initialFilter = voiceFilter,
                onDismiss = { viewModel.resetToIdle() },
                onRerecord = {
                    viewModel.resetToIdle()
                    viewModel.startRecording()
                }
            )
        }
    }
}
