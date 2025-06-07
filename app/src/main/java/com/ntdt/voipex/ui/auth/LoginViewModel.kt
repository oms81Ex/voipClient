package com.ntdt.voipex.ui.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
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
    private val app: Application,
    private val loginUseCase: LoginUseCase
) : AndroidViewModel(app) {

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
                val result = loginUseCase.loginAsGuest(name)
                result.onSuccess { authResponse ->
                    val prefs = getApplication<Application>().getSharedPreferences("VoipExPrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("auth_token", authResponse.accessToken)
                        .putString("guest_id", authResponse.user.id)
                        .putString("guest_name", authResponse.user.name)
                        .putBoolean("is_guest", true)
                        .apply()
                    _uiState.value = LoginUiState.Success
                }.onFailure { e ->
                    _uiState.value = LoginUiState.Error(e.message ?: "Failed to create guest session")
                }
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