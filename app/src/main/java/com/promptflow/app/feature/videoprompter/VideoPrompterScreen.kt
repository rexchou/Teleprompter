package com.promptflow.app.feature.videoprompter

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.SuccessMint

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

    LaunchedEffect(scrollOffset) {
        scrollState.scrollTo(scrollOffset.toInt())
    }

    LaunchedEffect(scrollState.maxValue) {
        viewModel.scrollEngine.maxScrollHeight = scrollState.maxValue.toFloat()
    }

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

        // Top Eye-Contact Teleprompter Card (Placed tightly below the punch hole)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(0.85f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99111318))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
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
                                text = "贴镜视线聚焦区",
                                color = SuccessMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "${String.format("%.1f", scrollSpeed)}x 匀速",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .height(180.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = script.content,
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bottom Control Bar Deck
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xD9111318))
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

                // Speed Slider
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(110.dp)
                ) {
                    Text(
                        text = "速度 ${String.format("%.1f", scrollSpeed)}x",
                        color = Color(0xFFCBD5E1),
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

                // Record Button
                val isRecording = recordingState is RecordingState.Recording
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
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
                                viewModel.startRecording(hasAudioPermission)
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
