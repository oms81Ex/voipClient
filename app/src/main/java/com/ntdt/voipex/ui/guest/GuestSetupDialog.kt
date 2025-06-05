package com.ntdt.voipex.ui.guest

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ntdt.voipex.databinding.DialogGuestSetupBinding

class GuestSetupDialog : DialogFragment() {
    private var _binding: DialogGuestSetupBinding? = null
    private val binding get() = _binding!!

    var onGuestNameConfirmed: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogGuestSetupBinding.inflate(layoutInflater)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enter Guest Name")
            .setView(binding.root)
            .setPositiveButton("Continue") { _, _ ->
                val guestName = binding.etGuestName.text.toString().trim()
                if (guestName.isNotEmpty()) {
                    onGuestNameConfirmed?.invoke(guestName)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GuestSetupDialog"
    }
} 