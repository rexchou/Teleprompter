package com.promptflow.app.feature.audioprompter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.promptflow.app.core.model.RecordingState
import com.promptflow.app.core.model.VoiceFilter
import com.promptflow.app.data.audio.AudioRecordingManager
import com.promptflow.app.domain.engine.PrompterScrollEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPrompterViewModel(application: Application) : AndroidViewModel(application) {

    val audioManager = AudioRecordingManager(application)
    val scrollEngine = PrompterScrollEngine(viewModelScope)

    val recordingState: StateFlow<RecordingState> = audioManager.recordingState
    val currentAmplitude: StateFlow<Int> = audioManager.currentAmplitude

    private val _currentVoiceFilter = MutableStateFlow(VoiceFilter.default)
    val currentVoiceFilter: StateFlow<VoiceFilter> = _currentVoiceFilter.asStateFlow()

    fun setVoiceFilter(filter: VoiceFilter) {
        _currentVoiceFilter.value = filter
    }

    fun startRecording() {
        audioManager.startRecording(_currentVoiceFilter.value)
        scrollEngine.start()
    }

    fun pauseRecording() {
        audioManager.pauseRecording()
        scrollEngine.pause()
    }

    fun resumeRecording() {
        audioManager.resumeRecording()
        scrollEngine.start()
    }

    fun stopRecording() {
        audioManager.stopRecording()
        scrollEngine.pause()
    }

    fun resetToIdle() {
        audioManager.resetToIdle()
    }

    override fun onCleared() {
        super.onCleared()
        scrollEngine.pause()
        audioManager.release()
    }
}
