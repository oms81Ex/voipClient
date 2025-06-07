package com.ntdt.voipex.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject

class PreferencesManager @Inject constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_AUTH_TOKEN, value) }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit { putString(KEY_USER_NAME, value) }
    
    var isGuest: Boolean
        get() = prefs.getBoolean(KEY_IS_GUEST, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_GUEST, value) }

    fun saveUserData(userId: String, userName: String, token: String, isGuestUser: Boolean = false) {
        prefs.edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_AUTH_TOKEN, token)
            putBoolean(KEY_IS_GUEST, isGuestUser)
        }
    }

    fun clearUserData() {
        prefs.edit {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_IS_GUEST)
        }
    }

    companion object {
        private const val PREFS_NAME = "VoipExPrefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_GUEST = "is_guest"
    }
} 