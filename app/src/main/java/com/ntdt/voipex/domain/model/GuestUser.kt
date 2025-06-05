package com.ntdt.voipex.domain.model

data class GuestUser(
    val id: String,
    val name: String,
    val isOnline: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) 