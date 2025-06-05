// File: data/local/PreferencesManager.kt
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
    
    fun clearAll() {
        encryptedSharedPreferences.edit().clear().apply()
        sharedPreferences.edit().clear().apply()
    }
}