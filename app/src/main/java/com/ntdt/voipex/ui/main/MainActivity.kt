package com.ntdt.voipex.ui.main

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntdt.voipex.R
import com.ntdt.voipex.databinding.ActivityMainBinding
import com.ntdt.voipex.ui.auth.LoginActivity
import com.ntdt.voipex.ui.call.CallActivity
import com.ntdt.voipex.ui.contacts.ContactsAdapter
import com.ntdt.voipex.utils.ErrorUtils
import com.ntdt.voipex.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var contactsAdapter: ContactsAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showContent()
        } else {
            showPermissionError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupLogoutButton()
        observeViewModel()
        if (PermissionUtils.hasRequiredPermissions(this)) {
            showContent()
        } else {
            requestPermissions()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.app_name)
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { user ->
            startCall(user.id)
        }
        binding.contactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }
    }

    private fun setupLogoutButton() {
        binding.logoutFab.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MainUiState.Loading -> showLoading()
                    is MainUiState.Success -> {
                        hideLoading()
                        if (state.contacts.isEmpty()) {
                            showEmptyState()
                        } else {
                            hideEmptyState()
                            contactsAdapter.submitList(state.contacts)
                        }
                    }
                    is MainUiState.Error -> {
                        hideLoading()
                        showError(state.message)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is MainEvent.NavigateToLogin -> {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                    is MainEvent.ShowError -> {
                        showError(event.message)
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        ErrorUtils.showError(
            view = binding.root,
            message = message,
            actionText = "Retry",
            action = {
                viewModel.refreshUsers()
            }
        )
    }

    private fun showContent() {
        binding.permissionErrorText.isVisible = false
        binding.contentLayout.isVisible = true
        
        // TODO: Set up RecyclerView and observe ViewModel
    }

    private fun showPermissionError() {
        binding.permissionErrorText.isVisible = true
        binding.contentLayout.isVisible = false
    }

    private fun requestPermissions() {
        permissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }

    private fun startCall(userId: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_USER_ID, userId)
            putExtra(CallActivity.EXTRA_IS_INCOMING, false)
            putExtra(CallActivity.EXTRA_CALL_ID, System.currentTimeMillis().toString())
        }
        startActivity(intent)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contactsRecyclerView.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.contactsRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.contactsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.tvEmptyState.visibility = View.GONE
        binding.contactsRecyclerView.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_call_history -> {
                // TODO: Navigate to call history
                true
            }
            R.id.action_settings -> {
                // TODO: Navigate to settings
                true
            }
            R.id.action_logout -> {
                viewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 
