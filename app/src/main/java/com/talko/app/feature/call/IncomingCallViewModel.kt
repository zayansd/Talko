package com.talko.app.feature.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talko.app.data.repository.AgoraCallRepositoryImpl
import com.talko.app.data.repository.IncomingCallData
import com.talko.app.domain.model.CallType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Singleton ViewModel that listens for incoming calls across the entire app lifetime.
 * Injected at the NavHost level so it's always active when the user is logged in.
 */
@HiltViewModel
class IncomingCallViewModel @Inject constructor(
    private val callRepository: AgoraCallRepositoryImpl,
) : ViewModel() {

    val incomingCall: StateFlow<IncomingCallData?> = callRepository.incomingCall

    fun startListening() {
        callRepository.listenForIncomingCalls()
    }

    fun accept(callId: String, callType: CallType) = viewModelScope.launch {
        callRepository.acceptCall(callId, callType)
    }
    fun decline(callId: String) = viewModelScope.launch {
        callRepository.declineCall(callId)
    }
}
