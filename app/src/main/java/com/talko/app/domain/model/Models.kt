package com.talko.app.domain.model

enum class MessageType { TEXT, IMAGE, VOICE }
enum class CallType { AUDIO, VIDEO }
enum class CallDirection { OUTGOING, INCOMING }
enum class CallStatus { COMPLETED, MISSED, DECLINED }

data class User(
    val id: String,
    val phone: String,
    val fullName: String,
    val bio: String,
    val isOnline: Boolean,
)

data class Chat(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessagePreview: String,
    val unreadCount: Int,
    val lastMessageTime: Long,
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val messageType: MessageType,
    val timestamp: Long,
    val isSynced: Boolean,
    val isTypingEvent: Boolean = false,
)

data class CallLog(
    val id: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    val direction: CallDirection,
    val status: CallStatus,
    val durationSec: Long,
    val timestamp: Long,
)
