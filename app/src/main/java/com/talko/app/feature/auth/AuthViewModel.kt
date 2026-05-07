package com.talko.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talko.app.domain.usecase.GetCurrentUserUseCase
import com.talko.app.domain.usecase.IsProfileCompletedUseCase
import com.talko.app.domain.usecase.SaveProfileUseCase
import com.talko.app.domain.usecase.SendPasswordResetUseCase
import com.talko.app.domain.usecase.SignInOrRegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    // Mode
    val isRegisterMode: Boolean = false,
    // Login form
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val resetEmailSent: Boolean = false,
    // Session
    val userId: String? = null,
    val profileCompleted: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val isProfileCompletedUseCase: IsProfileCompletedUseCase,
    private val signInOrRegisterUseCase: SignInOrRegisterUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(userId = getCurrentUserUseCase()))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // If already signed in, check profile completion
        viewModelScope.launch {
            val uid = getCurrentUserUseCase() ?: return@launch
            val completed = isProfileCompletedUseCase()
            _uiState.update { it.copy(userId = uid, profileCompleted = completed) }
        }
    }

    // ── Form inputs ───────────────────────────────────────────────────────────

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value.trim(), error = null, resetEmailSent = false) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isRegisterMode = !it.isRegisterMode,
                error = null,
                resetEmailSent = false,
                password = "",
                confirmPassword = "",
            )
        }
    }

    // ── Sign in / Register ────────────────────────────────────────────────────

    fun signIn() = viewModelScope.launch {
        val state = _uiState.value

        if (!isValidEmail(state.email)) {
            _uiState.update { it.copy(error = "Enter a valid email address.") }
            return@launch
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters.") }
            return@launch
        }
        if (state.isRegisterMode && state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return@launch
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        if (state.isRegisterMode) {
            // Explicit registration — create account directly
            runCatching { signInOrRegisterUseCase.register(state.email, state.password) }
                .onSuccess { uid ->
                    val completed = isProfileCompletedUseCase()
                    _uiState.update { it.copy(isLoading = false, userId = uid, profileCompleted = completed) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = friendlyError(e.message)) }
                }
        } else {
            // Sign-in only — do NOT auto-create
            runCatching { signInOrRegisterUseCase.signIn(state.email, state.password) }
                .onSuccess { uid ->
                    val completed = isProfileCompletedUseCase()
                    _uiState.update { it.copy(isLoading = false, userId = uid, profileCompleted = completed) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = friendlyError(e.message)) }
                }
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────

    fun sendPasswordReset() = viewModelScope.launch {
        val email = _uiState.value.email
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(error = "Enter your email address first.") }
            return@launch
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { sendPasswordResetUseCase(email) }
            .onSuccess { _uiState.update { it.copy(isLoading = false, resetEmailSent = true) } }
            .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = friendlyError(e.message)) } }
    }

    // ── Profile setup ─────────────────────────────────────────────────────────

    fun saveProfile(fullName: String, bio: String) = viewModelScope.launch {
        // "__skip__" sentinel means the user tapped "Skip for now" — go straight to home
        if (fullName == "__skip__") {
            _uiState.update { it.copy(profileCompleted = true) }
            return@launch
        }
        if (fullName.isBlank()) {
            _uiState.update { it.copy(error = "Full name is required.") }
            return@launch
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { saveProfileUseCase(fullName.trim(), bio.trim()) }
            .onSuccess {
                _uiState.update { it.copy(isLoading = false, profileCompleted = true, error = null) }
            }
            .onFailure { e ->
                android.util.Log.e("TalkoAuth", "saveProfile error: ${e.message}")
                val isOffline = "offline" in (e.message?.lowercase() ?: "") ||
                    "unavailable" in (e.message?.lowercase() ?: "") ||
                    "failed to get document" in (e.message?.lowercase() ?: "") ||
                    "permission" in (e.message?.lowercase() ?: "") ||
                    "insufficient" in (e.message?.lowercase() ?: "")
                if (isOffline) {
                    // Firestore queues the write — treat as success so user can proceed
                    _uiState.update { it.copy(isLoading = false, profileCompleted = true, error = null) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save profile.") }
                }
            }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isValidEmail(email: String) =
        email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun friendlyError(raw: String?): String {
        if (raw == null) return "Something went wrong. Please try again."

        // Log the raw error so it's visible in Logcat for debugging
        android.util.Log.e("TalkoAuth", "Firebase Auth error: $raw")

        return when {
            // Wrong password
            "wrong-password" in raw || "INVALID_PASSWORD" in raw ||
            ("password" in raw.lowercase() && "wrong" in raw.lowercase()) ->
                "Incorrect password. Please try again."

            // No account — we auto-create, so this shouldn't surface
            "user-not-found" in raw || "no user record" in raw.lowercase() ->
                "No account found. Creating a new account…"

            // Email already in use
            "email-already-in-use" in raw || "EMAIL_EXISTS" in raw ||
            "EMAIL_ALREADY_IN_USE" in raw ->
                "This email is already registered. Try signing in."

            // Invalid credentials — account exists but password is wrong
            "INVALID_LOGIN_CREDENTIALS" in raw || "ERROR_INVALID_CREDENTIAL" in raw ->
                "Incorrect email or password. Please try again."

            // Weak password
            "weak-password" in raw || "WEAK_PASSWORD" in raw ->
                "Password is too weak. Use at least 6 characters."

            // Invalid email format
            "invalid-email" in raw || "INVALID_EMAIL" in raw ->
                "Invalid email address format."

            // Email/Password sign-in not enabled in Firebase console
            "sign-in method" in raw.lowercase() || "OPERATION_NOT_ALLOWED" in raw ||
            "not enabled" in raw.lowercase() ->
                "Email sign-in is not enabled. Please enable Email/Password in your Firebase console → Authentication → Sign-in method."

            // Too many requests / rate limited
            "too-many-requests" in raw || "TOO_MANY_ATTEMPTS" in raw ->
                "Too many attempts. Please wait a few minutes and try again."

            // Actual network error (timeout, no connectivity)
            // Only match when it's clearly a connectivity issue, not a Firebase config error
            raw.contains("IOException") || raw.contains("SocketTimeoutException") ||
            raw.contains("UnknownHostException") || raw.contains("ConnectException") ->
                "Network error. Check your internet connection and try again."

            // Fallback: show the raw message so the user/developer can see what's wrong
            else -> raw
        }
    }
}
