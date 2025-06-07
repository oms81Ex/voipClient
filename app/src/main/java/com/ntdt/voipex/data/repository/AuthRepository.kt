package com.ntdt.voipex.data.repository

import com.ntdt.voipex.data.api.AuthApi
import com.ntdt.voipex.data.models.AuthResponse
import com.ntdt.voipex.data.models.GuestLoginRequest
import com.ntdt.voipex.utils.ErrorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import com.ntdt.voipex.data.models.User as UserModel

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) {
    
    suspend fun login(email: String, password: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf(
                    "email" to email,
                    "password" to password
                )
                val response = authApi.login(request)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.data)
                    } ?: Result.failure(Exception("Empty response"))
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loginAsGuest(name: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = GuestLoginRequest(
                    name = name,
                    isGuest = true
                )
                val response = authApi.loginAsGuest(request)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.data)
                    } ?: Result.failure(Exception("Empty response"))
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun register(name: String, email: String, password: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf(
                    "name" to name,
                    "email" to email,
                    "password" to password
                )
                val response = authApi.register(request)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.data)
                    } ?: Result.failure(Exception("Empty response"))
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun logout(token: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.logout("Bearer $token")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun refreshToken(refreshToken: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf("refreshToken" to refreshToken)
                val response = authApi.refreshToken(request)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.data)
                    } ?: Result.failure(Exception("Empty response"))
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    suspend fun requestGuestToken(guestUser: UserModel): String? = 
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf<String, String>(
                    "guestName" to (guestUser.name ?: "Guest"),
                    "deviceId" to "android_${System.currentTimeMillis()}"
                )
                val response = authApi.requestGuestToken(request)
                if (response.isSuccessful) {
                    response.body()?.data?.token
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    
    suspend fun upgradeGuestToUser(
        guestUserId: String,
        name: String,
        email: String,
        password: String
    ): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf(
                    "guestUserId" to guestUserId,
                    "name" to name,
                    "email" to email,
                    "password" to password
                )
                val response = authApi.upgradeGuest(request)
                if (response.isSuccessful) {
                    response.body()?.let {
                        Result.success(it.data)
                    } ?: Result.failure(Exception("Empty response"))
                } else {
                    val errorMessage = ErrorUtils.parseError(response)
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}