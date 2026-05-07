package com.talko.app.domain.repository

import com.talko.app.domain.model.CallType
import com.talko.app.domain.model.Chat
import com.talko.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun currentUserId(): String?
    fun currentUserEmail(): String?
    suspend fun isProfileCompleted(): Boolean
    /** Sign in to an existing account. Throws if credentials are wrong or account doesn't exist. */
    suspend fun signIn(email: String, password: String): String
    /** Create a new account. Throws if email is already registered. */
    suspend fun register(email: String, password: String): String
    suspend fun sendPasswordReset(email: String)
    suspend fun saveProfile(fullName: String, bio: String)
    fun signOut()
    fun setOnlineStatus(isOnline: Boolean)
}

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(chatId: String, text: String)
    suspend fun sendVoiceMessage(chatId: String, audioUrl: String)
    suspend fun sendImageMessage(chatId: String, imageUrl: String)
    suspend fun createChat(chatId: String, title: String)
    suspend fun setTyping(chatId: String, isTyping: Boolean)
    fun observeTyping(chatId: String): Flow<Boolean>
    fun observePeerOnlineStatus(peerId: String): Flow<Boolean>
}

interface CallRepository {
    suspend fun startCall(peerId: String, callType: CallType)
    suspend fun endCall()
    // Stubs kept for interface compatibility — not used with Agora
    fun getLocalVideoTrack(): Any?
    fun getRemoteVideoTrack(): Any?
    fun getLocalAudioTrack(): Any?
}
