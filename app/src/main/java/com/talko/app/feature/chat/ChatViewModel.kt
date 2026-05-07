package com.talko.app.feature.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.talko.app.domain.model.Chat
import com.talko.app.domain.model.Message
import com.talko.app.domain.repository.ChatRepository
import com.talko.app.domain.usecase.GenerateAiSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val searchQuery: String = "",
    val filteredChats: List<Chat> = emptyList(),
    val isSearchActive: Boolean = false,
)

data class ChatDetailUiState(
    val input: String = "",
    val messages: List<Message> = emptyList(),
    val isPeerTyping: Boolean = false,
    val isPeerOnline: Boolean = false,
    val aiSummary: String = "",
    val quickReplies: List<String> = emptyList(),
    val snackMessage: String? = null,
    val isOffline: Boolean = false,
    val isRecording: Boolean = false,
    val isUploadingMedia: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val generateAiSuggestionsUseCase: GenerateAiSuggestionsUseCase,
) : ViewModel() {

    private val _chatListState = MutableStateFlow(ChatListUiState())
    val chatListState: StateFlow<ChatListUiState> = _chatListState.asStateFlow()

    private val _chatDetailState = MutableStateFlow(ChatDetailUiState())
    val chatDetailState: StateFlow<ChatDetailUiState> = _chatDetailState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    init {
        viewModelScope.launch {
            chatRepository.observeChats().collect { chats ->
                _chatListState.update { state ->
                    val filtered = if (state.searchQuery.isBlank()) chats
                    else chats.filter { it.title.contains(state.searchQuery, ignoreCase = true) || it.lastMessagePreview.contains(state.searchQuery, ignoreCase = true) }
                    state.copy(chats = chats, filteredChats = filtered)
                }
            }
        }
    }

    // ── Chat list / search ────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _chatListState.update { state ->
            val filtered = if (query.isBlank()) state.chats
            else state.chats.filter { it.title.contains(query, ignoreCase = true) || it.lastMessagePreview.contains(query, ignoreCase = true) }
            state.copy(searchQuery = query, filteredChats = filtered, isSearchActive = query.isNotBlank())
        }
    }

    fun clearSearch() {
        _chatListState.update { it.copy(searchQuery = "", filteredChats = it.chats, isSearchActive = false) }
    }

    // ── Chat detail ───────────────────────────────────────────────────────────

    fun bindChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { msgs ->
                val ai = generateAiSuggestionsUseCase(msgs)
                _chatDetailState.update { it.copy(messages = msgs, aiSummary = ai.summary, quickReplies = ai.quickReplies) }
            }
        }
        viewModelScope.launch {
            chatRepository.observeTyping(chatId).collect { isTyping ->
                _chatDetailState.update { it.copy(isPeerTyping = isTyping) }
            }
        }
        // Observe real peer online status using the chat title as peer ID
        // (In a real app, you'd pass the actual peer UID)
        viewModelScope.launch {
            chatRepository.observePeerOnlineStatus(chatId).collect { isOnline ->
                _chatDetailState.update { it.copy(isPeerOnline = isOnline) }
            }
        }
    }

    fun onInputChange(chatId: String, value: String) {
        _chatDetailState.update { it.copy(input = value) }
        viewModelScope.launch { runCatching { chatRepository.setTyping(chatId, value.isNotBlank()) } }
    }

    fun sendText(chatId: String) {
        val text = _chatDetailState.value.input.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _chatDetailState.update { it.copy(input = "") }
            runCatching {
                chatRepository.sendMessage(chatId, text)
                chatRepository.setTyping(chatId, false)
            }.onFailure { e -> showOfflineSnack(e) }
        }
    }

    // ── Real image upload ─────────────────────────────────────────────────────

    fun sendImage(chatId: String, uri: Uri) = viewModelScope.launch {
        _chatDetailState.update { it.copy(isUploadingMedia = true) }
        runCatching {
            val storageRef = FirebaseStorage.getInstance().reference
                .child("chat_images/$chatId/${UUID.randomUUID()}.jpg")
            storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            chatRepository.sendImageMessage(chatId, downloadUrl)
        }.onSuccess {
            _chatDetailState.update { it.copy(isUploadingMedia = false) }
        }.onFailure { e ->
            _chatDetailState.update { it.copy(isUploadingMedia = false) }
            showOfflineSnack(e)
        }
    }

    // ── Real voice recording ──────────────────────────────────────────────────

    fun startRecording(context: Context, chatId: String) {
        val file = File(context.cacheDir, "voice_${UUID.randomUUID()}.m4a")
        recordingFile = file
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        _chatDetailState.update { it.copy(isRecording = true) }
    }

    fun stopRecording(chatId: String) {
        val file = recordingFile ?: return
        runCatching {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        }
        mediaRecorder = null
        _chatDetailState.update { it.copy(isRecording = false) }

        // Upload the recorded file
        viewModelScope.launch {
            _chatDetailState.update { it.copy(isUploadingMedia = true) }
            runCatching {
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("voice_messages/$chatId/${UUID.randomUUID()}.m4a")
                storageRef.putFile(Uri.fromFile(file)).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                chatRepository.sendVoiceMessage(chatId, downloadUrl)
            }.onSuccess {
                _chatDetailState.update { it.copy(isUploadingMedia = false) }
                file.delete()
            }.onFailure { e ->
                _chatDetailState.update { it.copy(isUploadingMedia = false) }
                showOfflineSnack(e)
            }
        }
    }

    fun onNewChat(chatId: String, title: String) {
        viewModelScope.launch {
            chatRepository.createChat(chatId, title)
        }
    }

    fun applyQuickReply(text: String) = _chatDetailState.update { it.copy(input = text) }
    fun clearSnack() = _chatDetailState.update { it.copy(snackMessage = null) }
    fun showSnack(msg: String) = _chatDetailState.update { it.copy(snackMessage = msg) }

    private fun showOfflineSnack(e: Throwable) {
        _chatDetailState.update {
            it.copy(
                isOffline = isOfflineError(e),
                snackMessage = if (isOfflineError(e)) "You're offline — message queued." else "Failed to send. Please try again.",
            )
        }
    }

    private fun isOfflineError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return "offline" in msg || "unavailable" in msg || "network" in msg || "client is offline" in msg
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
    }
}
