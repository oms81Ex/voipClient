package com.ntdt.voipex.data.models

data class AuthResponse(
    val user: User,
    val accessToken: String,
    val refreshToken: String? = null
) 