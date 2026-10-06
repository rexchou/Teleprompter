package com.promptflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.promptflow.app.core.model.Script
import com.promptflow.app.feature.audioprompter.AudioPrompterScreen
import com.promptflow.app.feature.scripthub.ScriptHubScreen
import com.promptflow.app.feature.videoprompter.VideoPrompterScreen
import com.promptflow.app.ui.theme.DarkSurface
import com.promptflow.app.ui.theme.PromptFlowTheme

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (!hasCameraPermission && !hasAudioPermission) {
            Toast.makeText(this, "需开启相机与麦克风权限以正常录像和录音", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            PromptFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkSurface
                ) {
                    PromptFlowNavigation(
                        hasCameraPermission = hasCameraPermission,
                        hasAudioPermission = hasAudioPermission,
                        onRequestPermissions = { checkPermissions() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasCameraPermission = cameraGranted
        hasAudioPermission = audioGranted

        if (!cameraGranted || !audioGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }
}

@Composable
fun PromptFlowNavigation(
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    var selectedScript by remember { mutableStateOf<Script?>(null) }

    NavHost(
        navController = navController,
        startDestination = "scripthub"
    ) {
        composable("scripthub") {
            ScriptHubScreen(
                onNavigateToVideo = { script ->
                    selectedScript = script
                    if (!hasCameraPermission) {
                        onRequestPermissions()
                    }
                    navController.navigate("video_prompter")
                },
                onNavigateToAudio = { script ->
                    selectedScript = script
                    if (!hasAudioPermission) {
                        onRequestPermissions()
                    }
                    navController.navigate("audio_prompter")
                }
            )
        }

        composable("video_prompter") {
            val script = selectedScript ?: Script(
                title = "默认台本",
                content = "请在台本中心创建或选择台本开始提词。"
            )
            VideoPrompterScreen(
                script = script,
                hasAudioPermission = hasAudioPermission
            )
        }

        composable("audio_prompter") {
            val script = selectedScript ?: Script(
                title = "默认台本",
                content = "请在台本中心创建或选择台本开始提词。"
            )
            AudioPrompterScreen(
                script = script
            )
        }
    }
}
