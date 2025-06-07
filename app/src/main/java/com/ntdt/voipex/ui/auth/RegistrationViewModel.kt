package com.ntdt.voipex.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class RegistrationUiState {
    object Idle : RegistrationUiState()
    object Loading : RegistrationUiState()
    object Success : RegistrationUiState()
    data class Error(val message: String) : RegistrationUiState()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = _uiState
    
    fun register(
        name: String,
        email: String,
        password: String,
        guestUserId: String? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = RegistrationUiState.Loading
                
                val result = if (guestUserId != null) {
                    // 게스트 사용자를 정식 사용자로 전환
                    authRepository.upgradeGuestToUser(
                        guestUserId = guestUserId,
                        name = name,
                        email = email,
                        password = password
                    )
                } else {
                    // 일반 회원가입
                    authRepository.register(
                        name = name,
                        email = email,
                        password = password
                    )
                }
                
                if (result.isSuccess) {
                    _uiState.value = RegistrationUiState.Success
                } else {
                    _uiState.value = RegistrationUiState.Error(
                        result.exceptionOrNull()?.message ?: "회원가입에 실패했습니다"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                _uiState.value = RegistrationUiState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다"
                )
            }
        }
    }
}