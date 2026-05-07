package com.talko.app.data.repository

import android.content.Context
import android.view.SurfaceView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.talko.app.core.call.AgoraConfig
import com.talko.app.data.repository.IncomingCallData
import com.talko.app.domain.model.CallType
import com.talko.app.domain.repository.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agora-based call repository.
 *
 * How it works:
 * - Caller creates a Firestore document under "calls/{channelId}" with status "ringing"
 * - Callee listens for incoming calls addressed to their UID
 * - Both join the same Agora channel using channelId as the channel name
 * - Agora handles all media routing, NAT traversal, and codec negotiation
 *
 * No STUN/TURN servers, no SDP, no ICE — Agora handles everything.
 */
@Singleton
class AgoraCallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CallRepository {

    private var engine: RtcEngine? = null
    private var currentChannelId: String? = null
    private var incomingCallListener: ListenerRegistration? = null

    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall: StateFlow<IncomingCallData?> = _incomingCall.asStateFlow()

    // Remote UID assigned by Agora — updated when remote user joins
    private var remoteUid: Int = 0

    // Callback to notify UI when remote user joins (so video can be set up)
    var onRemoteUserJoined: ((Int) -> Unit)? = null

    // Surface views for video rendering
    var localSurfaceView: SurfaceView? = null
    var remoteSurfaceView: SurfaceView? = null

    // ── Engine init ───────────────────────────────────────────────────────────

