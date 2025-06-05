// File: presentation/ui/auth/LoginActivity.kt (Updated)
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
            
            btnGuestMode.setOnClickListener {
                viewModel.loginAsGuest()
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
            btnGuestMode.isEnabled = !isLoading
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

// File: presentation/ui/auth/LoginViewModel.kt (Updated)
package com.voipex.android.presentation.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voipex.android.domain.usecase.LoginAsGuestUseCase
import com.voipex.android.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val loginAsGuestUseCase: LoginAsGuestUseCase
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
    
    fun loginAsGuest() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val result = loginAsGuestUseCase()
                _loginState.value = LoginState.Success(result)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Guest login failed")
            }
        }
    }
}

// File: domain/usecase/LoginAsGuestUseCase.kt
package com.voipex.android.domain.usecase

import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.repository.AuthRepository
import javax.inject.Inject

class LoginAsGuestUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AuthResponse {
        return authRepository.loginAsGuest()
    }
}

// File: data/repository/AuthRepository.kt (Updated)
package com.voipex.android.data.repository

import com.voipex.android.data.api.AuthApi
import com.voipex.android.data.local.PreferencesManager
import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.models.LoginRequest
import com.voipex.android.data.models.RegisterRequest
import com.voipex.android.data.models.User
import java.util.UUID
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun login(email: String, password: String): AuthResponse {
        val response = authApi.login(LoginRequest(email, password))
        saveAuthData(response)
        return response
    }
    
    suspend fun register(email: String, password: String, name: String): AuthResponse {
        val response = authApi.register(RegisterRequest(email, password, name))
        saveAuthData(response)
        return response
    }
    
    suspend fun loginAsGuest(): AuthResponse {
        val response = authApi.loginAsGuest()
        // Guest 사용자 정보 보강
        val guestUser = response.user.copy(
            name = response.user.name.ifEmpty { "Guest_${UUID.randomUUID().toString().substring(0, 8)}" }
        )
        val updatedResponse = response.copy(user = guestUser)
        saveAuthData(updatedResponse, isGuest = true)
        return updatedResponse
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
    
    private fun saveAuthData(response: AuthResponse, isGuest: Boolean = false) {
        preferencesManager.saveAuthToken(response.token)
        preferencesManager.saveUser(response.user)
        preferencesManager.setLoggedIn(true)
        preferencesManager.setGuestMode(isGuest)
    }
}

// File: data/api/AuthApi.kt (Updated)
package com.voipex.android.data.api

import com.voipex.android.data.models.AuthResponse
import com.voipex.android.data.models.LoginRequest
import com.voipex.android.data.models.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse
    
    @POST("auth/guest")
    suspend fun loginAsGuest(): AuthResponse
    
    @POST("auth/logout")
    suspend fun logout()
}

// File: data/local/PreferencesManager.kt (Updated)
package com.voipex.android.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.voipex.android.data.models.User
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        "voipex_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val sharedPreferences = context.getSharedPreferences(
        "voipex_prefs",
        Context.MODE_PRIVATE
    )
    
    private val gson = Gson()
    
    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER = "user"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_GUEST = "is_guest"
    }
    
    fun saveAuthToken(token: String) {
        encryptedSharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }
    
    fun getAuthToken(): String? {
        return encryptedSharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }
    
    fun saveUser(user: User) {
        sharedPreferences.edit()
            .putString(KEY_USER, gson.toJson(user))
            .apply()
    }
    
    fun getUser(): User? {
        val userJson = sharedPreferences.getString(KEY_USER, null)
        return if (userJson != null) {
            gson.fromJson(userJson, User::class.java)
        } else {
            null
        }
    }
    
    fun setLoggedIn(isLoggedIn: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            .apply()
    }
    
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun setGuestMode(isGuest: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_GUEST, isGuest)
            .apply()
    }
    
    fun isGuestMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_GUEST, false)
    }
    
    fun clearAll() {
        encryptedSharedPreferences.edit().clear().apply()
        sharedPreferences.edit().clear().apply()
    }
}

