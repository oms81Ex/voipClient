package com.ntdt.voipex.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ntdt.voipex.databinding.ActivityLoginBinding
import com.ntdt.voipex.ui.guest.GuestSetupDialog
import com.ntdt.voipex.ui.main.MainActivity
import com.ntdt.voipex.utils.ErrorUtils
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

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        binding.btnGuestMode.setOnClickListener {
            showGuestSetupDialog()
        }

        binding.tvRegister.setOnClickListener {
            // TODO: Navigate to register screen
        }
    }

    private fun showGuestSetupDialog() {
        GuestSetupDialog().apply {
            onGuestNameConfirmed = { name ->
                viewModel.loginAsGuest(name)
            }
        }.show(supportFragmentManager, GuestSetupDialog.TAG)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is LoginUiState.Loading -> showLoading()
                    is LoginUiState.Success -> {
                        hideLoading()
                        navigateToMain()
                    }
                    is LoginUiState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                    LoginUiState.Initial -> hideLoading()
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.btnGuestMode.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        binding.btnGuestMode.isEnabled = true
    }

    private fun showError(message: String) {
        ErrorUtils.showError(
            view = binding.root,
            message = message,
            actionText = "Retry",
            action = {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    viewModel.login(email, password)
                }
            }
        )
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 