package com.ntdt.voipex.data.api

import com.ntdt.voipex.data.models.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/guest")
    suspend fun guestLogin(@Body request: GuestLoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout()
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class GuestLoginRequest(
    val name: String
) 