package com.ntdt.voipex.ui.main

sealed class MainEvent {
    object NavigateToLogin : MainEvent()
    data class ShowError(val message: String) : MainEvent()
} 