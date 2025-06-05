package com.ntdt.voipex.ui.call

import androidx.lifecycle.ViewModel
import com.ntdt.voipex.webrtc.WebRTCManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val webRTCManager: WebRTCManager
) : ViewModel() {
    fun initializeCall(userId: String, isIncoming: Boolean, callId: String) {
        // TODO: Initialize WebRTC call
    }

    fun endCall() {
        // TODO: End WebRTC call
    }

    fun cleanup() {
        // TODO: Clean up WebRTC resources
    }
} 