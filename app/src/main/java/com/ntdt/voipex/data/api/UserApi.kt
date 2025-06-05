package com.ntdt.voipex.data.api

import com.ntdt.voipex.data.models.User
import retrofit2.http.GET

interface UserApi {
    @GET("users")
    suspend fun getUsers(): List<User>
} 