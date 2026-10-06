package com.promptflow.app.data.video

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.promptflow.app.core.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraBound: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner, previewView)
            onCameraBound()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "Camera binding failed")
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        _isFrontCamera.value = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun startRecording(hasAudioPermission: Boolean) {
        val capture = videoCapture ?: return
        if (activeRecording != null) return

        val name = "PromptFlow_Video_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PromptFlow")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val pendingRecording = capture.output
            .prepareRecording(context, mediaStoreOutputOptions)

        if (hasAudioPermission) {
            try {
                pendingRecording.withAudioEnabled()
            } catch (e: SecurityException) {
                // fall back to silent recording if audio permission missing
            }
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    _recordingState.value = RecordingState.Recording(0L)
                }
                is VideoRecordEvent.Status -> {
                    val durationMs = recordEvent.recordingStats.recordedDurationNanos / 1_000_000
                    _recordingState.value = RecordingState.Recording(durationMs)
                }
                is VideoRecordEvent.Pause -> {
                    val durationMs = recordEvent.recordingStats.recordedDurationNanos / 1_000_000
                    _recordingState.value = RecordingState.Paused(durationMs)
                }
                is VideoRecordEvent.Resume -> {
                    val durationMs = recordEvent.recordingStats.recordedDurationNanos / 1_000_000
                    _recordingState.value = RecordingState.Recording(durationMs)
                }
                is VideoRecordEvent.Finalize -> {
                    val durationMs = recordEvent.recordingStats.recordedDurationNanos / 1_000_000
                    if (!recordEvent.hasError()) {
                        val uriStr = recordEvent.outputResults.outputUri.toString()
                        _recordingState.value = RecordingState.Finished(durationMs, uriStr)
                    } else {
                        _recordingState.value = RecordingState.Error("Error code: ${recordEvent.error}")
                    }
                    activeRecording = null
                }
            }
        }
    }

    fun pauseRecording() {
        activeRecording?.pause()
    }

    fun resumeRecording() {
        activeRecording?.resume()
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
    }
}
