package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

enum class AiStyle(
    val id: String,
    val titleArabic: String,
    val descriptionArabic: String,
    val promptInstruction: String
) {
    BALANCED(
        id = "balanced",
        titleArabic = "متوازن وذكي",
        descriptionArabic = "إجابة شاملة وواضحة ومنسقة",
        promptInstruction = "أجب بشكل متوازن، مفيد، واضح ومنظم. استخدم نقاطاً وتنسيقاً أنيقاً عند الحاجة."
    ),
    CONCISE(
        id = "concise",
        titleArabic = "موجز وسريع",
        descriptionArabic = "إجابة مباشرة باختصار وافٍ",
        promptInstruction = "أجب باختصار شديد وبشكل مباشر دون إطالة غير لازمة، وركز على النقاط الجوهرية فقط."
    ),
    DETAILED(
        id = "detailed",
        titleArabic = "مفصل وشامل",
        descriptionArabic = "شرح عميق مع أمثلة وتوضيحات",
        promptInstruction = "قدّم شرحاً تفصيلياً مع التعمق في الأسباب، والأمثلة العملية، ووجهات النظر المختلفة إن وجدت."
    ),
    MEDICAL(
        id = "medical",
        titleArabic = "طبي وصحي 🩺",
        descriptionArabic = "استشارات طبية، أعراض، ووقاية",
        promptInstruction = "أنت استشاري طبي وصحي خبير. رتّب الإجابة الطبية بدقة: 1. نبذة علمية ومفاهيم أساسية، 2. الأسباب وعوامل الخطر، 3. الأعراض والعلامات، 4. طرق الوقاية والإرشادات الغذائية، 5. الفحوصات المقترحة، 6. إشارات الخطر التي تستوجب مراجعة الطبيب. اكتب بأسلوب علمي مطمئن ودقيق."
    ),
    SIMPLIFIED(
        id = "simplified",
        titleArabic = "مبسط للمبتدئين",
        descriptionArabic = "أسلوب سلس وأمثلة بسيطة",
        promptInstruction = "اشرح بأسلوب مبسط جداً يناسب المبتدئين والأطفال واستخدم تشبيهات وأمثلة سهلة الفهم."
    ),
    TECHNICAL(
        id = "technical",
        titleArabic = "تقني وبرمجي",
        descriptionArabic = "دقة برمجية وأكواد منظمة",
        promptInstruction = "ركز على الجانب البرمجي والتقني والمنطقي، وزوّد أمثلة بالأكواد البرمجية مع شروحات للخطوات وأفضل الممارسات."
    );

    companion object {
        fun fromId(id: String): AiStyle = entries.find { it.id == id } ?: BALANCED
    }
}

class GeminiRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val apiService: GeminiApiService = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApiService::class.java)

    suspend fun askGemini(
        question: String,
        style: AiStyle = AiStyle.BALANCED,
        history: List<Pair<String, String>> = emptyList(),
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException(
                    "مفتاح Gemini API غير مهيأ. يرجى إضافة GEMINI_API_KEY في لوحة Secrets في AI Studio أو إدخال مفتاحك في شاشة الإعدادات داخل التطبيق."
                )
            )
        }

        val systemPrompt = """
            أنت مساعد ذكاء اصطناعي عربي ذكي، ودود، وخبير يسمى "ذكاء مرتضى".
            مهمتك هي الإجابة عن أسئلة المستخدم بدقة، وموضوعية، وأسلوب عربي فصيح وراقي.
            قواعد الإجابة:
            1. نمط الإجابة المطلوب: ${style.promptInstruction}
            2. استخدم تنسيق Markdown (عناوين، نقاط، خط عريض، كتل أكواد إن وجدت) لتسهيل القراءة.
            3. إذا كان السؤال عن مسألة تحتمل آراء، اذكر باختصار التنوع مع الحياد.
            4. أجب دائماً باللغة العربية بطلاقة وسلاسة، إلا إذا طلب المستخدم لغة أخرى أو كوداً برمجياً.
            5. تجنب الحشو غير المفيد وابدأ الإجابة بالجواب المباشر والواضح.
        """.trimIndent()

        val contents = mutableListOf<GeminiContent>()

        // Add recent conversation history context if available (last 3 turns to prevent large payload)
        val recentHistory = history.takeLast(3)
        for ((histQ, histA) in recentHistory) {
            contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = histQ))))
            contents.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = histA))))
        }

        // Current question
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = question))))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = when (style) {
                    AiStyle.CONCISE -> 0.4f
                    AiStyle.DETAILED -> 0.7f
                    AiStyle.MEDICAL -> 0.3f
                    AiStyle.SIMPLIFIED -> 0.6f
                    AiStyle.TECHNICAL -> 0.3f
                    AiStyle.BALANCED -> 0.7f
                }
            )
        )

        // Try primary model gemini-3.8-flash, then fallback to gemini-3.5-flash or gemini-flash-latest
        val modelsToTry = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-latest")
        var lastErrorMessage = "تعذر الحصول على إجابة من خادم الذكاء الاصطناعي."

        for (model in modelsToTry) {
            for (attempt in 1..2) {
                try {
                    val response = apiService.generateContent(model = model, apiKey = apiKey, request = request)
                    val answerText = response.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text

                    if (!answerText.isNullOrBlank()) {
                        return@withContext Result.success(answerText.trim())
                    } else if (response.error != null) {
                        lastErrorMessage = response.error.message ?: "خطأ في معالجة الطلب من الخادم."
                    }
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    when (code) {
                        400 -> {
                            lastErrorMessage = "طلب غير صالح: $errorBody"
                            break // Don't retry same model on 400 bad request
                        }
                        403 -> {
                            lastErrorMessage = "تم رفض الوصول، يرجى التحقق من صلاحيات مفتاح Gemini API."
                            break
                        }
                        429, 500, 503 -> {
                            lastErrorMessage = "الخادم مضغوط مؤقتاً (${code}). جاري التبديل للمحرك البديل..."
                            delay(1000L * attempt)
                            continue
                        }
                        else -> {
                            lastErrorMessage = "خطأ في الاتصال بالخادم ($code): ${e.message()}"
                        }
                    }
                } catch (e: Exception) {
                    lastErrorMessage = "تعذر الاتصال بخدمة الذكاء الاصطناعي: ${e.localizedMessage ?: "تأكد من اتصالك بالإنترنت"}"
                    delay(500L)
                }
            }
        }

        Result.failure(Exception(lastErrorMessage))
    }
}
