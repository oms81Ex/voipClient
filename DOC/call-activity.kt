// File: presentation/ui/call/CallActivity.kt
package com.voipex.android.presentation.ui.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voipex.android.data.models.CallType
import com.voipex.android.data.models.User
import com.voipex.android.databinding.ActivityCallBinding
import com.voipex.android.service.CallService
import com.voipex.android.webrtc.PeerConnectionObserver
import com.voipex.android.webrtc.SimpleSdpObserver
import com.voipex.android.webrtc.WebRTCManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CallActivity : AppCompatActivity() {
    
    @Inject lateinit var webRTCManager: WebRTCManager
    
    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()
    
    private lateinit var audioManager: AudioManager
    private var remoteUser: User? = null
    private var callType: CallType = CallType.AUDIO
    private var isIncoming: Boolean = false
    
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var callDurationRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during call
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        extractIntentData()
        setupUI()
        observeViewModel()
        
        if (!isIncoming) {
            viewModel.initiateCall(remoteUser!!.id, callType)
        }
        
        startCallService()
    }
    
    private fun extractIntentData() {
        remoteUser = intent.getParcelableExtra("user")
        callType = CallType.valueOf(intent.getStringExtra("callType") ?: "AUDIO")
        isIncoming = intent.getBooleanExtra("isIncoming", false)
    }
    
    private fun setupUI() {
        binding.apply {
            tvRemoteUserName.text = remoteUser?.name ?: "Unknown"
            
            // Show/hide video views based on call type
            if (callType == CallType.VIDEO) {
                localVideoView.visibility = View.VISIBLE
                remoteVideoView.visibility = View.VISIBLE
                ivRemoteUserAvatar.visibility = View.GONE
            } else {
                localVideoView.visibility = View.GONE
                remoteVideoView.visibility = View.GONE
                ivRemoteUserAvatar.visibility = View.VISIBLE
            }
            
            // Control buttons
            btnMute.setOnClickListener {
                viewModel.toggleMute()
            }
            
            btnVideo.setOnClickListener {
                viewModel.toggleVideo()
            }
            
            btnSpeaker.setOnClickListener {
                viewModel.toggleSpeaker()
            }
            
            btnSwitchCamera.setOnClickListener {
                webRTCManager.switchCamera()
            }
            
            btnEndCall.setOnClickListener {
                endCall()
            }
            
            // Hide video-specific buttons for audio calls
            if (callType == CallType.AUDIO) {
                btnVideo.visibility = View.GONE
                btnSwitchCamera.visibility = View.GONE
            }
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.callState.collectLatest { state ->
                updateUI(state)
            }
        }
        
        lifecycleScope.launch {
            viewModel.webRTCEvents.collect { event ->
                handleWebRTCEvent(event)
            }
        }
    }
    
    private fun updateUI(state: com.voipex.android.data.models.CallState) {
        binding.apply {
            // Update mute button
            btnMute.isSelected = state.isMuted
            btnMute.setImageResource(
                if (state.isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
            
            // Update video button
            btnVideo.isSelected = !state.isVideoEnabled
            
            // Update speaker button
            btnSpeaker.isSelected = state.isSpeakerOn
            
            // Update call duration
            if (state.isInCall && state.callDuration > 0) {
                val minutes = state.callDuration / 60
                val seconds = state.callDuration % 60
                tvCallDuration.text = String.format("%02d:%02d", minutes, seconds)
            }
        }
        
        // Handle speaker
        audioManager.isSpeakerphoneOn = state.isSpeakerOn
    }
    
    private fun handleWebRTCEvent(event: WebRTCEvent) {
        when (event) {
            is WebRTCEvent.CallConnected -> {
                startCallTimer()
                binding.tvCallStatus.text = "Connected"
            }
            is WebRTCEvent.RemoteStreamAdded -> {
                if (callType == CallType.VIDEO) {
                    webRTCManager.attachRemoteRenderer(binding.remoteVideoView, event.stream)
                }
            }
            is WebRTCEvent.Error -> {
                showError(event.message)
                finish()
            }
            is WebRTCEvent.CallEnded -> {
                finish()
            }
        }
    }
    
    private fun startCallTimer() {
        callDurationRunnable = object : Runnable {
            override fun run() {
                viewModel.incrementCallDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
        }
        callDurationHandler.post(callDurationRunnable!!)
    }
    
    private fun startCallService() {
        val serviceIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_START_CALL
            putExtra("remoteUserName", remoteUser?.name)
        }
        startService(serviceIntent)
    }
    
    private fun endCall() {
        viewModel.endCall()
        stopCallService()
        finish()
    }
    
    private fun stopCallService() {
        val serviceIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_END_CALL
        }
        startService(serviceIntent)
    }
    
    private fun showError(message: String) {
        // Show error dialog or toast
        Timber.e("Call error: $message")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        callDurationRunnable?.let { callDurationHandler.removeCallbacks(it) }
        viewModel.cleanup()
        stopCallService()
    }
}

