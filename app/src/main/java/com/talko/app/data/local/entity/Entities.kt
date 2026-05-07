package com.talko.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat", indices = [Index("lastMessageTime")])
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessagePreview: String,
    val unreadCount: Int,
    val lastMessageTime: Long,
)

@Entity(tableName = "message", indices = [Index("chatId"), Index("timestamp")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val timestamp: Long,
    val isSynced: Boolean,
)

@Entity(tableName = "call_log", indices = [Index("timestamp")])
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerName: String,
    val callType: String,       // "AUDIO" | "VIDEO"
    val direction: String,      // "OUTGOING" | "INCOMING"
    val status: String,         // "COMPLETED" | "MISSED" | "DECLINED"
    val durationSec: Long,
    val timestamp: Long,
)