// File: presentation/ui/main/MainActivity.kt (Updated)
package com.voipex.android.presentation.ui.main

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.permissionx.guolindev.PermissionX
import com.voipex.android.R
import com.voipex.android.data.local.PreferencesManager
import com.voipex.android.data.models.CallType
import com.voipex.android.data.models.User
import com.voipex.android.databinding.ActivityMainBinding
import com.voipex.android.presentation.ui.auth.LoginActivity
import com.voipex.android.presentation.ui.call.CallActivity
import com.voipex.android.presentation.ui.call.DirectCallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject lateinit var preferencesManager: PreferencesManager
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var contactsAdapter: ContactsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
        
        // 게스트 모드인 경우 UI 조정
        if (preferencesManager.isGuestMode()) {
            supportActionBar?.title = "VoipEx - Guest Mode"
            binding.fabAddContact.hide()
        } else {
            viewModel.loadContacts()
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // 게스트 모드에서는 Direct Call 버튼 표시
            if (preferencesManager.isGuestMode()) {
                fabDirectCall.show()
                fabDirectCall.setOnClickListener {
                    showDirectCallDialog()
                }
            } else {
                fabAddContact.setOnClickListener {
                    // 연락처 추가 기능
                }
            }
        }
    }
    
    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { user ->
            showCallOptionsDialog(user)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.contactsState.collectLatest { state ->
                when (state) {
                    is ContactsState.Loading -> {
                        // Show loading
                    }
                    is ContactsState.Success -> {
                        contactsAdapter.submitList(state.contacts)
                    }
                    is ContactsState.Error -> {
                        // Show error
                    }
                }
            }
        }
    }
    
    private fun showDirectCallDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_direct_call, null)
        val etUserId = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUserId)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Direct Call")
            .setMessage("Enter the user ID to call")
            .setView(dialogView)
            .setPositiveButton("Call") { _, _ ->
                val userId = etUserId.text.toString().trim()
                if (userId.isNotEmpty()) {
                    showCallTypeDialog(userId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCallTypeDialog(userId: String) {
        val options = arrayOf("Voice Call", "Video Call")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Call Type")
            .setItems(options) { _, which ->
                val callType = if (which == 0) CallType.AUDIO else CallType.VIDEO
                checkPermissionsAndDirectCall(userId, callType)
            }
            .show()
    }
    
    private fun checkPermissionsAndDirectCall(userId: String, callType: CallType) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        if (callType == CallType.VIDEO) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        PermissionX.init(this)
            .permissions(permissions)
            .request { allGranted, _, _ ->
                if (allGranted) {
                    startDirectCall(userId, callType)
                } else {
                    // Show permission denied message
                }
            }
    }
    
    private fun startDirectCall(userId: String, callType: CallType) {
        val intent = Intent(this, DirectCallActivity::class.java).apply {
            putExtra("targetUserId", userId)
            putExtra("callType", callType.name)
        }
        startActivity(intent)
    }
    
    private fun showCallOptionsDialog(user: User) {
        val options = arrayOf("Voice Call", "Video Call")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Call ${user.name}")
            .setItems(options) { _, which ->
                val callType = if (which == 0) CallType.AUDIO else CallType.VIDEO
                checkPermissionsAndCall(user, callType)
            }
            .show()
    }
    
    private fun checkPermissionsAndCall(user: User, callType: CallType) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        if (callType == CallType.VIDEO) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        PermissionX.init(this)
            .permissions(permissions)
            .request { allGranted, _, _ ->
                if (allGranted) {
                    startCall(user, callType)
                } else {
                    // Show permission denied message
                }
            }
    }
    
    private fun startCall(user: User, callType: CallType) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("user", user)
            putExtra("callType", callType.name)
            putExtra("isIncoming", false)
        }
        startActivity(intent)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun logout() {
        viewModel.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}