package com.ntdt.voipex.domain.usecase

import com.ntdt.voipex.data.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(token: String): Result<Unit> {
        return authRepository.logout(token)
    }
} 