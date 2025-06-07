package com.ntdt.voipex.ui.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.local.PreferencesManager
import com.ntdt.voipex.data.manager.GuestSessionManager
import com.ntdt.voipex.data.repository.AuthRepository
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import com.ntdt.voipex.domain.model.GuestUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GuestLoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val guestSessionManager: GuestSessionManager,
    private val guestSignalingClient: GuestSignalingClient,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<GuestLoginUiState>(GuestLoginUiState.Idle)
    val uiState: StateFlow<GuestLoginUiState> = _uiState

    fun loginAsGuest(name: String) {
        viewModelScope.launch {
            _uiState.value = GuestLoginUiState.Loading
            
            try {
                // 서버에 게스트 토큰 요청
                val response = authRepository.loginAsGuest(name)
                
                response.fold(
                    onSuccess = { authResponse ->
                        // 게스트 세션 생성
                        val guestUser = guestSessionManager.createGuestSession(name)
                        
                        // 사용자 정보 저장 (게스트 플래그 포함)
                        preferencesManager.saveUserData(
                            userId = authResponse.user.id,
                            userName = authResponse.user.name ?: name,
                            token = authResponse.accessToken,
                            isGuestUser = true
                        )
                        
                        // Signaling 서버 연결
                        val guest = GuestUser(
                            id = authResponse.user.id,
                            name = authResponse.user.name ?: name
                        )
                        guestSignalingClient.connect(guest)
                        
                        _uiState.value = GuestLoginUiState.Success
                        Timber.d("Guest login successful: ${authResponse.user.id}")
                    },
                    onFailure = { exception ->
                        _uiState.value = GuestLoginUiState.Error(
                            exception.message ?: "게스트 로그인 실패"
                        )
                        Timber.e(exception, "Guest login failed")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = GuestLoginUiState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다"
                )
                Timber.e(e, "Guest login error")
            }
        }
    }
}

sealed class GuestLoginUiState {
    object Idle : GuestLoginUiState()
    object Loading : GuestLoginUiState()
    object Success : GuestLoginUiState()
    data class Error(val message: String) : GuestLoginUiState()
}