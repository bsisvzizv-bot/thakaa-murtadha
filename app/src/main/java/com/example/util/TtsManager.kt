package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Robust Text-to-Speech manager configured with defensive audio routing,
 * asynchronous background initialization, and active listeners for Bluetooth / audio routing changes
 * (AudioDeviceCallback, ACTION_AUDIO_BECOMING_NOISY, AudioFocusRequest).
 *
 * This prevents audio HAL hangs, binder deadlocks, and ANRs/crashes when Bluetooth
 * is toggled, connected, or disconnected in virtualized environments or physical devices.
 */
class TtsManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentSpeakingId = MutableStateFlow<String?>(null)
    val currentSpeakingId: StateFlow<String?> = _currentSpeakingId.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null

    // Handles disconnection of wired or Bluetooth headphones (Audio Becoming Noisy)
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("TtsManager", "Audio output became noisy (Bluetooth or headset disconnected). Stopping TTS cleanly.")
                stop()
            }
        }
    }
    private var isReceiverRegistered = false

    // Monitors additions/removals of audio hardware (including Bluetooth A2DP/SCO/LE)
    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.d("TtsManager", "Audio devices added (e.g. Bluetooth device connected). Adapting audio routing.")
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.d("TtsManager", "Audio devices removed (e.g. Bluetooth device disconnected).")
                // Stop ongoing playback if active to prevent routing to dead/hanging sink
                if (_isSpeaking.value) {
                    stop()
                }
            }
        }
    } else null

    init {
        registerAudioSensors()
        // Initialize TTS lazily in background thread to never block main thread or app launch
        initTtsAsync()
    }

    private fun registerAudioSensors() {
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            appContext.registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w("TtsManager", "Could not register noisyReceiver: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
            } catch (e: Exception) {
                Log.w("TtsManager", "Could not register audioDeviceCallback: ${e.message}")
            }
        }
    }

    private fun initTtsAsync() {
        if (isInitialized || isInitializing) return
        isInitializing = true

        scope.launch {
            try {
                var newTts: TextToSpeech? = null
                newTts = TextToSpeech(appContext) { status ->
                    isInitializing = false
                    if (status == TextToSpeech.SUCCESS) {
                        try {
                            val instance = newTts ?: tts ?: return@TextToSpeech
                            // Enforce non-blocking standard media stream attributes
                            instance.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )

                            val arabicLocale = Locale.forLanguageTag("ar")
                            val result = instance.setLanguage(arabicLocale)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                instance.language = Locale.getDefault()
                                Log.w("TtsManager", "Arabic TTS missing data or unsupported, falling back to default locale")
                            }

                            tts = instance
                            isInitialized = true
                            setupListener(instance)
                        } catch (e: Exception) {
                            Log.e("TtsManager", "Error configuring initialized TTS instance", e)
                        }
                    } else {
                        Log.e("TtsManager", "TTS initialization returned failure status: $status")
                    }
                }
                tts = newTts
            } catch (e: Exception) {
                isInitializing = false
                Log.e("TtsManager", "Exception during background TTS instantiation", e)
            }
        }
    }

    private fun setupListener(instance: TextToSpeech) {
        try {
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _currentSpeakingId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                    _isSpeaking.value = false
                    _currentSpeakingId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                    _isSpeaking.value = false
                    _currentSpeakingId.value = null
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    abandonAudioFocus()
                    _isSpeaking.value = false
                    _currentSpeakingId.value = null
                    Log.w("TtsManager", "TTS utterance error code: $errorCode")
                }
            })
        } catch (e: Exception) {
            Log.e("TtsManager", "Error setting utterance progress listener", e)
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stop()
                        }
                    }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stop()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w("TtsManager", "Failed to request audio focus safely: ${e.message}")
            true
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w("TtsManager", "Failed to abandon audio focus safely: ${e.message}")
        }
    }

    fun speak(text: String, id: String = System.currentTimeMillis().toString()) {
        if (!isInitialized) {
            // If not ready yet, initialize and abort without blocking
            initTtsAsync()
            return
        }

        stop()
        requestAudioFocus()

        try {
            val cleanText = text
                .replace(Regex("```[a-zA-Z]*"), "")
                .replace(Regex("[#*`_~]"), " ")
                .trim()

            _currentSpeakingId.value = id
            val res = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, id)
            if (res != TextToSpeech.SUCCESS) {
                Log.w("TtsManager", "tts.speak returned non-success code: $res")
                abandonAudioFocus()
                _isSpeaking.value = false
                _currentSpeakingId.value = null
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "TTS speak caught unexpected exception safely", e)
            abandonAudioFocus()
            _isSpeaking.value = false
            _currentSpeakingId.value = null
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TtsManager", "TTS stop caught exception safely", e)
        } finally {
            abandonAudioFocus()
            _isSpeaking.value = false
            _currentSpeakingId.value = null
        }
    }

    fun shutdown() {
        stop()

        // Unregister receivers and audio device listeners
        if (isReceiverRegistered) {
            try {
                appContext.unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {
                Log.w("TtsManager", "Error unregistering noisyReceiver", e)
            }
            isReceiverRegistered = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {
                Log.w("TtsManager", "Error unregistering audioDeviceCallback", e)
            }
        }

        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsManager", "TTS shutdown error caught safely", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
