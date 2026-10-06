package com.promptflow.app.feature.videoprompter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.Script
import com.promptflow.app.data.video.CameraManager
import com.promptflow.app.domain.engine.PrompterScrollEngine
import kotlinx.coroutines.flow.StateFlow

class VideoPrompterViewModel(application: Application) : AndroidViewModel(application) {

    val cameraManager = CameraManager(application)
    val scrollEngine = PrompterScrollEngine(viewModelScope)

    val recordingState: StateFlow<RecordingState> = cameraManager.recordingState
    val isFrontCamera: StateFlow<Boolean> = cameraManager.isFrontCamera

    fun startRecording(hasAudioPermission: Boolean) {
        cameraManager.startRecording(hasAudioPermission)
        scrollEngine.start()
    }

    fun stopRecording() {
        cameraManager.stopRecording()
        scrollEngine.pause()
    }

    override fun onCleared() {
        super.onCleared()
        scrollEngine.pause()
        cameraManager.release()
    }
}
