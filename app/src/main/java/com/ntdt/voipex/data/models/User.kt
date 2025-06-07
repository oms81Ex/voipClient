package com.ntdt.voipex.data.models

data class User(
    val id: String,
    val name: String?,
    val email: String? = null,
    val isGuest: Boolean = false,
    val role: String? = null
) 