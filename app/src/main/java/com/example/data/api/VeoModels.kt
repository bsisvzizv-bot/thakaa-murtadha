package com.example.data.api

import android.net.Uri

data class GeneratedVideo(
    val id: String = System.currentTimeMillis().toString(),
    val prompt: String,
    val videoUri: String,
    val thumbnailUri: Uri? = null,
    val sourceImageUri: Uri? = null,
    val aspectRatio: String = "16:9",
    val model: String = "veo-3.1-fast-generate-preview",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromImage: Boolean = false
)

sealed class VideoGenerationState {
    object Idle : VideoGenerationState()
    data class Submitting(val message: String = "جاري إرسال الطلب إلى نموذج Veo...") : VideoGenerationState()
    data class Polling(val operationName: String, val secondsElapsed: Int, val message: String = "جاري تحريك وإنشاء إطارات الفيديو بالذكاء الاصطناعي...") : VideoGenerationState()
    data class Success(val video: GeneratedVideo) : VideoGenerationState()
    data class Error(val message: String) : VideoGenerationState()
}

enum class VideoAspectRatio(val label: String, val ratio: String, val descriptionArabic: String) {
    LANDSCAPE("16:9", "16:9", "أفقي (عريض)"),
    PORTRAIT("9:16", "9:16", "عمودي (ريلز / تيك توك)"),
    SQUARE("1:1", "1:1", "مربع (انستغرام)")
}
