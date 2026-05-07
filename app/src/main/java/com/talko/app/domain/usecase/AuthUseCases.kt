package com.talko.app.domain.usecase

import com.talko.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): String? = authRepository.currentUserId()
}

class IsProfileCompletedUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Boolean = authRepository.isProfileCompleted()
}

class SignInOrRegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    /** Sign in to an existing account. Throws if credentials are wrong. */
    suspend fun signIn(email: String, password: String): String =
        authRepository.signIn(email, password)

    /** Create a new account. Throws if email already exists. */
    suspend fun register(email: String, password: String): String =
        authRepository.register(email, password)
}

class SendPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String) =
        authRepository.sendPasswordReset(email)
}

class SaveProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(fullName: String, bio: String) =
        authRepository.saveProfile(fullName, bio)
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke() = authRepository.signOut()
}
