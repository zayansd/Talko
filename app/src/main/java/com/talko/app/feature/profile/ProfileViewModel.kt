package com.talko.app.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.talko.app.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val fullName: String = "",
    val email: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isOffline: Boolean = false,
    val snackMessage: String? = null,   // transient success/info messages
    val error: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadProfile() = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return@launch
        }

        val docRef = firestore.collection("users").document(uid)

        // 1. Try cache first — works offline, instant
        val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) {
            _uiState.update {
                it.copy(
                    fullName  = cached.getString("name").orEmpty(),
                    email     = auth.currentUser?.email.orEmpty(),
                    bio       = cached.getString("bio").orEmpty(),
                    photoUrl  = cached.getString("photoUrl").orEmpty(),
                    isLoading = false,
                    isOffline = false,
                )
            }
        }

        // 2. Try server for fresh data — gracefully handle offline
        runCatching { docRef.get(Source.SERVER).await() }
            .onSuccess { doc ->
                _uiState.update {
                    it.copy(
                        fullName  = doc.getString("name").orEmpty(),
                        email     = auth.currentUser?.email.orEmpty(),
                        bio       = doc.getString("bio").orEmpty(),
                        photoUrl  = doc.getString("photoUrl").orEmpty(),
                        isLoading = false,
                        isOffline = false,
                        error     = null,
                    )
                }
            }
            .onFailure { e ->
                val isOffline = isOfflineError(e)
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        isOffline = isOffline,
                        // Only show error if we have no cached data to show
                        error = if (cached == null || !cached.exists()) {
                            if (isOffline) "You're offline. Connect to load your profile."
                            else friendlyFirestoreError(e)
                        } else null,
                    )
                }
            }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveProfile(fullName: String, bio: String) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(error = "Not signed in.") }
            return@launch
        }
        if (fullName.isBlank()) {
            _uiState.update { it.copy(error = "Full name cannot be empty.") }
            return@launch
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        // Optimistic update — update UI immediately
        _uiState.update { it.copy(fullName = fullName.trim(), bio = bio.trim()) }

        runCatching {
            firestore.collection("users").document(uid)
                .set(
                    mapOf("name" to fullName.trim(), "bio" to bio.trim()),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
                .await()
        }.onSuccess {
            _uiState.update {
                it.copy(isSaving = false, snackMessage = "Profile saved ✓", error = null)
            }
        }.onFailure { e ->
            val isOffline = isOfflineError(e)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    // Firestore queues writes offline — they sync when back online
                    snackMessage = if (isOffline) "Saved locally — will sync when online" else null,
                    error = if (isOffline) null else friendlyFirestoreError(e),
                )
            }
        }
    }

    fun clearSnack() = _uiState.update { it.copy(snackMessage = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun uploadProfilePhoto(uri: Uri) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        _uiState.update { it.copy(isSaving = true) }
        runCatching {
            val ref = FirebaseStorage.getInstance().reference
                .child("profile_photos/$uid/${UUID.randomUUID()}.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            firestore.collection("users").document(uid)
                .update("photoUrl", url).await()
            url
        }.onSuccess { url ->
            _uiState.update { it.copy(isSaving = false, photoUrl = url, snackMessage = "Photo updated ✓") }
        }.onFailure { e ->
            _uiState.update { it.copy(isSaving = false, error = "Failed to upload photo: ${e.message}") }
        }
    }

    fun logout() {
        signOutUseCase()
        _uiState.update { it.copy(loggedOut = true) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isOfflineError(e: Throwable): Boolean {
        if (e is FirebaseFirestoreException) {
            return e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        }
        val msg = e.message?.lowercase() ?: ""
        return "offline" in msg || "unavailable" in msg || "network" in msg ||
            "failed to get document" in msg
    }

    private fun friendlyFirestoreError(e: Throwable): String {
        android.util.Log.e("TalkoProfile", "Firestore error: ${e.message}")
        return when {
            isOfflineError(e) -> "You're offline. Changes will sync when you reconnect."
            "permission" in (e.message?.lowercase() ?: "") ->
                "Permission denied. Please sign in again."
            else -> "Something went wrong. Please try again."
        }
    }
}
