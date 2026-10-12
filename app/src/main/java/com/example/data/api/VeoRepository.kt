package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VeoRepository(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateVideo(
        prompt: String,
        imageUri: Uri? = null,
        aspectRatio: VideoAspectRatio = VideoAspectRatio.LANDSCAPE,
        modelName: String = "veo-3.1-fast-generate-preview",
        customApiKey: String? = null,
        onProgressUpdate: (message: String, secondsElapsed: Int) -> Unit
    ): Result<GeneratedVideo> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("يرجى إعداد مفتاح GEMINI_API_KEY في لوحة Secrets أو إدخاله في الإعدادات لتوليد الفيديو.")
            )
        }

        val requestJson = JSONObject()
        requestJson.put("prompt", prompt)

        // Config object
        val configJson = JSONObject().apply {
            put("aspectRatio", aspectRatio.ratio)
            put("numberOfVideos", 1)
        }
        requestJson.put("config", configJson)

        // If Image-to-Video is requested
        if (imageUri != null) {
            val base64Image = readImageAsBase64(imageUri)
            if (base64Image != null) {
                val imageObj = JSONObject().apply {
                    put("bytesBase64Encoded", base64Image)
                    put("mimeType", "image/jpeg")
                }
                requestJson.put("image", imageObj)
            }
        }

        onProgressUpdate("جاري إرسال طلب التوليد إلى نموذج Veo...", 0)

        // Call generateVideos endpoint
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateVideos?key=$apiKey"
        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val postRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(postRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, responseBody)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val responseObj = JSONObject(responseBody)

            // If it returns an operation name, we poll until done
            if (responseObj.has("name")) {
                val operationName = responseObj.getString("name")
                val pollResult = pollOperation(operationName, apiKey, prompt, imageUri, aspectRatio.ratio, modelName, onProgressUpdate)
                return@withContext pollResult
            } else if (responseObj.has("generatedVideos")) {
                // Synchronous response
                val videoUri = extractVideoUriFromResponse(responseObj)
                if (videoUri != null) {
                    return@withContext Result.success(
                        GeneratedVideo(
                            prompt = prompt,
                            videoUri = videoUri,
                            sourceImageUri = imageUri,
                            aspectRatio = aspectRatio.ratio,
                            model = modelName,
                            isFromImage = imageUri != null
                        )
                    )
                }
            }

            Result.failure(Exception("استجابة غير متوقعة من خادم توليد الفيديو: $responseBody"))
        } catch (e: Exception) {
            Result.failure(Exception("خطأ في توليد الفيديو: ${e.localizedMessage ?: e.message}", e))
        }
    }

    private suspend fun pollOperation(
        operationName: String,
        apiKey: String,
        prompt: String,
        imageUri: Uri?,
        aspectRatio: String,
        modelName: String,
        onProgressUpdate: (message: String, secondsElapsed: Int) -> Unit
    ): Result<GeneratedVideo> {
        val opUrl = "https://generativelanguage.googleapis.com/v1beta/$operationName?key=$apiKey"
        var secondsElapsed = 0
        val maxWaitSeconds = 180 // 3 minutes timeout

        while (secondsElapsed < maxWaitSeconds) {
            delay(5000)
            secondsElapsed += 5

            val statusMsg = when {
                secondsElapsed < 15 -> "جاري تحليل المشهد وتجهيز المحرك الذكي... ($secondsElapsed ثانية)"
                secondsElapsed < 40 -> "جاري إنشاء وتوليد إطارات الحركة المتتابعة... ($secondsElapsed ثانية)"
                secondsElapsed < 80 -> "جاري ضبط الإضاءة والتنعيم السينمائي... ($secondsElapsed ثانية)"
                else -> "جاري إخراج وتشفير الفيديو النهائي بدقة عالية... ($secondsElapsed ثانية)"
            }
            onProgressUpdate(statusMsg, secondsElapsed)

            val getRequest = Request.Builder().url(opUrl).get().build()
            try {
                val response = okHttpClient.newCall(getRequest).execute()
                val bodyString = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    continue
                }

                val opObj = JSONObject(bodyString)
                if (opObj.optBoolean("done", false)) {
                    // Check for error
                    if (opObj.has("error")) {
                        val errorObj = opObj.getJSONObject("error")
                        val msg = errorObj.optString("message", "فشلت عملية توليد الفيديو")
                        return Result.failure(Exception(msg))
                    }

                    // Extract response
                    val resp = opObj.optJSONObject("response")
                    val videoUri = resp?.let { extractVideoUriFromResponse(it) }

                    if (videoUri != null) {
                        return Result.success(
                            GeneratedVideo(
                                prompt = prompt,
                                videoUri = videoUri,
                                sourceImageUri = imageUri,
                                aspectRatio = aspectRatio,
                                model = modelName,
                                isFromImage = imageUri != null
                            )
                        )
                    } else {
                        return Result.failure(Exception("اكتملت العملية ولكن لم يتم العثور على رابط الفيديو في النتيجة."))
                    }
                }
            } catch (e: Exception) {
                // Temporary network blip, continue polling
            }
        }

        return Result.failure(Exception("استغرقت عملية توليد الفيديو وقتاً أطول من المتوقع، يرجى المحاولة لاحقاً."))
    }

    private fun extractVideoUriFromResponse(responseObj: JSONObject): String? {
        val videos = responseObj.optJSONArray("generatedVideos") ?: return null
        if (videos.length() == 0) return null
        val first = videos.getJSONObject(0)
        val videoObj = first.optJSONObject("video") ?: return null

        // Check if direct uri is present
        val uriStr = videoObj.optString("uri", "")
        if (uriStr.isNotBlank()) return uriStr

        // Check if downloadUri is present
        val downloadUri = videoObj.optString("downloadUri", "")
        if (downloadUri.isNotBlank()) return downloadUri

        // Check if base64 bytes are returned directly
        val base64Bytes = videoObj.optString("bytesBase64Encoded", "")
        if (base64Bytes.isNotBlank()) {
            return saveBase64VideoLocally(base64Bytes)
        }

        return null
    }

    private fun saveBase64VideoLocally(base64String: String): String? {
        return try {
            val videoBytes = Base64.decode(base64String, Base64.DEFAULT)
            val outputFile = File(context.cacheDir, "veo_generated_${System.currentTimeMillis()}.mp4")
            FileOutputStream(outputFile).use { it.write(videoBytes) }
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun readImageAsBase64(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return null
                // Scale down if extremely large to prevent OOM
                val maxDim = 1280
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else bitmap

                val outputStream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val err = json.optJSONObject("error")
            val msg = err?.optString("message", "") ?: body
            when (code) {
                400 -> "طلب غير صالح: $msg"
                403 -> "نموذج توليد الفيديو Veo يتطلب تفعيل صلاحيات المعاينة في مشروعك: $msg"
                404 -> "النموذج غير متوفر أو الرابط غير صحيح ($code): $msg"
                429 -> "تم تجاوز حد الطلبات المسموح به حالياً، يرجى الانتظار قليلاً."
                500, 503 -> "خوادم توليد الفيديو مشغولة حالياً، يرجى المحاولة بعد قليل."
                else -> "خطأ ($code): $msg"
            }
        } catch (e: Exception) {
            "فشل الاتصال بخدمة الفيديو ($code): $body"
        }
    }
}
