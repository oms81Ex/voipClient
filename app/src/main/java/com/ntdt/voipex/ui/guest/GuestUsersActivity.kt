package com.ntdt.voipex.ui.guest

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntdt.voipex.databinding.ActivityGuestUsersBinding
import com.ntdt.voipex.ui.call.CallActivity
import com.ntdt.voipex.domain.model.GuestUser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import javax.inject.Inject

@AndroidEntryPoint
class GuestUsersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuestUsersBinding
    private val viewModel: GuestUsersViewModel by viewModels()
    private lateinit var adapter: GuestUsersAdapter
    
    @Inject
    lateinit var guestSignalingClient: GuestSignalingClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuestUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        connectToSignalingServer()
        
        viewModel.loadGuestUsers()
    }
    
    private fun connectToSignalingServer() {
        val prefs = getSharedPreferences("VoipExPrefs", MODE_PRIVATE)
        val guestId = prefs.getString("guest_id", "") ?: ""
        val guestName = prefs.getString("guest_name", "") ?: ""
        
        if (guestId.isNotEmpty() && guestName.isNotEmpty()) {
            val guestUser = GuestUser(id = guestId, name = guestName)
            guestSignalingClient.connect(guestUser)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "온라인 게스트 사용자"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = GuestUsersAdapter { guestUser ->
            // 선택한 게스트 사용자와 통화 시작
            initiateCall(guestUser)
        }
        
        binding.rvGuestUsers.apply {
            layoutManager = LinearLayoutManager(this@GuestUsersActivity)
            adapter = this@GuestUsersActivity.adapter
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is GuestUsersUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.rvGuestUsers.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                    }
                    is GuestUsersUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        
                        if (state.users.isEmpty()) {
                            binding.rvGuestUsers.visibility = View.GONE
                            binding.tvEmptyState.visibility = View.VISIBLE
                        } else {
                            binding.rvGuestUsers.visibility = View.VISIBLE
                            binding.tvEmptyState.visibility = View.GONE
                            adapter.submitList(state.users)
                        }
                    }
                    is GuestUsersUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvGuestUsers.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = "오류: ${state.message}"
                    }
                }
            }
        }
        
        // 실시간 게스트 사용자 업데이트 관찰
        lifecycleScope.launch {
            viewModel.guestUserUpdates.collect { update ->
                when (update) {
                    is GuestUserUpdate.UserJoined -> {
                        adapter.addUser(update.user)
                    }
                    is GuestUserUpdate.UserLeft -> {
                        adapter.removeUser(update.userId)
                    }
                }
            }
        }
    }

    private fun initiateCall(guestUser: GuestUser) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("CALLEE_ID", guestUser.id)
            putExtra("CALLEE_NAME", guestUser.name)
            putExtra("IS_OUTGOING", true)
            putExtra("CALL_TYPE", "audio") // 기본적으로 음성 통화
            putExtra("IS_GUEST_CALL", true)
        }
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        guestSignalingClient.disconnect()
    }
}