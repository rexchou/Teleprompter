package com.promptflow.app.feature.videoprompter

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.core.util.TextHighlightHelper
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.HighlightGold
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.SuccessMint
import kotlinx.coroutines.delay

@Composable
fun VideoPrompterScreen(
    script: Script,
    hasAudioPermission: Boolean,
    viewModel: VideoPrompterViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordingState by viewModel.recordingState.collectAsState()
    val isScrolling by viewModel.scrollEngine.isScrolling.collectAsState()
    val scrollOffset by viewModel.scrollEngine.scrollOffset.collectAsState()
    val scrollSpeed by viewModel.scrollEngine.scrollSpeed.collectAsState()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val scrollState = rememberScrollState()

    // Configurable typography and display options
    var fontSizeSp by remember { mutableIntStateOf(18) }
    var isMirrored by remember { mutableStateOf(false) }

    // Pre-roll countdown state (3, 2, 1, 0)
    var countdownValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdownValue) {
        if (countdownValue > 0) {
            delay(1000)
            if (countdownValue == 1) {
                // Countdown complete, start actual recording & prompter
                countdownValue = 0
                viewModel.startRecording(hasAudioPermission)
            } else {
                countdownValue -= 1
            }
        }
    }

    LaunchedEffect(scrollOffset) {
        scrollState.scrollTo(scrollOffset.toInt())
    }

    LaunchedEffect(scrollState.maxValue) {
        viewModel.scrollEngine.maxScrollHeight = scrollState.maxValue.toFloat()
    }

    val isRecording = recordingState is RecordingState.Recording

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // CameraX Live Preview in AndroidView
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                    viewModel.cameraManager.bindCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Eye-Contact Teleprompter Card
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(0.90f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Punch-hole proximity alignment guide badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xD90D0F15))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▲ 视线请平视此处（打孔镜头）",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Glassmorphic Prompter Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xD912151E), Color(0xBF0E1017))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color(0x4DFFFFFF), Color(0x1AFFFFFF))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // Card status & Quick Config Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(SuccessMint, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "聚焦视区 · ${String.format("%.1f", scrollSpeed)}x",
                                color = SuccessMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Font Size Toggle
                            Text(
                                text = "${fontSizeSp}sp",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x26FBBF24))
                                    .clickable {
                                        fontSizeSp = when (fontSizeSp) {
                                            16 -> 20
                                            20 -> 24
                                            else -> 16
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mirror Toggle
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = "Mirror",
                                tint = if (isMirrored) AccentCoral else Color.Gray,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { isMirrored = !isMirrored }
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Reset Scroll
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                tint = Color.LightGray,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.scrollEngine.reset() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Text viewport with smooth gradient fade masks
                    Box(
                        modifier = Modifier
                            .height(180.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isMirrored) rotationY = 180f
                            }
                    ) {
                        // Scrolling Content
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = TextHighlightHelper.formatScriptText(script.content),
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.55f).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Top dissolve gradient mask
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xD912151E), Color.Transparent)
                                    )
                                )
                        )

                        // Bottom dissolve gradient mask
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xBF0E1017))
                                    )
                                )
                        )
                    }
                }
            }
        }

        // 3-2-1 Countdown Overlay
        AnimatedVisibility(
            visible = countdownValue > 0,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(2.dp, AccentCoral, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdownValue",
                    color = AccentCoral,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Bottom Floating Control Bar Deck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xEB0D0F16))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play / Pause Scroll
                IconButton(
                    onClick = { viewModel.scrollEngine.toggle() }
                ) {
                    Icon(
                        imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Scroll",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Speed Slider & Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(115.dp)
                ) {
                    Text(
                        text = "滚速 ${String.format("%.1f", scrollSpeed)}x",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
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

                // Big Record Button with Pulsing Dot & Timecode
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(RecordRed)
                        .clickable {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                countdownValue = 3 // Trigger 3-second countdown
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (isRecording) pulseScale else 1f)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val durationText = when (recordingState) {
                        is RecordingState.Recording -> {
                            val sec = (recordingState as RecordingState.Recording).durationMs / 1000
                            String.format("%02d:%02d", sec / 60, sec % 60)
                        }
                        is RecordingState.Finished -> "已保存"
                        else -> "录像"
                    }
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Flip Camera
                IconButton(
                    onClick = {
                        previewViewRef?.let {
                            viewModel.cameraManager.switchCamera(lifecycleOwner, it)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
