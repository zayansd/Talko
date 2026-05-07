package com.talko.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.talko.app.data.local.entity.CallLogEntity
import com.talko.app.data.local.entity.ChatEntity
import com.talko.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TalkoDao {
    // ── Chats ─────────────────────────────────────────────────────────────────
    @Query("SELECT * FROM chat ORDER BY lastMessageTime DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat WHERE title LIKE '%' || :query || '%' ORDER BY lastMessageTime DESC")
    fun searchChats(query: String): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("SELECT * FROM chat WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    // ── Messages ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM message WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE chatId = :chatId AND content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(chatId: String, query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("SELECT * FROM message WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE message SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE chat SET unreadCount = 0 WHERE id = :chatId")
    suspend fun clearUnread(chatId: String)

    // ── Call log ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM call_log ORDER BY timestamp DESC")
    fun observeCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity)

    @Query("DELETE FROM call_log WHERE id = :id")
    suspend fun deleteCallLog(id: String)
}
