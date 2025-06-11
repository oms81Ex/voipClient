package com.ntdt.voipex.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ntdt.voipex.data.api.UserApi
import com.ntdt.voipex.data.model.User
import com.ntdt.voipex.data.repository.AuthRepository
import com.ntdt.voipex.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import javax.inject.Inject
import com.ntdt.voipex.data.models.User as UserModel

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val logoutUseCase: LogoutUseCase,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {
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
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("VoipExPrefs", Context.MODE_PRIVATE)
                val token = prefs.getString("auth_token", null)
                val isGuest = prefs.getBoolean("is_guest", false)
                val guestId = prefs.getString("guest_id", null)
                
                if (token != null) {
                    // 로그아웃 API 호출
                    logoutUseCase(token)
                    
                    // 게스트 사용자인 경우 삭제
                    if (isGuest && guestId != null) {
                        deleteGuestUser(guestId, token)
                    }
                }
                
                // SharedPreferences 클리어
                prefs.edit().clear().apply()
                
                // 로그인 화면으로 이동
                _events.emit(MainEvent.NavigateToLogin)
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                // 에러가 발생해도 로그인 화면으로 이동
                val prefs = getApplication<Application>().getSharedPreferences("VoipExPrefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                _events.emit(MainEvent.NavigateToLogin)
            }
        }
    }
    
    private suspend fun deleteGuestUser(guestId: String, token: String) {
        try {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(request)
                }
                .build()
                
            val retrofit = Retrofit.Builder()
                .baseUrl("http://10.47.16.163:3000/api/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                
            val authApi = retrofit.create(com.ntdt.voipex.data.api.AuthApi::class.java)
            
            // 게스트 사용자 삭제 API 호출
            val response = authApi.deleteGuest(guestId)
            if (response.isSuccessful) {
                Timber.d("Guest user deleted successfully: $guestId")
            } else {
                Timber.e("Failed to delete guest user: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete guest user")
        }
    }
    
    fun connectAsGuest(guestUser: UserModel, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // 게스트용 임시 토큰 요청
                val guestToken = authRepository.requestGuestToken(guestUser)
                
                if (guestToken != null) {
                    // SharedPreferences에 게스트 토큰 저장
                    val context = getApplication<Application>()
                    val prefs = context.getSharedPreferences("VoipExPrefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("auth_token", guestToken)
                        putBoolean("is_guest_mode", true)
                        apply()
                    }
                    
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect as guest")
                callback(false)
            }
        }
    }
}