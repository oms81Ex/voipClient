// File: presentation/ui/auth/RegisterActivity.kt
package com.voipex.android.presentation.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.voipex.android.databinding.ActivityRegisterBinding
import com.voipex.android.presentation.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.apply {
            btnRegister.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val name = etName.text.toString().trim()
                
                if (validateInput(email, password, name)) {
                    viewModel.register(email, password, name)
                }
            }
            
            tvLogin.setOnClickListener {
                finish()
            }
        }
    }
    
    private fun validateInput(email: String, password: String, name: String): Boolean {
        var isValid = true
        
        if (name.isEmpty()) {
            binding.tilName.error = "Name is required"
            isValid = false
        } else {
            binding.tilName.error = null
        }
        
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Invalid email format"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }
        
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }
        
        return isValid
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.registerState.collectLatest { state ->
                when (state) {
                    is RegisterState.Loading -> showLoading(true)
                    is RegisterState.Success -> {
                        showLoading(false)
                        navigateToMain()
                    }
                    is RegisterState.Error -> {
                        showLoading(false)
                        showError(state.message)
                    }
                    is RegisterState.Idle -> showLoading(false)
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnRegister.isEnabled = !isLoading
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }
}

// File: presentation/ui/auth/RegisterViewModel.kt
package com.voipex.android.presentation.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voipex.android.data.models.AuthResponse
import com.voipex.android.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {
    
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState
    
    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            try {
                val result = registerUseCase(email, password, name)
                _registerState.value = RegisterState.Success(result)
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error(e.message ?: "Registration failed")
            }
        }
    }
}

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val authResponse: AuthResponse) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

// File: domain/usecase/RegisterUseCase.kt
package com.voipex.android.domain.usecase

import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, name: String): AuthResponse {
        return authRepository.register(email, password, name)
    }
}

// File: presentation/ui/call/IncomingCallActivity.kt
package com.voipex.android.presentation.ui.call

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.voipex.android.data.models.CallType
import com.voipex.android.data.models.User
import com.voipex.android.databinding.ActivityIncomingCallBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IncomingCallActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityIncomingCallBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    
    private var callerUser: User? = null
    private var callType: CallType = CallType.AUDIO
    private var roomId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show on lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        extractIntentData()
        setupUI()
        startRinging()
    }
    
    private fun extractIntentData() {
        callerUser = intent.getParcelableExtra("caller")
        callType = CallType.valueOf(intent.getStringExtra("callType") ?: "AUDIO")
        roomId = intent.getStringExtra("roomId")
    }
    
    private fun setupUI() {
        binding.apply {
            tvCallerName.text = callerUser?.name ?: "Unknown"
            tvCallType.text = if (callType == CallType.VIDEO) "Video Call" else "Voice Call"
            
            btnAccept.setOnClickListener {
                acceptCall()
            }
            
            btnDecline.setOnClickListener {
                declineCall()
            }
        }
    }
    
    private fun startRinging() {
        // Play ringtone
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
        ringtone?.play()
        
        // Vibrate
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 1000, 1000)
        vibrator?.vibrate(pattern, 0)
    }
    
    private fun stopRinging() {
        ringtone?.stop()
        vibrator?.cancel()
    }
    
    private fun acceptCall() {
        stopRinging()
        
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("user", callerUser)
            putExtra("callType", callType.name)
            putExtra("isIncoming", true)
            putExtra("roomId", roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
    
    private fun declineCall() {
        stopRinging()
        // TODO: Send decline signal to server
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }
}

// File: service/VoipExFirebaseMessagingService.kt
package com.voipex.android.service

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.voipex.android.data.local.PreferencesManager
import com.voipex.android.data.models.CallType
import com.voipex.android.data.models.User
import com.voipex.android.presentation.ui.call.IncomingCallActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class VoipExFirebaseMessagingService : FirebaseMessagingService() {
    
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var notificationManager: CallNotificationManager
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM Token: $token")
        // TODO: Send token to server
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val data = message.data
        val type = data["type"]
        
        when (type) {
            "incoming_call" -> handleIncomingCall(data)
            else -> Timber.d("Unknown message type: $type")
        }
    }
    
    private fun handleIncomingCall(data: Map<String, String>) {
        val callerId = data["callerId"] ?: return
        val callerName = data["callerName"] ?: "Unknown"
        val callType = data["callType"] ?: "audio"
        val roomId = data["roomId"] ?: return
        
        val caller = User(
            id = callerId,
            name = callerName,
            email = "",
            isOnline = true
        )
        
        // Show incoming call UI
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("caller", caller)
            putExtra("callType", callType.uppercase())
            putExtra("roomId", roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        startActivity(intent)
        
        // Also show notification
        val notification = notificationManager.createIncomingCallNotification(
            callerName,
            if (callType == "video") "Video" else "Voice"
        )
        notificationManager.notify(2001, notification)
    }
}