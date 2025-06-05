package com.ntdt.voipex.data.models

data class User(
    val id: String,
    val name: String,
    val email: String?,
    val isGuest: Boolean
) 