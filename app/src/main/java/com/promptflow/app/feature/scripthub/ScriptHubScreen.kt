package com.promptflow.app.feature.scripthub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.Script
import com.promptflow.app.feature.overlay.FloatingPrompterService
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.DarkCard
import com.promptflow.app.ui.theme.DarkSurface
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.SuccessMint

@Composable
fun ScriptHubScreen(
    onNavigateToVideo: (Script) -> Unit,
    onNavigateToAudio: (Script) -> Unit,
    viewModel: ScriptHubViewModel = viewModel()
) {
    val scripts by viewModel.scripts.collectAsState()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkSurface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = AccentCoral,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Script")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PromptFlow 语流",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "极简本地提词工作台 · 共 ${scripts.size} 篇台本",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无台本，点击右下角按钮创建",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(scripts, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            onVideoClick = { onNavigateToVideo(script) },
                            onAudioClick = { onNavigateToAudio(script) },
                            onOverlayClick = {
                                launchFloatingOverlay(context, script)
                            },
                            onDelete = { viewModel.deleteScript(script.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        CreateScriptDialog(
            onDismiss = { showDialog = false },
            onConfirm = { title, content ->
                viewModel.saveScript(title, content)
                showDialog = false
            }
        )
    }
}

@Composable
fun ScriptCard(
    script: Script,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stats badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${script.characterCount} 字",
                    color = AccentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = " · ", color = Color.Gray, fontSize = 11.sp)
                Text(
                    text = "预计朗读 ${script.formattedDuration}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = script.content,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row (Video, Audio, Overlay)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Video Mode
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33EF4444))
                        .border(1.dp, Color(0x4DEF4444), RoundedCornerShape(12.dp))
                        .clickable { onVideoClick() }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = RecordRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "视频录制", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Audio Mode
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FF6B4A))
                        .border(1.dp, Color(0x4DFF6B4A), RoundedCornerShape(12.dp))
                        .clickable { onAudioClick() }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "音频录音", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Floating Window
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x3310B981))
                        .border(1.dp, Color(0x4D10B981), RoundedCornerShape(12.dp))
                        .clickable { onOverlayClick() }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = null,
                        tint = SuccessMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "悬浮窗", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun launchFloatingOverlay(context: Context, script: Script) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            return
        }
    }
    FloatingPrompterService.start(context, script.title, script.content)
}

@Composable
fun CreateScriptDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(text = "新建台本", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("台本标题 (可选)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentCoral,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("台本正文内容") },
                    minLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentCoral,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, content) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCoral)
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = Color.Gray)
            }
        }
    )
}
