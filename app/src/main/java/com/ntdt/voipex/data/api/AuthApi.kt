package com.ntdt.voipex.data.api

import com.ntdt.voipex.data.models.ApiResponse
import com.ntdt.voipex.data.models.AuthResponse
import com.ntdt.voipex.data.models.User
import com.ntdt.voipex.data.models.GuestLoginRequest
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>

    @POST("auth/guest")
    suspend fun loginAsGuest(@Body request: GuestLoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>

    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiResponse<Unit>>

    @POST("auth/refresh-token")
    suspend fun refreshToken(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>

    @GET("auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<ApiResponse<User>>

    @DELETE("auth/guest/{guestId}")
    suspend fun deleteGuest(@Path("guestId") guestId: String): Response<ApiResponse<Unit>>
    
    @POST("auth/guest/token")
    suspend fun requestGuestToken(@Body request: Map<String, String>): Response<ApiResponse<GuestTokenResponse>>
    
    @POST("auth/guest/upgrade")
    suspend fun upgradeGuest(@Body request: Map<String, String>): Response<ApiResponse<AuthResponse>>
}

data class GuestTokenResponse(
    val token: String,
    val expiresIn: Long,
    val limitations: GuestLimitations
)

data class GuestLimitations(
    val maxCallDuration: Int,
    val canRecord: Boolean,
    val canSaveContacts: Boolean,
    val canViewHistory: Boolean
) 