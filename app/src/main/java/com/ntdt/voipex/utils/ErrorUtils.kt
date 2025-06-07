package com.ntdt.voipex.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import retrofit2.Response

object ErrorUtils {
    fun parseError(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            errorBody ?: "Unknown error occurred"
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }
    
    fun showError(
        view: View,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        
        snackbar.show()
    }
}