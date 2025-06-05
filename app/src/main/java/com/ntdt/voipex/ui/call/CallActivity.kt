package com.ntdt.voipex.ui.call

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ntdt.voipex.databinding.ActivityCallBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        const val EXTRA_CALL_ID = "extra_call_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra(EXTRA_USER_ID)
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        val callId = intent.getStringExtra(EXTRA_CALL_ID)

        if (userId == null || callId == null) {
            finish()
            return
        }

        setupUI()
        observeViewModel()
        viewModel.initializeCall(userId, isIncoming, callId)
    }

    private fun setupUI() {
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall()
            finish()
        }
    }

    private fun observeViewModel() {
        // TODO: Implement call state observation
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cleanup()
    }
} 