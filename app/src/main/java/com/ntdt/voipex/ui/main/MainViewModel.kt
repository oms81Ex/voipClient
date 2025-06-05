package com.ntdt.voipex.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _events = MutableSharedFlow<MainEvent>()
    val events: SharedFlow<MainEvent> = _events

    init {
        refreshUsers()
    }

    fun refreshUsers() {
        viewModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading
                // TODO: Implement user fetching logic
                _uiState.value = MainUiState.Success(emptyList())
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // TODO: Implement logout logic
                _events.emit(MainEvent.NavigateToLogin)
            } catch (e: Exception) {
                _events.emit(MainEvent.ShowError(e.message ?: "Failed to logout"))
            }
        }
    }
} 