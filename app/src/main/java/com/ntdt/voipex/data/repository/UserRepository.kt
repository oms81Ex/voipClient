package com.ntdt.voipex.data.repository

import com.ntdt.voipex.data.api.UserApi
import com.ntdt.voipex.data.models.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi
) {
    suspend fun getUsers(): List<User> {
        return userApi.getUsers()
    }
} 