package com.talko.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.talko.app.data.local.dao.TalkoDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Background worker that syncs pending (offline-written) messages to Firestore.
 * Runs every 15 minutes when the device has network connectivity.
 * On failure it retries with exponential back-off (configured in TalkoApp).
 */
@HiltWorker
class PendingSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TalkoDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success() // not signed in — nothing to sync

        val pending = dao.getPendingMessages()
        if (pending.isEmpty()) return Result.success()

        android.util.Log.d("TalkoSync", "Syncing ${pending.size} pending message(s)")

        var anyFailed = false
        pending.forEach { msg ->
            runCatching {
                val chatTitle = dao.getChatById(msg.chatId)?.title ?: "Chat ${msg.chatId}"
                val chatRef = firestore.collection("chats").document(msg.chatId)

                // Upsert the chat document
                chatRef.set(
                    mapOf(
                        "title"              to chatTitle,
                        "isGroup"            to false,
                        "lastMessagePreview" to msg.content,
                        "lastMessageTime"    to msg.timestamp,
                        "participants"       to FieldValue.arrayUnion(uid),
                    ),
                    SetOptions.merge(),
                ).await()

                // Write the message document
                chatRef.collection("messages").document(msg.id).set(
                    mapOf(
                        "id"          to msg.id,
                        "chatId"      to msg.chatId,
                        "senderId"    to msg.senderId,
                        "content"     to msg.content,
                        "messageType" to msg.messageType,
                        "timestamp"   to msg.timestamp,
                    ),
                ).await()

                dao.markSynced(msg.id)
                android.util.Log.d("TalkoSync", "Synced message ${msg.id}")
            }.onFailure { e ->
                android.util.Log.w("TalkoSync", "Failed to sync message ${msg.id}: ${e.message}")
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
