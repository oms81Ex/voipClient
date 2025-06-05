package com.ntdt.voipex.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar

object ErrorUtils {
    fun showError(view: View, message: String, duration: Int = Snackbar.LENGTH_LONG) {
        Snackbar.make(view, message, duration).apply {
            setAction("Dismiss") {
                dismiss()
            }
        }.show()
    }

    fun showError(
        view: View,
        message: String,
        actionText: String,
        action: () -> Unit,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        Snackbar.make(view, message, duration).apply {
            setAction(actionText) {
                action()
                dismiss()
            }
        }.show()
    }
} 