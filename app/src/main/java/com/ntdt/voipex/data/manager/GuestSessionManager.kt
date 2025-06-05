package com.ntdt.voipex.data.manager

import com.ntdt.voipex.data.local.PreferencesManager
import com.ntdt.voipex.data.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuestSessionManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private var currentGuestUser: User? = null
    private val guestUsers = mutableMapOf<String, User>()
    
    private val _guestUsersFlow = MutableStateFlow<List<User>>(emptyList())
    val guestUsersFlow: StateFlow<List<User>> = _guestUsersFlow

    fun createGuestSession(name: String): User {
        val guestId = "guest_${System.currentTimeMillis()}"
        val guestUser = User(id = guestId, name = name, email = null, isGuest = true)
        currentGuestUser = guestUser
        guestUsers[guestId] = guestUser
        updateGuestUsersList()
        return guestUser
    }

    fun getCurrentGuestUser(): User? = currentGuestUser

    fun getAllGuestUsers(): List<User> = guestUsers.values.toList()

    fun addGuestUser(guestUser: User) {
        if (guestUser.isGuest) {
            guestUsers[guestUser.id] = guestUser
            updateGuestUsersList()
        }
    }

    fun removeGuestUser(guestId: String) {
        guestUsers.remove(guestId)
        updateGuestUsersList()
    }

    fun clearSession() {
        if (isGuest) {
            guestUsers.remove(currentGuestUser?.id)
            currentGuestUser = null
            preferencesManager.clearUserData()
            updateGuestUsersList()
        }
    }

    val isGuest: Boolean
        get() = preferencesManager.userId?.startsWith("guest_") == true

    private fun updateGuestUsersList() {
        _guestUsersFlow.value = guestUsers.values.toList()
    }
} 