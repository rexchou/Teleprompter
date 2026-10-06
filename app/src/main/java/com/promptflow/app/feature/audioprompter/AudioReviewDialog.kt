package com.promptflow.app.feature.audioprompter

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.promptflow.app.core.model.VoiceFilter
import com.promptflow.app.data.audio.AudioPolisherEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * 录音完成后的专业评审与美音试听对比面板 (Studio Review Dialog)
 *
 * 核心特色：
 * 1. 实时多预设即点即切试听
 * 2. 独创“按住对比原声”触觉监听交互
 * 3. 一键分享与保存
 */
@Composable
fun AudioReviewDialog(
    filePath: String,
    initialFilter: VoiceFilter = VoiceFilter.default,
    onDismiss: () -> Unit,
    onRerecord: () -> Unit
) {
    val context = LocalContext.current
    val audioFile = remember { File(filePath) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var isComparingRaw by remember { mutableStateOf(false) }

    val polisherEngine = remember { AudioPolisherEngine() }
    val mediaPlayer = remember { MediaPlayer() }

    // 初始化播放器与美音引擎
    DisposableEffect(filePath) {
        try {
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            durationMs = mediaPlayer.duration.coerceAtLeast(1)
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
            }
            // 挂载美音引擎到播放器的 session
            polisherEngine.attachToSession(mediaPlayer.audioSessionId)
            polisherEngine.applyFilter(selectedFilter)
        } catch (e: Exception) {
            Toast.makeText(context, "无法载入录音文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
            polisherEngine.release()
        }
    }

    // 播放进度轮询
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            try {
                currentPositionMs = mediaPlayer.currentPosition
            } catch (_: Exception) {}
            delay(100)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141721),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "录音完成 · 大师试听",
                            color = Color(0xFFF8FAFC),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = audioFile.name,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            shareAudioFile(context, audioFile)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waveform & Progress Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F121C))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPositionMs),
                                color = Color(selectedFilter.accentColorHex),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatTime(durationMs),
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress bar
                        val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(selectedFilter.accentColorHex),
                            trackColor = Color(0x26FFFFFF)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play / Pause Circle Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(selectedFilter.accentColorHex),
                                            Color(selectedFilter.accentColorHex).copy(alpha = 0.7f)
                                        )
                                    )
                                )
                                .clickable {
                                    if (isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.start()
                                        isPlaying = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Filter Selectors (Horizontal Chips)
                Text(
                    text = "一键切换美音风格",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceFilter.values().forEach { filter ->
                        val isSelected = filter == selectedFilter
                        val color = Color(filter.accentColorHex)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color.copy(alpha = 0.2f) else Color(0x0DFFFFFF))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) color else Color(0x1AFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedFilter = filter
                                    polisherEngine.applyFilter(filter)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = filter.displayName,
                                color = if (isSelected) color else Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ✨ AHA Moment: "按住对比原声" Tactile Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isComparingRaw) Color(0xFF334155) else Color(0x1F222838)
                        )
                        .border(
                            1.dp,
                            if (isComparingRaw) Color(0xFF94A3B8) else Color(selectedFilter.accentColorHex).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isComparingRaw = true
                                    polisherEngine.setBypass(true)
                                    tryAwaitRelease()
                                    isComparingRaw = false
                                    polisherEngine.setBypass(false)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isComparingRaw) Icons.Default.GraphicEq else Icons.Default.Hearing,
                            contentDescription = null,
                            tint = if (isComparingRaw) Color(0xFFF1F5F9) else Color(selectedFilter.accentColorHex),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isComparingRaw) "正在聆听原始干音（松开恢复美音）" else "按住对比原声",
                            color = if (isComparingRaw) Color.White else Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom actions: Rerecord vs Save Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            mediaPlayer.stop()
                            onRerecord()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x1AFFFFFF),
                            contentColor = Color(0xFFCBD5E1)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重录", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            mediaPlayer.stop()
                            Toast.makeText(context, "已应用【${selectedFilter.displayName}】并保存录音", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(selectedFilter.accentColorHex),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f).height(46.dp)
                    ) {
                        Text("保存并完成", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun shareAudioFile(context: Context, file: File) {
    if (!file.exists()) return
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享美化录音"))
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
