package com.ntdt.voipex.domain.usecase

import com.ntdt.voipex.data.models.AuthResponse
import com.ntdt.voipex.data.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthResponse> {
        return authRepository.login(email, password)
    }

    suspend fun loginAsGuest(name: String): Result<AuthResponse> {
        return authRepository.loginAsGuest(name)
    }
} 