// File: presentation/ui/call/CallViewModel.kt
package com.voipex.android.presentation.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voipex.android.data.local.PreferencesManager
import com.voipex.android.data.models.CallState
import com.voipex.android.data.models.CallType
import com.voipex.android.data.repository.CallRepository
import com.voipex.android.webrtc.PeerConnectionObserver
import com.voipex.android.webrtc.SignallingClient
import com.voipex.android.webrtc.SimpleSdpObserver
import com.voipex.android.webrtc.WebRTCManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val webRTCManager: WebRTCManager,
    private val signallingClient: SignallingClient,
    private val callRepository: CallRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState
    
    private val _webRTCEvents = MutableSharedFlow<WebRTCEvent>()
    val webRTCEvents: SharedFlow<WebRTCEvent> = _webRTCEvents
    
    private var peerConnection: PeerConnection? = null
    private var currentRoomId: String? = null
    private var targetUserId: String? = null
    
    init {
        setupSignallingCallbacks()
        connectToSignallingServer()
    }
    
    private fun connectToSignallingServer() {
        val token = preferencesManager.getAuthToken() ?: return
        signallingClient.connect(token)
    }
    
    private fun setupSignallingCallbacks() {
        signallingClient.apply {
            onConnected = {
                Timber.d("Signalling connected")
            }
            
            onOfferReceived = { offer, userId ->
                handleOfferReceived(offer, userId)
            }
            
            onAnswerReceived = { answer, userId ->
                handleAnswerReceived(answer, userId)
            }
            
            onIceCandidateReceived = { candidate, userId ->
                handleIceCandidateReceived(candidate, userId)
            }
            
            onUserLeft = { user ->
                if (user.userId == targetUserId) {
                    endCall()
                }
            }
            
            onError = { error ->
                viewModelScope.launch {
                    _webRTCEvents.emit(WebRTCEvent.Error(error))
                }
            }
        }
    }
    
    fun initiateCall(remoteUserId: String, callType: CallType) {
        viewModelScope.launch {
            try {
                targetUserId = remoteUserId
                
                // Initialize WebRTC
                val isVideoCall = callType == CallType.VIDEO
                webRTCManager.initializeLocalStream(isVideoCall)
                
                // Create peer connection
                peerConnection = webRTCManager.createPeerConnection(
                    object : PeerConnectionObserver() {
                        override fun onIceCandidate(candidate: IceCandidate?) {
                            candidate?.let {
                                signallingClient.sendIceCandidate(remoteUserId, it)
                            }
                        }
                        
                        override fun onAddStream(stream: MediaStream?) {
                            viewModelScope.launch {
                                _webRTCEvents.emit(WebRTCEvent.RemoteStreamAdded(stream))
                            }
                        }
                        
                        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                            when (state) {
                                PeerConnection.IceConnectionState.CONNECTED -> {
                                    viewModelScope.launch {
                                        _callState.value = _callState.value.copy(isInCall = true)
                                        _webRTCEvents.emit(WebRTCEvent.CallConnected)
                                    }
                                }
                                PeerConnection.IceConnectionState.FAILED,
                                PeerConnection.IceConnectionState.DISCONNECTED -> {
                                    viewModelScope.launch {
                                        _webRTCEvents.emit(WebRTCEvent.Error("Connection failed"))
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                )
                
                // Initiate call on server
                val response = callRepository.initiateCall(remoteUserId, callType)
                if (response.success) {
                    currentRoomId = response.callId
                    signallingClient.joinRoom(response.callId)
                    
                    // Create offer
                    webRTCManager.createOffer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                            sessionDescription?.let {
                                webRTCManager.setLocalDescription(it, object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        signallingClient.sendOffer(remoteUserId, it)
                                    }
                                })
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                _webRTCEvents.emit(WebRTCEvent.Error(e.message ?: "Failed to initiate call"))
            }
        }
    }
    
    private fun handleOfferReceived(offer: SessionDescription, userId: String) {
        targetUserId = userId
        webRTCManager.setRemoteDescription(offer, object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                webRTCManager.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        sessionDescription?.let {
                            webRTCManager.setLocalDescription(it, object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    signallingClient.sendAnswer(userId, it)
                                }
                            })
                        }
                    }
                })
            }
        })
    }
    
    private fun handleAnswerReceived(answer: SessionDescription, userId: String) {
        webRTCManager.setRemoteDescription(answer, object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Timber.d("Remote description set successfully")
            }
        })
    }
    
    private fun handleIceCandidateReceived(candidate: IceCandidate, userId: String) {
        webRTCManager.addIceCandidate(candidate)
    }
    
    fun toggleMute() {
        val newMuteState = !_callState.value.isMuted
        _callState.value = _callState.value.copy(isMuted = newMuteState)
        webRTCManager.toggleAudio(!newMuteState)
        signallingClient.toggleAudio(!newMuteState)
    }
    
    fun toggleVideo() {
        val newVideoState = !_callState.value.isVideoEnabled
        _callState.value = _callState.value.copy(isVideoEnabled = newVideoState)
        webRTCManager.toggleVideo(newVideoState)
        signallingClient.toggleVideo(newVideoState)
    }
    
    fun toggleSpeaker() {
        _callState.value = _callState.value.copy(
            isSpeakerOn = !_callState.value.isSpeakerOn
        )
    }
    
    fun incrementCallDuration() {
        _callState.value = _callState.value.copy(
            callDuration = _callState.value.callDuration + 1
        )
    }
    
    fun endCall() {
        viewModelScope.launch {
            currentRoomId?.let {
                callRepository.endCall(it)
            }
            _webRTCEvents.emit(WebRTCEvent.CallEnded)
        }
    }
    
    fun cleanup() {
        webRTCManager.release()
        signallingClient.disconnect()
    }
}

sealed class WebRTCEvent {
    object CallConnected : WebRTCEvent()
    data class RemoteStreamAdded(val stream: MediaStream?) : WebRTCEvent()
    data class Error(val message: String) : WebRTCEvent()
    object CallEnded : WebRTCEvent()
}

// File: data/repository/CallRepository.kt
package com.voipex.android.data.repository

import com.voipex.android.data.api.CallApi
import com.voipex.android.data.models.*
import javax.inject.Inject

class CallRepository @Inject constructor(
    private val callApi: CallApi
) {
    suspend fun initiateCall(targetUserId: String, callType: CallType): InitiateCallResponse {
        return callApi.initiateCall(
            InitiateCallRequest(
                targetUserId = targetUserId,
                callType = callType.name.lowercase()
            )
        )
    }
    
    suspend fun endCall(callId: String) {
        callApi.endCall(mapOf("callId" to callId))
    }
    
    suspend fun getCallHistory(page: Int = 1): List<Call> {
        return callApi.getCallHistory(page)
    }
}