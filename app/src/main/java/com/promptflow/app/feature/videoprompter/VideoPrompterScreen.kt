package com.promptflow.app.feature.videoprompter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.core.util.TextHighlightHelper
import com.promptflow.app.feature.overlay.FloatingPrompterService
import com.promptflow.app.ui.theme.AccentAmber
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.HighlightGold
import com.promptflow.app.ui.theme.RecordRed
import com.promptflow.app.ui.theme.SuccessMint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 视频录制提词界面 (全面参考海外顶尖提词器 UI 交互架构)
 * - 全屏取景 + 自由拖拽/伸缩悬浮提词卡片 (Draggable & Resizable Prompter Card)
 * - 0% ~ 100% 卡片背景透明度实时调节
 * - 极简步进 HUD 快速调参 (A-/A+ 字号、-/+ 滚速、一键镜像、重置、暂停/播放)
 * - 焦点参考导引框 (Active Reading Focus Frame)，首行完美对齐
 * - 一键桌面画中画悬浮窗 (Floating Mode)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPrompterScreen(
    script: Script,
    hasAudioPermission: Boolean,
    onBack: () -> Unit = {},
    viewModel: VideoPrompterViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordingState by viewModel.recordingState.collectAsState()
    val isScrolling by viewModel.scrollEngine.isScrolling.collectAsState()
    val scrollOffset by viewModel.scrollEngine.scrollOffset.collectAsState()
    val scrollSpeed by viewModel.scrollEngine.scrollSpeed.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val scrollState = rememberScrollState()

    // 1. 悬浮提词卡片状态 (位置、尺寸与视觉)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var cardOpacity by remember { mutableFloatStateOf(0.70f) }      // 0.0f (纯透) ~ 1.0f (纯黑)
    var cardHeightDp by remember { mutableIntStateOf(240) }         // 170dp, 240dp, 320dp
    var cardWidthPercent by remember { mutableFloatStateOf(0.88f) } // 75%, 88%, 96%
    var fontSizeSp by remember { mutableIntStateOf(20) }            // 14 ~ 32sp
    var isMirrored by remember { mutableStateOf(false) }            // 硬件分光镜水平镜像

    // 2. 读词焦点高亮基准线 (0.12f 顶端首行, 0.32f 中上, 0.50f 居中)
    var focusPositionRatio by remember { mutableFloatStateOf(0.12f) }

    // 3. 倒计时录制与高级设置面板
    var countdownDuration by remember { mutableIntStateOf(3) }      // 0: 立即录制, 3: 3秒倒计时, 5: 5秒倒计时
    var activeCountdown by remember { mutableIntStateOf(0) }
    var showQuickSettingsSheet by remember { mutableStateOf(false) }

    // 倒计时核心循环
    LaunchedEffect(activeCountdown) {
        if (activeCountdown > 0) {
            delay(1000)
            if (activeCountdown == 1) {
                activeCountdown = 0
                viewModel.startRecording(hasAudioPermission)
            } else {
                activeCountdown -= 1
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
    val durationMs = when (val state = recordingState) {
        is RecordingState.Recording -> state.durationMs
        is RecordingState.Paused -> state.durationMs
        is RecordingState.Finished -> state.durationMs
        else -> 0L
    }
    val timerText = run {
        val totalSec = (durationMs / 1000).toInt()
        String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val maxAvailableWidthPx = constraints.maxWidth.toFloat()
        val maxAvailableHeightPx = constraints.maxHeight.toFloat()

        // ---------------------------------------------------------
        // 1. CameraX 实时取景全屏画布
        // ---------------------------------------------------------
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
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

        // ---------------------------------------------------------
        // 2. 顶部微型全局状态与动作条 (Edge-to-edge Top Action Bar)
        // ---------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回与台本胶囊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0D0F17))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = script.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.width(100.dp)
                )
            }

            // 录制指示胶囊
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isRecording) RecordRed.copy(alpha = 0.85f) else Color(0x990D0F17))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isRecording) Color.White else SuccessMint, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) "REC $timerText" else "就绪 · ${countdownDuration}s预备",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 右侧工具 (悬浮窗与镜头翻转)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 桌面画中画悬浮窗启动
                IconButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, FloatingPrompterService::class.java).apply {
                                putExtra(FloatingPrompterService.EXTRA_SCRIPT_TITLE, script.title)
                                putExtra(FloatingPrompterService.EXTRA_SCRIPT_CONTENT, script.content)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            Toast.makeText(context, "桌面悬浮提词窗已就绪，可切到其他App使用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0D0F17))
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "开启桌面悬浮窗",
                        tint = AccentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 翻转前/后置摄像头
                IconButton(
                    onClick = {
                        previewViewRef?.let {
                            viewModel.cameraManager.switchCamera(lifecycleOwner, it)
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0D0F17))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "切换摄像头",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // 3. 核心：海外参考 App 式「可拖拽/可调节尺寸」悬浮提词卡片
        // ---------------------------------------------------------
        val focusLineY = (cardHeightDp * focusPositionRatio).dp

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .offset {
                    // 安全约束，防止卡片拖拽完全飞出屏幕外
                    val clampedX = offsetX.coerceIn(-maxAvailableWidthPx * 0.45f, maxAvailableWidthPx * 0.45f)
                    val clampedY = offsetY.coerceIn(0f, maxAvailableHeightPx * 0.55f)
                    IntOffset(clampedX.roundToInt(), clampedY.roundToInt())
                }
                .padding(top = 46.dp)
                .fillMaxWidth(cardWidthPercent)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0C0F17).copy(alpha = cardOpacity))
                    .border(
                        1.dp,
                        if (cardOpacity > 0.05f) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 3.1 提词卡片顶部拖拽手柄与快速调节档位 (Drag Handle Header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 拖拽指示器条 (Pill Handle)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .background(Color(0xB3FFFFFF), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "拖动",
                            color = Color(0xB3FFFFFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 快速档位切换组 (透明度、高度、焦点线)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 1) 背景透明度快捷循环: 0% (全透) -> 40% (半透) -> 70% (沉浸) -> 95% (遮光)
                        val opacityPercent = (cardOpacity * 100).toInt()
                        Text(
                            text = "透 $opacityPercent%",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x26FBBF24))
                                .clickable {
                                    cardOpacity = when {
                                        cardOpacity >= 0.90f -> 0.0f
                                        cardOpacity < 0.20f -> 0.40f
                                        cardOpacity < 0.60f -> 0.70f
                                        else -> 0.95f
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // 2) 高度切换: 170dp -> 240dp -> 320dp
                        Text(
                            text = "高 ${cardHeightDp}dp",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x263B82F6))
                                .clickable {
                                    cardHeightDp = when (cardHeightDp) {
                                        170 -> 240
                                        240 -> 320
                                        else -> 170
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // 3) 焦点基准位切换: 顶端首行 (12%) -> 中上 (32%) -> 居中 (50%)
                        val focusLabel = when {
                            focusPositionRatio < 0.20f -> "顶端"
                            focusPositionRatio < 0.40f -> "中上"
                            else -> "居中"
                        }
                        Text(
                            text = "位 $focusLabel",
                            color = SuccessMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2610B981))
                                .clickable {
                                    focusPositionRatio = when {
                                        focusPositionRatio < 0.20f -> 0.32f
                                        focusPositionRatio < 0.40f -> 0.50f
                                        else -> 0.12f
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // 3.2 滚动文本可视视窗 (支持焦点框高亮、首行完美可见对齐、硬件分光镜像)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeightDp.dp)
                        .graphicsLayer {
                            if (isMirrored) scaleX = -1f
                        }
                ) {
                    // 焦点指示发光框 (Reading Focus Guide)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = focusLineY)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x26FBBF24))
                            .border(1.dp, Color(0x66FBBF24), RoundedCornerShape(6.dp))
                    )

                    // 滚动台本文档
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = TextHighlightHelper.formatScriptText(script.content),
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .padding(
                                    top = focusLineY, // 首行直接对齐高亮框
                                    bottom = (cardHeightDp * 0.7f).dp
                                )
                        )
                    }

                    // 顶部溶解蒙层 (仅在滚动后浮现)
                    if (scrollOffset > 10f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF0C0F17).copy(alpha = cardOpacity),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // 底部溶解蒙层
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF0C0F17).copy(alpha = cardOpacity)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3.3 提词板专属快速步进微调 HUD (Signature Quick Stepper Bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33000000))
                        .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [A-] 字号步进 [A+]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (fontSizeSp > 14) fontSizeSp -= 2 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(text = "A-", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${fontSizeSp}",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        IconButton(
                            onClick = { if (fontSizeSp < 36) fontSizeSp += 2 },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(text = "A+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // [-] 语速步进 [+]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val nextSpeed = (scrollSpeed - 0.1f).coerceAtLeast(0.5f)
                                viewModel.scrollEngine.setSpeed(nextSpeed)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "减速", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = String.format("%.1fx", scrollSpeed),
                            color = AccentCoral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        IconButton(
                            onClick = {
                                val nextSpeed = (scrollSpeed + 0.1f).coerceAtMost(3.0f)
                                viewModel.scrollEngine.setSpeed(nextSpeed)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "加速", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }

                    // 播放 / 暂停切换
                    IconButton(
                        onClick = { viewModel.scrollEngine.toggle() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "滚动控制",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 回滚重置到第一行
                    IconButton(
                        onClick = { viewModel.scrollEngine.reset() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "重置滚位",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 4. 开拍 3-2-1 动画倒计时遮罩
        // ---------------------------------------------------------
        AnimatedVisibility(
            visible = activeCountdown > 0,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xD9000000))
                    .border(3.dp, AccentCoral, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$activeCountdown",
                    color = AccentCoral,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // ---------------------------------------------------------
        // 5. 底部摄影录制中枢控制栏 (Master Controls Deck)
        // ---------------------------------------------------------
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xE60F121C))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(36.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧工具：水平镜像 🪞 & 倒计时设置 ⏱
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 水平镜像开关
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isMirrored = !isMirrored }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "水平镜像",
                            tint = if (isMirrored) AccentCoral else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isMirrored) "镜像开" else "镜像",
                            color = if (isMirrored) AccentCoral else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 倒计时预备时长切换: 3s -> 5s -> 0s(关)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                countdownDuration = when (countdownDuration) {
                                    3 -> 5
                                    5 -> 0
                                    else -> 3
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "倒计时设置",
                            tint = if (countdownDuration > 0) AccentAmber else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (countdownDuration > 0) "${countdownDuration}秒" else "倒计关",
                            color = if (countdownDuration > 0) AccentAmber else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 中间核心：录像快门大主键 (Large Circular Shutter)
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(3.dp, if (isRecording) RecordRed else Color.White, CircleShape)
                        .clickable {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                if (countdownDuration > 0) {
                                    activeCountdown = countdownDuration
                                } else {
                                    viewModel.startRecording(hasAudioPermission)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // 录制中：呼吸闪烁方块 (Stop Shutter)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .scale(pulseScale)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RecordRed)
                        )
                    } else {
                        // 待录制：饱满鲜红圆形 (Record Shutter)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(RecordRed)
                        )
                    }
                }

                // 右侧工具：宽度调节 📐 与 详细面板 ⚙️
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 卡片宽度切换: 窄 (75%) -> 标准 (88%) -> 宽 (96%)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                cardWidthPercent = when {
                                    cardWidthPercent < 0.80f -> 0.88f
                                    cardWidthPercent < 0.92f -> 0.96f
                                    else -> 0.75f
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "卡片宽度",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        val widthLabel = when {
                            cardWidthPercent < 0.80f -> "窄版"
                            cardWidthPercent < 0.92f -> "标准"
                            else -> "宽版"
                        }
                        Text(
                            text = widthLabel,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 调参面板
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showQuickSettingsSheet = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "详细设置",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "调参",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // 6. 快速调参底抽屉 (Quick Settings Sheet)
        // ---------------------------------------------------------
        if (showQuickSettingsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showQuickSettingsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF131622),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提词板高级参数配置",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showQuickSettingsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. 卡片背景透明度滑块 (0% ~ 100%)
                    Text(
                        text = "背景不透明度: ${(cardOpacity * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = cardOpacity,
                        onValueChange = { cardOpacity = it },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentAmber,
                            activeTrackColor = AccentAmber,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. 语速精细滑块 (0.5x ~ 3.0x)
                    Text(
                        text = "滚动语速: ${String.format("%.1fx", scrollSpeed)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { viewModel.scrollEngine.setSpeed(it) },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCoral,
                            activeTrackColor = AccentCoral,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. 字体大小精细滑块 (14sp ~ 34sp)
                    Text(
                        text = "字体大小: ${fontSizeSp}sp",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = fontSizeSp.toFloat(),
                        onValueChange = { fontSizeSp = it.roundToInt() },
                        valueRange = 14f..34f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF60A5FA),
                            activeTrackColor = Color(0xFF60A5FA),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}
