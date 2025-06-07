package com.ntdt.voipex.ui.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import com.ntdt.voipex.data.signaling.SignalingEvent
import com.ntdt.voipex.domain.model.GuestUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GuestUsersViewModel @Inject constructor(
    private val guestSignalingClient: GuestSignalingClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<GuestUsersUiState>(GuestUsersUiState.Loading)
    val uiState: StateFlow<GuestUsersUiState> = _uiState

    private val _guestUserUpdates = MutableSharedFlow<GuestUserUpdate>()
    val guestUserUpdates: SharedFlow<GuestUserUpdate> = _guestUserUpdates

    private val guestUsers = mutableListOf<GuestUser>()

    init {
        observeSignalingEvents()
    }

    fun loadGuestUsers() {
        viewModelScope.launch {
            _uiState.value = GuestUsersUiState.Loading
            
            // Signaling 서버에서 현재 온라인 게스트 사용자 목록 요청
            // 실제로는 서버에 연결된 후 자동으로 업데이트됨
            _uiState.value = GuestUsersUiState.Success(guestUsers.toList())
        }
    }

    private fun observeSignalingEvents() {
        viewModelScope.launch {
            guestSignalingClient.signalingEvents.collect { event ->
                when (event) {
                    is SignalingEvent.GuestJoined -> {
                        val newUser = GuestUser(
                            id = event.userId,
                            name = event.name
                        )
                        
                        // 중복 체크
                        if (guestUsers.none { it.id == newUser.id }) {
                            guestUsers.add(newUser)
                            _guestUserUpdates.emit(GuestUserUpdate.UserJoined(newUser))
                            _uiState.value = GuestUsersUiState.Success(guestUsers.toList())
                            Timber.d("Guest user joined: ${newUser.name}")
                        }
                    }
                    is SignalingEvent.GuestLeft -> {
                        guestUsers.removeAll { it.id == event.userId }
                        _guestUserUpdates.emit(GuestUserUpdate.UserLeft(event.userId))
                        _uiState.value = GuestUsersUiState.Success(guestUsers.toList())
                        Timber.d("Guest user left: ${event.userId}")
                    }
                    else -> {
                        // 다른 이벤트는 여기서 처리하지 않음
                    }
                }
            }
        }
    }
}

sealed class GuestUsersUiState {
    object Loading : GuestUsersUiState()
    data class Success(val users: List<GuestUser>) : GuestUsersUiState()
    data class Error(val message: String) : GuestUsersUiState()
}

sealed class GuestUserUpdate {
    data class UserJoined(val user: GuestUser) : GuestUserUpdate()
    data class UserLeft(val userId: String) : GuestUserUpdate()
}