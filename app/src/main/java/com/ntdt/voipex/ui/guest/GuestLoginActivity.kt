package com.ntdt.voipex.ui.guest

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ntdt.voipex.databinding.ActivityGuestLoginBinding
import com.ntdt.voipex.ui.main.MainActivity
import com.ntdt.voipex.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GuestLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuestLoginBinding
    private val viewModel: GuestLoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuestLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnEnterAsGuest.setOnClickListener {
            val guestName = binding.etGuestName.text.toString().trim()
            if (guestName.isEmpty()) {
                Toast.makeText(this, "게스트 이름을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.loginAsGuest(guestName)
        }

        binding.btnLogin.setOnClickListener {
            // 일반 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is GuestLoginUiState.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.btnEnterAsGuest.isEnabled = false
                    }
                    is GuestLoginUiState.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.btnEnterAsGuest.isEnabled = true
                        
                        // MainActivity로 이동
                        val intent = Intent(this@GuestLoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    is GuestLoginUiState.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.btnEnterAsGuest.isEnabled = true
                        Toast.makeText(this@GuestLoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is GuestLoginUiState.Idle -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.btnEnterAsGuest.isEnabled = true
                    }
                }
            }
        }
    }
}