package com.talko.app.data.repository

import com.talko.app.data.local.dao.TalkoDao
import com.talko.app.data.local.entity.ChatEntity
import com.talko.app.data.local.entity.MessageEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.talko.app.domain.model.Chat
import com.talko.app.domain.model.Message
import com.talko.app.domain.model.MessageType
import com.talko.app.domain.repository.AuthRepository
import com.talko.app.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ── Auth ──────────────────────────────────────────────────────────────────────

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : AuthRepository {

    override fun currentUserId(): String? = auth.currentUser?.uid

    override fun currentUserEmail(): String? = auth.currentUser?.email

    override suspend fun isProfileCompleted(): Boolean {
        val uid = currentUserId() ?: return false
        // Try cache first — works offline
        val cached = runCatching {
            firestore.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.CACHE).await()
        }.getOrNull()
        if (cached != null && cached.exists() && !cached.getString("name").isNullOrBlank()) {
            return true
        }
        // Try server — if offline, assume not completed so user can fill profile
        return runCatching {
            val doc = firestore.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.SERVER).await()
            doc.exists() && !doc.getString("name").isNullOrBlank()
        }.getOrDefault(false)
    }

    /**
     * Try sign-in first; if the account doesn't exist, create it.
     * Uses Firebase Email/Password — free on Spark plan, no billing required.
     */
    override suspend fun signIn(email: String, password: String): String {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.uid ?: throw IllegalStateException("UID is null after sign-in")
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            android.util.Log.e("TalkoAuth", "signIn failed [${e.errorCode}]: ${e.message}")
            when (e.errorCode) {
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_PASSWORD" -> throw Exception("Incorrect password. Please try again.")
                "ERROR_USER_NOT_FOUND"   -> throw Exception("No account found with this email. Please create an account.")
                "ERROR_USER_DISABLED"    -> throw Exception("This account has been disabled.")
                "ERROR_TOO_MANY_REQUESTS"-> throw Exception("Too many attempts. Please wait and try again.")
                else                     -> throw Exception(e.message ?: "Sign-in failed.")
            }
        }
    }

    override suspend fun register(email: String, password: String): String {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.uid ?: throw IllegalStateException("UID is null after registration")
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            android.util.Log.e("TalkoAuth", "register failed [${e.errorCode}]: ${e.message}")
            when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> throw Exception("An account with this email already exists. Please sign in.")
                "ERROR_WEAK_PASSWORD"        -> throw Exception("Password is too weak. Use at least 6 characters.")
                "ERROR_INVALID_EMAIL"        -> throw Exception("Invalid email address format.")
                else                         -> throw Exception(e.message ?: "Registration failed.")
            }
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun saveProfile(fullName: String, bio: String) {
        val uid = currentUserId() ?: throw IllegalStateException("User not logged in")
        val token = runCatching { messaging.token.await() }.getOrNull()
        // Use set+merge so offline writes are queued by Firestore and synced later
        firestore.collection("users").document(uid).set(
            mapOf(
                "name"     to fullName,
                "bio"      to bio,
                "email"    to (auth.currentUser?.email ?: ""),
                "isOnline" to true,
                "fcmToken" to (token ?: ""),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    override fun signOut() {
        // Mark offline before signing out
        val uid = auth.currentUser?.uid
        if (uid != null) {
            firestore.collection("users").document(uid)
                .update("isOnline", false)
        }
        auth.signOut()
    }

    /** Call from MainActivity.onStart / onStop to update presence. */
    override fun setOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update(mapOf("isOnline" to isOnline, "lastSeen" to System.currentTimeMillis()))
    }
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: TalkoDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ChatRepository {
    private val typingMap = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()
    private val typingListeners = mutableMapOf<String, ListenerRegistration>()
    private var chatListListener: ListenerRegistration? = null

    override fun observeChats(): Flow<List<Chat>> {
        ensureChatListListener()
        return dao.observeChats().map { list ->
            list.map {
                Chat(
                    id = it.id,
                    title = it.title,
                    isGroup = it.isGroup,
                    lastMessagePreview = it.lastMessagePreview,
                    unreadCount = it.unreadCount,
                    lastMessageTime = it.lastMessageTime,
                )
            }
        }
    }
    override fun observeMessages(chatId: String): Flow<List<Message>> {
        ensureMessageListener(chatId)
        return dao.observeMessages(chatId).map { list ->
            list.map {
                Message(
                    id = it.id,
                    chatId = it.chatId,
                    senderId = it.senderId,
                    content = it.content,
                    messageType = MessageType.valueOf(it.messageType),
                    timestamp = it.timestamp,
                    isSynced = it.isSynced,
                )
            }
        }
    }

    override suspend fun sendMessage(chatId: String, text: String) {
        sendTyped(chatId, text, MessageType.TEXT)
    }

    override suspend fun createChat(chatId: String, title: String) {
        val existing = dao.getChatById(chatId)
        if (existing == null) {
            dao.upsertChat(ChatEntity(chatId, title, false, "", 0, System.currentTimeMillis()))
        } else if (existing.title.startsWith("Chat ") || existing.title.startsWith("chat_")) {
            // Update with real title if it was previously set to a placeholder
            dao.upsertChat(existing.copy(title = title))
        }
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("chats").document(chatId).set(
                mapOf(
                    "title"              to title,
                    "isGroup"            to false,
                    "lastMessagePreview" to (existing?.lastMessagePreview ?: ""),
                    "lastMessageTime"    to System.currentTimeMillis(),
                    "participants"       to FieldValue.arrayUnion(uid),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }
    }

    override suspend fun sendVoiceMessage(chatId: String, audioUrl: String) {
        sendTyped(chatId, audioUrl, MessageType.VOICE)
    }

    override suspend fun sendImageMessage(chatId: String, imageUrl: String) {
        sendTyped(chatId, imageUrl, MessageType.IMAGE)
    }

    private suspend fun sendTyped(chatId: String, content: String, type: MessageType) {
        val senderId = auth.currentUser?.uid ?: "guest"
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        // Preserve existing chat title — never overwrite a real name with "Chat $chatId"
        // chatId format: "chat_firstname_lastname_timestamp" → extract "Firstname Lastname"
        val existingTitle = dao.getChatById(chatId)?.title
            ?.takeIf { it.isNotBlank() && !it.startsWith("Chat ") && !it.startsWith("chat_") }
            ?: run {
                // Strip "chat_" prefix and trailing timestamp (last segment of digits)
                val parts = chatId.removePrefix("chat_").split("_")
                val nameParts = parts.dropLast(1).filter { it.isNotBlank() && !it.all { c -> c.isDigit() } }
                nameParts.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.ifBlank { chatId }
            }

        // 1. Save to Room immediately — offline-first
        dao.upsertMessage(MessageEntity(messageId, chatId, senderId, content, type.name, now, false))
        dao.upsertChat(ChatEntity(chatId, existingTitle, false, content, 0, now))

        // 2. Sync to Firestore (queued automatically when offline)
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "title"              to existingTitle,
                "isGroup"            to false,
                "lastMessagePreview" to content,
                "lastMessageTime"    to now,
                "participants"       to FieldValue.arrayUnion(senderId),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
        chatRef.collection("messages").document(messageId).set(
            mapOf(
                "id"          to messageId,
                "chatId"      to chatId,
                "senderId"    to senderId,
                "content"     to content,
                "messageType" to type.name,
                "timestamp"   to now,
            ),
        ).await()
        dao.markSynced(messageId)
    }

    override suspend fun setTyping(chatId: String, isTyping: Boolean) {
        typingMap.getOrPut(chatId) { MutableStateFlow(false) }.value = isTyping
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("chats").document(chatId).set(
                mapOf("typing" to mapOf(uid to isTyping)),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }
    }

    override fun observeTyping(chatId: String): Flow<Boolean> {
        ensureTypingListener(chatId)
        return typingMap.getOrPut(chatId) { MutableStateFlow(false) }
    }

    private val onlineStatusMap = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val onlineStatusListeners = mutableMapOf<String, ListenerRegistration>()

    override fun observePeerOnlineStatus(peerId: String): Flow<Boolean> {
        // peerId here is actually the chatId — we need to find the other participant's UID
        // from the chat document, then watch their user document
        val flow = onlineStatusMap.getOrPut(peerId) { MutableStateFlow(false) }

        if (!onlineStatusListeners.containsKey(peerId)) {
            val myUid = auth.currentUser?.uid
            // First get the chat to find the peer's UID
            scope.launch {
                runCatching {
                    val chatDoc = firestore.collection("chats").document(peerId).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
                    val peerUid = participants.firstOrNull { it != myUid }
                    if (peerUid != null) {
                        val listener = firestore.collection("users").document(peerUid)
                            .addSnapshotListener { snap, error ->
                                if (error != null) return@addSnapshotListener
                                flow.value = snap?.getBoolean("isOnline") ?: false
                            }
                        onlineStatusListeners[peerId] = listener
                    }
                }
            }
        }
        return flow
    }

    private fun ensureChatListListener() {
        // If already listening, skip
        if (chatListListener != null) return
        val uid = auth.currentUser?.uid
        // If not logged in yet, retry when auth state changes
        if (uid == null) {
            scope.launch {
                // Poll briefly — in practice auth is resolved before this is called
                kotlinx.coroutines.delay(500)
                val retryUid = auth.currentUser?.uid ?: return@launch
                startChatListListener(retryUid)
            }
            return
        }
        startChatListListener(uid)
    }

    private fun startChatListListener(uid: String) {
        if (chatListListener != null) return
        chatListListener = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("TalkoChat", "Chat list listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                scope.launch {
                    docs.forEach { doc -> dao.upsertChat(doc.toChatEntity()) }
                }
            }
    }

    private fun ensureMessageListener(chatId: String) {
        if (messageListeners.containsKey(chatId)) return
        val listener = firestore.collection("chats").document(chatId).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("TalkoChat", "Message listener error [$chatId]: ${error.message}")
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                scope.launch {
                    docs.forEach { doc ->
                        dao.upsertMessage(doc.toMessageEntity(chatId))
                        dao.markSynced(doc.getString("id").orEmpty())
                    }
                }
            }
        messageListeners[chatId] = listener
    }

    private fun ensureTypingListener(chatId: String) {
        if (typingListeners.containsKey(chatId)) return
        val myUid = auth.currentUser?.uid
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("TalkoChat", "Typing listener error [$chatId]: ${error.message}")
                    return@addSnapshotListener
                }
                val typingMapData = snapshot?.get("typing") as? Map<*, *> ?: emptyMap<Any, Any>()
                val someoneTyping = typingMapData.entries.any { (key, value) ->
                    key?.toString() != myUid && value == true
                }
                typingMap.getOrPut(chatId) { MutableStateFlow(false) }.value = someoneTyping
            }
        typingListeners[chatId] = listener
    }
}

private fun DocumentSnapshot.toChatEntity(): ChatEntity {
    return ChatEntity(
        id = id,
        title = getString("title").orEmpty().ifBlank { "Chat $id" },
        isGroup = getBoolean("isGroup") ?: false,
        lastMessagePreview = getString("lastMessagePreview").orEmpty(),
        unreadCount = (getLong("unreadCount") ?: 0L).toInt(),
        lastMessageTime = getLong("lastMessageTime") ?: 0L,
    )
}

private fun DocumentSnapshot.toMessageEntity(chatId: String): MessageEntity {
    return MessageEntity(
        id = getString("id").orEmpty().ifBlank { id },
        chatId = getString("chatId").orEmpty().ifBlank { chatId },
        senderId = getString("senderId").orEmpty(),
        content = getString("content").orEmpty(),
        messageType = getString("messageType").orEmpty().ifBlank { MessageType.TEXT.name },
        timestamp = getLong("timestamp") ?: 0L,
        isSynced = true,
    )
}


