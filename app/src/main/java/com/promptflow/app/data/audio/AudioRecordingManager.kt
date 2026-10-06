package com.promptflow.app.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import com.promptflow.app.core.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordingManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMillis: Long = 0L
    private var pausedTimeOffset: Long = 0L
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0)
    val currentAmplitude: StateFlow<Int> = _currentAmplitude.asStateFlow()

    fun startRecording(): Result<File> {
        try {
            stopPolling()
            mediaRecorder?.release()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val audioFile = File(storageDir, "PromptFlow_Audio_$timestamp.m4a")
            currentOutputFile = audioFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(256000) // 256kbps high quality
                setAudioSamplingRate(48000)      // 48kHz studio sample rate
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            startTimeMillis = System.currentTimeMillis()
            pausedTimeOffset = 0L
            _recordingState.value = RecordingState.Recording(0L, 0)
            startPolling()

            return Result.success(audioFile)
        } catch (e: IOException) {
            _recordingState.value = RecordingState.Error(e.message ?: "Failed to initialize audio recorder")
            return Result.failure(e)
        } catch (e: IllegalStateException) {
            _recordingState.value = RecordingState.Error(e.message ?: "Illegal audio recording state")
            return Result.failure(e)
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val currentState = _recordingState.value
            if (currentState is RecordingState.Recording) {
                try {
                    mediaRecorder?.pause()
                    pausedTimeOffset += System.currentTimeMillis() - startTimeMillis
                    stopPolling()
                    _recordingState.value = RecordingState.Paused(pausedTimeOffset)
                } catch (e: Exception) {
                    _recordingState.value = RecordingState.Error("Failed to pause: ${e.message}")
                }
            }
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val currentState = _recordingState.value
            if (currentState is RecordingState.Paused) {
                try {
                    mediaRecorder?.resume()
                    startTimeMillis = System.currentTimeMillis()
                    _recordingState.value = RecordingState.Recording(pausedTimeOffset, 0)
                    startPolling()
                } catch (e: Exception) {
                    _recordingState.value = RecordingState.Error("Failed to resume: ${e.message}")
                }
            }
        }
    }

    fun stopRecording(): File? {
        stopPolling()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Defensively handle edge cases where stop is called immediately
        } finally {
            mediaRecorder = null
        }

        val totalDuration = if (startTimeMillis > 0) {
            pausedTimeOffset + (System.currentTimeMillis() - startTimeMillis)
        } else pausedTimeOffset

        val savedFile = currentOutputFile
        if (savedFile != null && savedFile.exists() && savedFile.length() > 0) {
            _recordingState.value = RecordingState.Finished(totalDuration, savedFile.absolutePath)
        } else {
            _recordingState.value = RecordingState.Idle
        }
        _currentAmplitude.value = 0
        return savedFile
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val current = System.currentTimeMillis()
                val elapsed = pausedTimeOffset + (current - startTimeMillis)

                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                _currentAmplitude.value = amp
                _recordingState.value = RecordingState.Recording(elapsed, amp)
                delay(80) // ~12 fps update rate for waveform
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun release() {
        stopPolling()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaRecorder = null
    }
}
