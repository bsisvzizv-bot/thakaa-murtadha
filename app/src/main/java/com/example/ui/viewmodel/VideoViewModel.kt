package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeneratedVideo
import com.example.data.api.VeoRepository
import com.example.data.api.VideoAspectRatio
import com.example.data.api.VideoGenerationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VideoCreationMode(val titleArabic: String, val subtitleArabic: String) {
    TEXT_TO_VIDEO("تحويل الكلام إلى فيديو", "اكتب أي نص أو فكرة وتخيلها مشهداً سينمائياً"),
    IMAGE_TO_VIDEO("تحويل الصورة إلى فيديو", "اختر صورة لتحريكها وإضفاء حياة سينمائية عليها")
}

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VeoRepository(application)

    private val _creationMode = MutableStateFlow(VideoCreationMode.IMAGE_TO_VIDEO)
    val creationMode: StateFlow<VideoCreationMode> = _creationMode.asStateFlow()

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _aspectRatio = MutableStateFlow(VideoAspectRatio.LANDSCAPE)
    val aspectRatio: StateFlow<VideoAspectRatio> = _aspectRatio.asStateFlow()

    private val _isHighQualityModel = MutableStateFlow(false)
    val isHighQualityModel: StateFlow<Boolean> = _isHighQualityModel.asStateFlow()

    private val _generationState = MutableStateFlow<VideoGenerationState>(VideoGenerationState.Idle)
    val generationState: StateFlow<VideoGenerationState> = _generationState.asStateFlow()

    private val _savedVideos = MutableStateFlow<List<GeneratedVideo>>(emptyList())
    val savedVideos: StateFlow<List<GeneratedVideo>> = _savedVideos.asStateFlow()

    fun setCreationMode(mode: VideoCreationMode) {
        _creationMode.value = mode
    }

    fun setPrompt(text: String) {
        _prompt.value = text
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun setAspectRatio(ratio: VideoAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setIsHighQualityModel(hq: Boolean) {
        _isHighQualityModel.value = hq
    }

    fun generateVideo(customApiKey: String? = null) {
        val currentPrompt = _prompt.value.trim()
        val currentMode = _creationMode.value
        val imageUri = if (currentMode == VideoCreationMode.IMAGE_TO_VIDEO) _selectedImageUri.value else null

        if (currentPrompt.isBlank() && imageUri == null) {
            _generationState.value = VideoGenerationState.Error("يرجى إدخال وصف للفيديو أو اختيار صورة لتحريكها.")
            return
        }

        // Default prompt if image selected but user didn't write prompt
        val effectivePrompt = if (currentPrompt.isBlank()) {
            "Animate this image with smooth cinematic movement, depth of field and beautiful lighting"
        } else currentPrompt

        val modelName = if (_isHighQualityModel.value) "veo-3.1-generate-preview" else "veo-3.1-fast-generate-preview"

        _generationState.value = VideoGenerationState.Submitting()

        viewModelScope.launch {
            val result = repository.generateVideo(
                prompt = effectivePrompt,
                imageUri = imageUri,
                aspectRatio = _aspectRatio.value,
                modelName = modelName,
                customApiKey = customApiKey
            ) { message, secondsElapsed ->
                _generationState.value = VideoGenerationState.Polling(
                    operationName = "",
                    secondsElapsed = secondsElapsed,
                    message = message
                )
            }

            result.onSuccess { video ->
                _generationState.value = VideoGenerationState.Success(video)
                _savedVideos.value = listOf(video) + _savedVideos.value
            }.onFailure { error ->
                _generationState.value = VideoGenerationState.Error(
                    error.message ?: "حدث خطأ أثناء توليد الفيديو."
                )
            }
        }
    }

    fun resetState() {
        _generationState.value = VideoGenerationState.Idle
    }

    fun deleteVideo(video: GeneratedVideo) {
        _savedVideos.value = _savedVideos.value.filter { it.id != video.id }
        if ((_generationState.value as? VideoGenerationState.Success)?.video?.id == video.id) {
            _generationState.value = VideoGenerationState.Idle
        }
    }
}
