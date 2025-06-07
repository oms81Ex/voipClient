package com.ntdt.voipex.ui.guest

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntdt.voipex.databinding.FragmentGuestUsersBinding
import com.ntdt.voipex.domain.model.GuestUser
import com.ntdt.voipex.ui.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GuestUsersFragment : Fragment() {
    private var _binding: FragmentGuestUsersBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GuestUsersViewModel by viewModels()
    private lateinit var adapter: GuestUsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuestUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        
        viewModel.loadGuestUsers()
    }

    private fun setupRecyclerView() {
        adapter = GuestUsersAdapter { guestUser ->
            initiateCall(guestUser)
        }
        
        binding.rvGuestUsers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GuestUsersFragment.adapter
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
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
        
        viewLifecycleOwner.lifecycleScope.launch {
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
        val intent = Intent(requireContext(), CallActivity::class.java).apply {
            putExtra("CALLEE_ID", guestUser.id)
            putExtra("CALLEE_NAME", guestUser.name)
            putExtra("IS_OUTGOING", true)
            putExtra("CALL_TYPE", "audio")
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}