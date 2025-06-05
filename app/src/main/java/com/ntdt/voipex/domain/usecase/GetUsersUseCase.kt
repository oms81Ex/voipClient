package com.ntdt.voipex.domain.usecase

import com.ntdt.voipex.data.models.User
import com.ntdt.voipex.data.repository.UserRepository
import javax.inject.Inject

class GetUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): Result<List<User>> {
        return try {
            Result.success(userRepository.getUsers())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 