    private fun ensureEngine(): RtcEngine {
        engine?.let { return it }
        val config = RtcEngineConfig().apply {
            mContext = context
            mAppId = AgoraConfig.APP_ID
            mEventHandler = object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    remoteUid = uid
                    android.util.Log.d("Agora", "Remote user joined: $uid")
                    // Set up remote video if surface is already ready
                    remoteSurfaceView?.let { sv ->
                        engine?.setupRemoteVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, uid))
                    }
                    // Notify UI so it can set up the surface if not ready yet
                    onRemoteUserJoined?.invoke(uid)
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    remoteUid = 0
                    android.util.Log.d("Agora", "Remote user left: $uid")
                }
                override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                    android.util.Log.d("Agora", "Joined channel: $channel as uid: $uid")
                }
                override fun onError(err: Int) {
                    android.util.Log.e("Agora", "Error: $err — ${RtcEngine.getErrorDescription(err)}")
                }
            }
        }
        return RtcEngine.create(config).also { engine = it }
    }

    // ── Outgoing call ─────────────────────────────────────────────────────────

    override suspend fun startCall(peerId: String, callType: CallType) {
        val myUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Not signed in")
        val myEmail = auth.currentUser?.email ?: myUid

        val channelId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        currentChannelId = channelId

        val rtc = ensureEngine()
        rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        rtc.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtc.enableAudio()

        if (callType == CallType.VIDEO) {
            rtc.enableVideo()
            localSurfaceView = SurfaceView(context)
            rtc.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            rtc.startPreview()
        }

        // Resolve peer UID: peerId may be a chatId or display name.
        // Try to find the peer's UID from the chat participants.
        val peerUid = resolvePeerUid(peerId, myUid)

        // Signal the callee via Firestore
        firestore.collection("calls").document(channelId).set(
            mapOf(
                "callerId"    to myUid,
                "callerName"  to (auth.currentUser?.email ?: myUid),
                "calleeId"    to peerUid,
                "callType"    to callType.name,
                "channelId"   to channelId,
                "status"      to "ringing",
                "timestamp"   to System.currentTimeMillis(),
            )
        ).await()

        // Join the Agora channel
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            publishCameraTrack = callType == CallType.VIDEO
            autoSubscribeAudio = true
            autoSubscribeVideo = callType == CallType.VIDEO
        }
        rtc.joinChannel(AgoraConfig.TEMP_TOKEN, channelId, 0, options)
    }

    /**
     * Resolve the peer's Firebase UID from a chatId or display name.
     * Looks up the chat document's participants list and returns the other participant.
     * Falls back to using peerId directly if lookup fails.
     */
    private suspend fun resolvePeerUid(peerId: String, myUid: String): String {
        // If peerId looks like a chatId (starts with "chat_"), look up participants
        if (peerId.startsWith("chat_")) {
            runCatching {
                val chatDoc = firestore.collection("chats").document(peerId).get().await()
                @Suppress("UNCHECKED_CAST")
                val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
                val peerUid = participants.firstOrNull { it != myUid }
                if (peerUid != null) return peerUid
            }
        }
        // Try to find user by display name / email in Firestore
        runCatching {
            val query = firestore.collection("users")
                .whereEqualTo("email", peerId)
                .limit(1)
                .get().await()
            val uid = query.documents.firstOrNull()?.id
            if (uid != null) return uid
        }
        // Last resort: use peerId as-is (works if caller already has the UID)
        return peerId
    }

    // ── Incoming call: listen for calls addressed to current user ─────────────

    fun listenForIncomingCalls() {
        val uid = auth.currentUser?.uid ?: return
        incomingCallListener?.remove()
        incomingCallListener = firestore.collection("calls")
            .whereEqualTo("calleeId", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                if (doc == null) {
                    _incomingCall.value = null
                    return@addSnapshotListener
                }
                _incomingCall.value = IncomingCallData(
                    callId     = doc.id,
                    callerId   = doc.getString("callerId") ?: "",
                    callerName = doc.getString("callerName") ?: "Unknown",
                    callType   = if (doc.getString("callType") == "VIDEO") CallType.VIDEO else CallType.AUDIO,
                    channelId  = doc.getString("channelId") ?: doc.id,
                )
            }
    }

    suspend fun acceptCall(callId: String, callType: CallType) {
        val channelId = firestore.collection("calls").document(callId)
            .get().await().getString("channelId") ?: callId
        currentChannelId = channelId

        val rtc = ensureEngine()
        rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        rtc.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtc.enableAudio()

        if (callType == CallType.VIDEO) {
            rtc.enableVideo()
            localSurfaceView = SurfaceView(context)
            rtc.setupLocalVideo(VideoCanvas(localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            rtc.startPreview()
        }

        // Update status to accepted
        firestore.collection("calls").document(callId)
            .update("status", "accepted").await()

        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            publishCameraTrack = callType == CallType.VIDEO
            autoSubscribeAudio = true
            autoSubscribeVideo = callType == CallType.VIDEO
        }
        rtc.joinChannel(AgoraConfig.TEMP_TOKEN, channelId, 0, options)
        _incomingCall.value = null
    }

    suspend fun declineCall(callId: String) {
        runCatching {
            firestore.collection("calls").document(callId)
                .update("status", "declined").await()
        }
        _incomingCall.value = null
    }

    // ── End call ──────────────────────────────────────────────────────────────

    override suspend fun endCall() {
        engine?.leaveChannel()
        engine?.stopPreview()
        localSurfaceView = null
        remoteSurfaceView = null
        remoteUid = 0

        currentChannelId?.let { id ->
            runCatching {
                firestore.collection("calls").document(id)
                    .update("status", "ended").await()
            }
        }
        currentChannelId = null
    }

    // ── Video surface setup (called from UI) ──────────────────────────────────

    fun setupRemoteVideo(surfaceView: SurfaceView) {
        remoteSurfaceView = surfaceView
        // If remote user already joined, set up immediately; otherwise wait for onUserJoined callback
        if (remoteUid != 0) {
            engine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid))
        }
    }

    fun setupLocalVideo(surfaceView: SurfaceView) {
        localSurfaceView = surfaceView
        engine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun muteLocalAudio(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun muteLocalVideo(muted: Boolean) {
        engine?.muteLocalVideoStream(muted)
    }

    fun setSpeakerphone(enabled: Boolean) {
        engine?.setEnableSpeakerphone(enabled)
    }

    fun switchCamera() {
        engine?.switchCamera()
    }

    // ── CallRepository stubs (video tracks not used with Agora) ──────────────

    override fun getLocalVideoTrack(): Any? = null
    override fun getRemoteVideoTrack(): Any? = null
    override fun getLocalAudioTrack(): Any? = null
}

// Updated IncomingCallData with channelId
data class IncomingCallData(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val callType: CallType,
    val channelId: String = callId,
)
