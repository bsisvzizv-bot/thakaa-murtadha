package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AiStyle
import com.example.data.api.GeminiRepository
import com.example.data.local.AppDatabase
import com.example.data.local.QaItemEntity
import com.example.util.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatItem(
    val id: Long = System.currentTimeMillis(),
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis(),
    val style: AiStyle = AiStyle.BALANCED,
    val isFavorite: Boolean = false,
    val isError: Boolean = false,
    val isGenerating: Boolean = false
)

class QaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.qaDao()
    private val repository = GeminiRepository(application)
    val ttsManager = TtsManager(application)

    private val prefs = application.getSharedPreferences("ai_qa_prefs", Context.MODE_PRIVATE)

    // Current conversation in the active session
    private val _messages = MutableStateFlow<List<ChatItem>>(emptyList())
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentStyle = MutableStateFlow(AiStyle.BALANCED)
    val currentStyle: StateFlow<AiStyle> = _currentStyle.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // History and Favorites from Room DB
    val historyItems: StateFlow<List<QaItemEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteItems: StateFlow<List<QaItemEntity>> = dao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        val savedStyleId = prefs.getString("selected_style", AiStyle.BALANCED.id) ?: AiStyle.BALANCED.id
        _currentStyle.value = AiStyle.fromId(savedStyleId)
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setStyle(style: AiStyle) {
        _currentStyle.value = style
        prefs.edit().putString("selected_style", style.id).apply()
    }

    fun saveCustomApiKey(key: String) {
        _customApiKey.value = key
        prefs.edit().putString("custom_api_key", key).apply()
    }

    fun askQuestion(questionText: String? = null) {
        val query = (questionText ?: _inputText.value).trim()
        if (query.isBlank() || _isLoading.value) return

        _inputText.value = ""
        val tempId = System.currentTimeMillis()
        val currentStyle = _currentStyle.value

        // Add user question with generating indicator
        val newChatList = _messages.value.toMutableList().apply {
            add(
                ChatItem(
                    id = tempId,
                    question = query,
                    answer = "",
                    timestamp = tempId,
                    style = currentStyle,
                    isGenerating = true
                )
            )
        }
        _messages.value = newChatList
        _isLoading.value = true

        viewModelScope.launch {
            // Prepare history pairs for context
            val historyPairs = _messages.value
                .filter { !it.isGenerating && !it.isError && it.answer.isNotBlank() }
                .takeLast(3)
                .map { it.question to it.answer }

            val result = repository.askGemini(
                question = query,
                style = currentStyle,
                history = historyPairs,
                customApiKey = _customApiKey.value.ifBlank { null }
            )

            _isLoading.value = false

            result.onSuccess { answer ->
                // Update active chat message
                _messages.value = _messages.value.map { item ->
                    if (item.id == tempId) {
                        item.copy(answer = answer, isGenerating = false, isError = false)
                    } else item
                }

                // Save to Room DB
                val entity = QaItemEntity(
                    question = query,
                    answer = answer,
                    timestamp = tempId,
                    mode = currentStyle.id,
                    isFavorite = false
                )
                dao.insert(entity)
            }.onFailure { error ->
                val errorMsg = error.message ?: "حدث خطأ غير متوقع أثناء معالجة السؤال."
                _messages.value = _messages.value.map { item ->
                    if (item.id == tempId) {
                        item.copy(
                            answer = "⚠️ $errorMsg",
                            isGenerating = false,
                            isError = true
                        )
                    } else item
                }
            }
        }
    }

    fun retryQuestion(item: ChatItem) {
        // Remove the failed message and ask again
        _messages.value = _messages.value.filter { it.id != item.id }
        askQuestion(item.question)
    }

    fun toggleFavorite(item: ChatItem) {
        val updatedFavorite = !item.isFavorite
        _messages.value = _messages.value.map {
            if (it.id == item.id) it.copy(isFavorite = updatedFavorite) else it
        }

        viewModelScope.launch {
            // Check if exists in db by question or timestamp
            val entity = QaItemEntity(
                id = item.id,
                question = item.question,
                answer = item.answer,
                timestamp = item.timestamp,
                mode = item.style.id,
                isFavorite = updatedFavorite
            )
            dao.insert(entity)
        }
    }

    fun toggleDbFavorite(entity: QaItemEntity) {
        viewModelScope.launch {
            val updated = entity.copy(isFavorite = !entity.isFavorite)
            dao.update(updated)
            // Also sync active chat messages if present
            _messages.value = _messages.value.map {
                if (it.question == entity.question) it.copy(isFavorite = updated.isFavorite) else it
            }
        }
    }

    fun deleteHistoryItem(entity: QaItemEntity) {
        viewModelScope.launch {
            dao.delete(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }

    fun loadFromHistory(entity: QaItemEntity) {
        val chatItem = ChatItem(
            id = entity.id,
            question = entity.question,
            answer = entity.answer,
            timestamp = entity.timestamp,
            style = AiStyle.fromId(entity.mode),
            isFavorite = entity.isFavorite,
            isGenerating = false,
            isError = false
        )
        // Add to active chat if not already present
        if (_messages.value.none { it.question == entity.question && it.answer == entity.answer }) {
            _messages.value = _messages.value + chatItem
        }
    }

    fun clearActiveChat() {
        ttsManager.stop()
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
