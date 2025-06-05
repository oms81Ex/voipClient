// File: presentation/ui/main/MainActivity.kt
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
import com.voipex.android.data.models.CallType
import com.voipex.android.data.models.User
import com.voipex.android.databinding.ActivityMainBinding
import com.voipex.android.presentation.ui.auth.LoginActivity
import com.voipex.android.presentation.ui.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var contactsAdapter: ContactsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        setupRecyclerView()
        observeViewModel()
        
        viewModel.loadContacts()
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

// File: presentation/ui/main/MainViewModel.kt
package com.voipex.android.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voipex.android.data.models.User
import com.voipex.android.data.repository.AuthRepository
import com.voipex.android.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _contactsState = MutableStateFlow<ContactsState>(ContactsState.Loading)
    val contactsState: StateFlow<ContactsState> = _contactsState
    
    fun loadContacts() {
        viewModelScope.launch {
            try {
                _contactsState.value = ContactsState.Loading
                val contacts = userRepository.getContacts()
                _contactsState.value = ContactsState.Success(contacts)
            } catch (e: Exception) {
                _contactsState.value = ContactsState.Error(e.message ?: "Failed to load contacts")
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}

sealed class ContactsState {
    object Loading : ContactsState()
    data class Success(val contacts: List<User>) : ContactsState()
    data class Error(val message: String) : ContactsState()
}

// File: presentation/ui/main/ContactsAdapter.kt
package com.voipex.android.presentation.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voipex.android.data.models.User
import com.voipex.android.databinding.ItemContactBinding

class ContactsAdapter(
    private val onItemClick: (User) -> Unit
) : ListAdapter<User, ContactsAdapter.ContactViewHolder>(UserDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(user: User) {
            binding.apply {
                tvName.text = user.name
                tvEmail.text = user.email
                ivStatus.setColorFilter(
                    if (user.isOnline) android.graphics.Color.GREEN 
                    else android.graphics.Color.GRAY
                )
                
                root.setOnClickListener {
                    onItemClick(user)
                }
            }
        }
    }
}

class UserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}

// File: data/repository/UserRepository.kt
package com.voipex.android.data.repository

import com.voipex.android.data.api.UserApi
import com.voipex.android.data.models.User
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val userApi: UserApi
) {
    suspend fun getProfile(): User {
        return userApi.getProfile()
    }
    
    suspend fun updateProfile(user: User): User {
        return userApi.updateProfile(user)
    }
    
    suspend fun searchUsers(query: String): List<User> {
        return userApi.searchUsers(query)
    }
    
    suspend fun getContacts(): List<User> {
        return userApi.getContacts()
    }
}