package com.ntdt.voipex.data.model

data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val isGuest: Boolean = false
) 