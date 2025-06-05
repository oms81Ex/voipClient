package com.ntdt.voipex.domain.usecase

import com.ntdt.voipex.data.models.AuthResponse
import com.ntdt.voipex.data.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthResponse> {
        return try {
            Result.success(authRepository.login(email, password))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginAsGuest(name: String): Result<AuthResponse> {
        return try {
            Result.success(authRepository.guestLogin(name))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 