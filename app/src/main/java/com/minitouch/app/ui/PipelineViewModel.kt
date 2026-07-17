package com.minitouch.app.ui

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import com.minitouch.app.pipeline.PipelineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PipelineViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = PipelineManager(application.applicationContext)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastSavedMessage = MutableStateFlow<String?>(null)
    val lastSavedMessage: StateFlow<String?> = _lastSavedMessage

    fun startPipeline(lifecycleOwner: LifecycleOwner, surfaceProvider: () -> Surface) {
        pipeline.start(lifecycleOwner, surfaceProvider)
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            pipeline.stopRecording { _lastSavedMessage.value = "Vídeo salvo em Movies/MiniTouch" }
            _isRecording.value = false
        } else {
            pipeline.startRecording()
            _isRecording.value = true
        }
    }

    fun clearMessage() {
        _lastSavedMessage.value = null
    }

    override fun onCleared() {
        pipeline.stop()
        super.onCleared()
    }
}
