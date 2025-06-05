package com.ntdt.voipex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.manager.GuestSessionManager
import com.ntdt.voipex.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val guestSessionManager: GuestSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error("Please enter email and password")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = LoginUiState.Loading
                loginUseCase(email, password)
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun loginAsGuest(name: String) {
        if (name.isEmpty()) {
            _uiState.value = LoginUiState.Error("Please enter your name")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = LoginUiState.Loading
                guestSessionManager.createGuestSession(name)
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Failed to create guest session")
            }
        }
    }
}

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
} 