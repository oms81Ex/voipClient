// File: presentation/ui/auth/LoginActivity.kt
package com.voipex.android.presentation.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.voipex.android.databinding.ActivityLoginBinding
import com.voipex.android.presentation.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.apply {
            btnLogin.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()
                
                if (validateInput(email, password)) {
                    viewModel.login(email, password)
                }
            }
            
            tvRegister.setOnClickListener {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            }
        }
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true
        
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
            viewModel.loginState.collectLatest { state ->
                when (state) {
                    is LoginState.Loading -> showLoading(true)
                    is LoginState.Success -> {
                        showLoading(false)
                        navigateToMain()
                    }
                    is LoginState.Error -> {
                        showLoading(false)
                        showError(state.message)
                    }
                    is LoginState.Idle -> showLoading(false)
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !isLoading
        }
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// File: presentation/ui/auth/LoginViewModel.kt
package com.voipex.android.presentation.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voipex.android.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val result = loginUseCase(email, password)
                _loginState.value = LoginState.Success(result)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Login failed")
            }
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val authResponse: com.voipex.android.data.models.AuthResponse) : LoginState()
    data class Error(val message: String) : LoginState()
}

// File: domain/usecase/LoginUseCase.kt
package com.voipex.android.domain.usecase

import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): AuthResponse {
        return authRepository.login(email, password)
    }
}

// File: data/repository/AuthRepository.kt
package com.voipex.android.data.repository

import com.voipex.android.data.api.AuthApi
import com.voipex.android.data.local.PreferencesManager
import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.models.LoginRequest
import com.voipex.android.data.models.RegisterRequest
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun login(email: String, password: String): AuthResponse {
        val response = authApi.login(LoginRequest(email, password))
        preferencesManager.saveAuthToken(response.token)
        preferencesManager.saveUser(response.user)
        preferencesManager.setLoggedIn(true)
        return response
    }
    
    suspend fun register(email: String, password: String, name: String): AuthResponse {
        val response = authApi.register(RegisterRequest(email, password, name))
        preferencesManager.saveAuthToken(response.token)
        preferencesManager.saveUser(response.user)
        preferencesManager.setLoggedIn(true)
        return response
    }
    
    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: Exception) {
            // Ignore network errors during logout
        } finally {
            preferencesManager.clearAll()
        }
    }
}