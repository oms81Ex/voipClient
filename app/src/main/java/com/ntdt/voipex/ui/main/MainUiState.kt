package com.ntdt.voipex.ui.main

import com.ntdt.voipex.data.model.User

sealed class MainUiState {
    object Loading : MainUiState()
    data class Success(val contacts: List<User>) : MainUiState()
    data class Error(val message: String) : MainUiState()
} 