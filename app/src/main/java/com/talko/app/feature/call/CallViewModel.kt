package com.talko.app.feature.call

import android.content.Context
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talko.app.data.local.dao.TalkoDao
import com.talko.app.data.local.entity.CallLogEntity
import com.talko.app.data.repository.AgoraCallRepositoryImpl
import com.talko.app.domain.model.CallType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CallUiState(
    val inCall: Boolean = false,
    val muted: Boolean = false,
    val videoEnabled: Boolean = true,
    val speakerOn: Boolean = false,
    val callDurationSec: Long = 0L,
    val remoteUserJoined: Boolean = false,   // true once remote peer connects
    val errorDialog: String? = null,
)

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callRepository: AgoraCallRepositoryImpl,
    private val dao: TalkoDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var callStartTime = 0L
    private var currentPeerName = ""
    private var currentCallType = CallType.AUDIO

    fun start(peerId: String, type: CallType) = viewModelScope.launch {
        currentPeerName = peerId
        currentCallType = type
        // Register callback so we know when remote video is ready
        callRepository.onRemoteUserJoined = { _ ->
            _uiState.update { it.copy(remoteUserJoined = true) }
        }
        runCatching { callRepository.startCall(peerId, type) }
            .onSuccess {
                callStartTime = System.currentTimeMillis()
                _uiState.update { it.copy(inCall = true) }
                startDurationTimer()
            }
            .onFailure { e ->
                _uiState.update { it.copy(errorDialog = e.message ?: "Failed to start call. Check your Agora App ID.") }
                saveCallLog("MISSED")
            }
    }

    fun end() = viewModelScope.launch {
        val duration = if (callStartTime > 0) (System.currentTimeMillis() - callStartTime) / 1000 else 0L
        callRepository.endCall()
        saveCallLog(if (_uiState.value.inCall) "COMPLETED" else "MISSED", duration)
        _uiState.update { CallUiState() }
    }

    fun toggleMute() {
        val muted = !_uiState.value.muted
        callRepository.muteLocalAudio(muted)
        _uiState.update { it.copy(muted = muted) }
    }

    fun toggleVideo() {
        val enabled = !_uiState.value.videoEnabled
        callRepository.muteLocalVideo(!enabled)
        _uiState.update { it.copy(videoEnabled = enabled) }
    }

    fun toggleSpeaker() {
        val speakerOn = !_uiState.value.speakerOn
        callRepository.setSpeakerphone(speakerOn)
        _uiState.update { it.copy(speakerOn = speakerOn) }
    }

    fun switchCamera() = callRepository.switchCamera()

    fun setupLocalVideo(surfaceView: SurfaceView) = callRepository.setupLocalVideo(surfaceView)
    fun setupRemoteVideo(surfaceView: SurfaceView) = callRepository.setupRemoteVideo(surfaceView)

    fun dismissDialog() = _uiState.update { it.copy(errorDialog = null) }

    private fun startDurationTimer() = viewModelScope.launch {
        while (_uiState.value.inCall) {
            delay(1_000)
            _uiState.update { it.copy(callDurationSec = it.callDurationSec + 1) }
        }
    }

    private suspend fun saveCallLog(status: String, durationSec: Long = 0L) {
        runCatching {
            dao.insertCallLog(
                CallLogEntity(
                    id = UUID.randomUUID().toString(),
                    peerId = currentPeerName,
                    peerName = currentPeerName,
                    callType = currentCallType.name,
                    direction = "OUTGOING",
                    status = status,
                    durationSec = durationSec,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }
}
