package com.promptflow.app.feature.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.promptflow.app.MainActivity
import com.promptflow.app.PromptFlowApp
import com.promptflow.app.R
import com.promptflow.app.domain.engine.PrompterScrollEngine
import com.promptflow.app.ui.theme.AccentCoral
import com.promptflow.app.ui.theme.DarkCard
import com.promptflow.app.ui.theme.PromptFlowTheme

class FloatingPrompterService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val scrollEngine = PrompterScrollEngine()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scriptContent = intent?.getStringExtra(EXTRA_SCRIPT_CONTENT)
            ?: "提示：请在主界面选择台本后开启悬浮窗。"
        val scriptTitle = intent?.getStringExtra(EXTRA_SCRIPT_TITLE) ?: "台本提词"

        showFloatingWindow(scriptTitle, scriptContent)
        return START_NOT_STICKY
    }

    private fun showFloatingWindow(title: String, content: String) {
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            340.dpToPx(this),
            260.dpToPx(this),
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 180
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingPrompterService)
            setViewTreeViewModelStoreOwner(this@FloatingPrompterService)
            setViewTreeSavedStateRegistryOwner(this@FloatingPrompterService)

            setContent {
                PromptFlowTheme {
                    FloatingWindowCard(
                        title = title,
                        content = content,
                        scrollEngine = scrollEngine,
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        // Enable dragging
        composeView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Only drag if moved more than 10 pixels
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            windowManager.updateViewLayout(composeView, params)
                            return true
                        }
                    }
                }
                return false
            }
        })

        floatingView = composeView
        windowManager.addView(composeView, params)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, PromptFlowApp.CHANNEL_FLOATING_SERVICE_ID)
            .setContentTitle("PromptFlow 悬浮提词中")
            .setContentText("悬浮窗正在其它应用上层显示")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrollEngine.pause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SCRIPT_CONTENT = "extra_script_content"
        const val EXTRA_SCRIPT_TITLE = "extra_script_title"

        fun start(context: Context, title: String, content: String) {
            val intent = Intent(context, FloatingPrompterService::class.java).apply {
                putExtra(EXTRA_SCRIPT_TITLE, title)
                putExtra(EXTRA_SCRIPT_CONTENT, content)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPrompterService::class.java))
        }
    }
}

@Composable
private fun FloatingWindowCard(
    title: String,
    content: String,
    scrollEngine: PrompterScrollEngine,
    onClose: () -> Unit
) {
    val isScrolling by scrollEngine.isScrolling.collectAsState()
    val scrollOffset by scrollEngine.scrollOffset.collectAsState()
    val scrollSpeed by scrollEngine.scrollSpeed.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollOffset) {
        scrollState.scrollTo(scrollOffset.toInt())
    }

    LaunchedEffect(scrollState.maxValue) {
        scrollEngine.maxScrollHeight = scrollState.maxValue.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6111318))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle and top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(AccentCoral, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.1f", scrollSpeed)}x",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                val next = if (scrollSpeed >= 2.5f) 0.8f else scrollSpeed + 0.4f
                                scrollEngine.setSpeed(next)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = content,
                    color = Color(0xFFEDEDED),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom quick controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isScrolling) "▶ 正在滚动..." else "⏸ 已暂停 (点击开始)",
                    color = if (isScrolling) Color(0xFF10B981) else Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCoral)
                        .clickable { scrollEngine.toggle() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isScrolling) "暂停" else "开始",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
