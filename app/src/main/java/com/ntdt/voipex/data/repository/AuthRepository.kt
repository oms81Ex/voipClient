package com.ntdt.voipex.data.repository

import com.ntdt.voipex.data.api.AuthApi
import com.ntdt.voipex.data.api.GuestLoginRequest
import com.ntdt.voipex.data.api.LoginRequest
import com.ntdt.voipex.data.api.RegisterRequest
import com.ntdt.voipex.data.local.PreferencesManager
import com.ntdt.voipex.data.models.AuthResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun login(email: String, password: String): AuthResponse {
        val response = authApi.login(LoginRequest(email, password))
        saveAuthData(response)
        return response
    }

    suspend fun register(email: String, password: String, name: String): AuthResponse {
        val response = authApi.register(RegisterRequest(email, password, name))
        saveAuthData(response)
        return response
    }

    suspend fun guestLogin(name: String): AuthResponse {
        val response = authApi.guestLogin(GuestLoginRequest(name))
        saveAuthData(response)
        return response
    }

    suspend fun logout() {
        authApi.logout()
        preferencesManager.clearUserData()
    }

    private fun saveAuthData(response: AuthResponse) {
        preferencesManager.authToken = response.token
        preferencesManager.userId = response.user.id
        preferencesManager.userName = response.user.name
    }
} 