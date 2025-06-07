package com.ntdt.voipex.data.models

data class GuestLoginRequest(
    val name: String,
    val isGuest: Boolean = true
)