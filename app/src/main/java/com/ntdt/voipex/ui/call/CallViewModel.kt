package com.ntdt.voipex.ui.call

import androidx.lifecycle.ViewModel
import com.ntdt.voipex.webrtc.WebRTCManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import com.ntdt.voipex.data.repository.CallRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class CallState {
    object Idle : CallState()
    object Connecting : CallState()
    object Connected : CallState()
    object Ended : CallState()
    data class Error(val message: String) : CallState()
}

@HiltViewModel
class CallViewModel @Inject constructor(
    private val webRTCManager: WebRTCManager,
    private val callRepository: CallRepository,
    private val guestSignalingClient: GuestSignalingClient
) : ViewModel() {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState
    
    private var currentRoomId: String? = null
    private var currentPeerId: String? = null
    private var isGuestCall: Boolean = false
    
    // Call timer
    private val _callDuration = MutableStateFlow(0)
    val callDuration: StateFlow<Int> = _callDuration
    private var timerJob: kotlinx.coroutines.Job? = null

    fun initializeGuestCall(
        callId: String?,
        roomId: String?,
        userId: String,
        userName: String,
        isIncoming: Boolean,
        callType: String
    ) {
        Timber.d("[GuestCall] ========== INITIALIZE GUEST CALL START ==========")
        Timber.d("[GuestCall] Parameters:")
        Timber.d("[GuestCall]   - callId: $callId")
        Timber.d("[GuestCall]   - roomId: $roomId")
        Timber.d("[GuestCall]   - userId: $userId")
        Timber.d("[GuestCall]   - userName: $userName")
        Timber.d("[GuestCall]   - isIncoming: $isIncoming")
        Timber.d("[GuestCall]   - callType: $callType")
        
        currentRoomId = roomId
        currentPeerId = userId
        isGuestCall = true
        
        Timber.d("[GuestCall] Setting call state to Connecting")
        _callState.value = CallState.Connecting
        
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] Starting WebRTC setup...")
                // WebRTC 초기화
                setupWebRTC()
                Timber.d("[GuestCall] WebRTC setup completed")
                
                if (!isIncoming) {
                    // 발신자: offer 생성 및 전송
                    Timber.d("[GuestCall] OUTGOING CALL - Creating offer...")
                    Timber.d("[GuestCall] Waiting 1000ms for WebRTC to stabilize...")
                    delay(1000) // WebRTC가 완전히 초기화될 시간을 줌
                    
                    Timber.d("[GuestCall] Calling webRTCManager.createOffer()...")
                    val offer = webRTCManager.createOffer()
                    if (offer != null) {
                        Timber.d("[GuestCall] Offer created successfully!")
                        Timber.d("[GuestCall] Offer type: ${offer.type}")
                        Timber.d("[GuestCall] Offer SDP length: ${offer.description.length}")
                        Timber.d("[GuestCall] Sending offer to $userId...")
                        sendOffer(userId, offer)
                    } else {
                        Timber.e("[GuestCall] ERROR: createOffer returned null")
                        _callState.value = CallState.Error("Offer 생성 실패")
                    }
                } else {
                    // 수신자: answer 준비
                    Timber.d("[GuestCall] INCOMING CALL - Waiting for offer...")
                    Timber.d("[GuestCall] WebRTC is ready to receive offer")
                    // 수신자도 WebRTC를 미리 초기화해둔다
                    delay(500)
                    Timber.d("[GuestCall] Ready to process incoming offer")
                }
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] ERROR in initializeGuestCall: ${e.message}")
                _callState.value = CallState.Error(e.message ?: "통화 초기화 실패")
            }
        }
        Timber.d("[GuestCall] ========== INITIALIZE GUEST CALL END ==========")
    }
    
    fun initializeCall(roomId: String?, isIncoming: Boolean, peerId: String?) {
        _callState.value = CallState.Connecting
        if (roomId != null && peerId != null) {
            // guest-to-guest: roomId, peerId로 signaling 연결 (테스트용 바로 연결)
            _callState.value = CallState.Connected
            return
        }
        // 기존 userId/callId 기반 로직 (호환성)
        if (isIncoming) {
            // 수신: REST로 joinCall
            if (peerId != null) {
                viewModelScope.launch {
                    try {
                        callRepository.joinCall(peerId)
                        // TODO: signaling 연결 및 answer 전송
                        _callState.value = CallState.Connected
                    } catch (e: Exception) {
                        _callState.value = CallState.Error(e.message ?: "통화 참여 실패")
                    }
                }
            }
        } else {
            // 발신: REST로 createCall
            viewModelScope.launch {
                try {
                    callRepository.createCall(roomId ?: "")
                    // TODO: signaling 연결 및 offer 전송
                    _callState.value = CallState.Connected
                } catch (e: Exception) {
                    _callState.value = CallState.Error(e.message ?: "통화 생성 실패")
                }
            }
        }
    }

    fun endCall() {
        Timber.d("[GuestCall] Ending call")
        timerJob?.cancel()
        
        // 시그널링 서버에 통화 종료 알림
        if (isGuestCall && currentRoomId != null) {
            guestSignalingClient.endCall(currentRoomId!!)
        }
        
        webRTCManager.endCall()
        _callState.value = CallState.Ended
    }

    fun cleanup() {
        Timber.d("[GuestCall] Cleanup")
        webRTCManager.release()
    }
    
    private fun setupWebRTC() {
        Timber.d("[GuestCall] ========== SETUP WEBRTC START ==========")
        
        try {
            Timber.d("[GuestCall] Step 1: Initializing audio only...")
            // WebRTC Manager 초기화 (오디오 전용)
            webRTCManager.initializeAudioOnly()
            Timber.d("[GuestCall] Step 1 completed: Audio initialized")
            
            Timber.d("[GuestCall] Step 2: Creating PeerConnection...")
            // PeerConnection 생성
            webRTCManager.createPeerConnection()
            Timber.d("[GuestCall] Step 2 completed: PeerConnection created")
            
            Timber.d("[GuestCall] Step 3: Setting up ICE candidate callback...")
            // ICE candidate 콜백 설정
            webRTCManager.setOnIceCandidateCallback { candidate ->
                Timber.d("[GuestCall] ICE CANDIDATE GENERATED:")
                Timber.d("[GuestCall]   - sdpMid: ${candidate.sdpMid}")
                Timber.d("[GuestCall]   - sdpMLineIndex: ${candidate.sdpMLineIndex}")
                Timber.d("[GuestCall]   - candidate: ${candidate.sdp}")
                currentPeerId?.let { peerId ->
                    Timber.d("[GuestCall] Sending ICE candidate to peer: $peerId")
                    sendIceCandidate(peerId, candidate)
                } ?: Timber.e("[GuestCall] ERROR: No peer ID set for ICE candidate")
            }
            Timber.d("[GuestCall] Step 3 completed: ICE callback set")
            
            Timber.d("[GuestCall] Step 4: Setting up connection state callback...")
            // 연결 상태 변경 콜백
            webRTCManager.setOnConnectionStateChangeCallback { state ->
                Timber.d("[GuestCall] *** CONNECTION STATE CHANGED: $state ***")
                when (state) {
                    "connected" -> {
                        Timber.d("[GuestCall] CONNECTED! Starting call timer...")
                        _callState.value = CallState.Connected
                        startCallTimer()
                    }
                    "disconnected" -> {
                        Timber.d("[GuestCall] DISCONNECTED! Ending call...")
                        _callState.value = CallState.Ended
                    }
                    "failed" -> {
                        Timber.e("[GuestCall] CONNECTION FAILED!")
                        _callState.value = CallState.Error("연결 실패")
                    }
                    else -> {
                        Timber.d("[GuestCall] Other state: $state")
                    }
                }
            }
            Timber.d("[GuestCall] Step 4 completed: Connection state callback set")
            
            Timber.d("[GuestCall] Step 5: Starting local stream (audio only)...")
            // 로컬 스트림 시작 (음성만)
            webRTCManager.startLocalStream(false)
            Timber.d("[GuestCall] Step 5 completed: Local stream started")
            
        } catch (e: Exception) {
            Timber.e(e, "[GuestCall] ERROR in setupWebRTC: ${e.message}")
            throw e
        }
        
        Timber.d("[GuestCall] ========== SETUP WEBRTC END ==========")
    }
    
    private fun startCallTimer() {
        Timber.d("[GuestCall] Starting call timer...")
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _callDuration.value = 0 // 타이머 초기화
            while (true) {
                delay(1000)
                val newDuration = _callDuration.value + 1
                _callDuration.value = newDuration
                Timber.d("[GuestCall] Call duration: $newDuration seconds")
            }
        }
    }
    
    private fun sendOffer(targetUserId: String, offer: SessionDescription) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] Sending offer to $targetUserId via signaling")
                // 시그널링 서버로 offer 전송
                guestSignalingClient.sendOffer(targetUserId, offer.description)
                Timber.d("[GuestCall] Offer sent successfully")
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] Failed to send offer")
                _callState.value = CallState.Error("Offer 전송 실패")
            }
        }
    }
    
    private fun sendAnswer(targetUserId: String, answer: SessionDescription) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] Sending answer to $targetUserId via signaling")
                // 시그널링 서버로 answer 전송
                guestSignalingClient.sendAnswer(targetUserId, answer.description)
                Timber.d("[GuestCall] Answer sent successfully")
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] Failed to send answer")
                _callState.value = CallState.Error("Answer 전송 실패")
            }
        }
    }
    
    private fun sendIceCandidate(targetUserId: String, candidate: IceCandidate) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] Sending ICE candidate to $targetUserId: ${candidate.sdp}")
                // 시그널링 서버로 ICE candidate 전송
                guestSignalingClient.sendIceCandidate(targetUserId, candidate)
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] Failed to send ICE candidate")
            }
        }
    }
    
    // 외부에서 offer를 받았을 때 호출
    fun handleReceivedOffer(fromUserId: String, sdp: String) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] ========== HANDLE RECEIVED OFFER START ==========")
                Timber.d("[GuestCall] Offer from: $fromUserId")
                Timber.d("[GuestCall] SDP length: ${sdp.length}")
                Timber.d("[GuestCall] First 100 chars of SDP: ${sdp.take(100)}...")
                
                Timber.d("[GuestCall] Creating SessionDescription object...")
                // Remote description 설정
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                Timber.d("[GuestCall] SessionDescription created, setting as remote description...")
                
                webRTCManager.setRemoteDescription(offer)
                Timber.d("[GuestCall] Remote description set successfully")
                
                // Answer 생성
                Timber.d("[GuestCall] Waiting 100ms before creating answer...")
                delay(100)
                
                Timber.d("[GuestCall] Creating answer...")
                val answer = webRTCManager.createAnswer()
                if (answer != null) {
                    Timber.d("[GuestCall] Answer created successfully!")
                    Timber.d("[GuestCall] Answer type: ${answer.type}")
                    Timber.d("[GuestCall] Answer SDP length: ${answer.description.length}")
                    Timber.d("[GuestCall] Sending answer back to $fromUserId...")
                    sendAnswer(fromUserId, answer)
                } else {
                    Timber.e("[GuestCall] ERROR: createAnswer returned null")
                    _callState.value = CallState.Error("Answer 생성 실패")
                }
                Timber.d("[GuestCall] ========== HANDLE RECEIVED OFFER END ==========")
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] ERROR in handleReceivedOffer: ${e.message}")
                Timber.e("[GuestCall] Stack trace: ${e.stackTraceToString()}")
                _callState.value = CallState.Error("Offer 처리 실패: ${e.message}")
            }
        }
    }
    
    // 외부에서 answer를 받았을 때 호출
    fun handleReceivedAnswer(fromUserId: String, sdp: String) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] ========== HANDLE RECEIVED ANSWER START ==========")
                Timber.d("[GuestCall] Answer from: $fromUserId")
                Timber.d("[GuestCall] SDP length: ${sdp.length}")
                Timber.d("[GuestCall] First 100 chars of SDP: ${sdp.take(100)}...")
                
                Timber.d("[GuestCall] Creating SessionDescription object...")
                // Remote description 설정
                val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                Timber.d("[GuestCall] SessionDescription created, setting as remote description...")
                
                webRTCManager.setRemoteDescription(answer)
                Timber.d("[GuestCall] Remote description (answer) set successfully")
                Timber.d("[GuestCall] ========== HANDLE RECEIVED ANSWER END ==========")
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] ERROR in handleReceivedAnswer: ${e.message}")
                Timber.e("[GuestCall] Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    // 외부에서 ICE candidate를 받았을 때 호출
    fun handleReceivedIceCandidate(fromUserId: String, candidate: IceCandidate) {
        viewModelScope.launch {
            try {
                Timber.d("[GuestCall] ========== HANDLE RECEIVED ICE CANDIDATE ==========")
                Timber.d("[GuestCall] From user: $fromUserId")
                Timber.d("[GuestCall] Candidate info:")
                Timber.d("[GuestCall]   - sdpMid: ${candidate.sdpMid}")
                Timber.d("[GuestCall]   - sdpMLineIndex: ${candidate.sdpMLineIndex}")
                Timber.d("[GuestCall]   - candidate: ${candidate.sdp}")
                
                Timber.d("[GuestCall] Adding ICE candidate to PeerConnection...")
                webRTCManager.addIceCandidate(candidate)
                Timber.d("[GuestCall] ICE candidate added successfully")
            } catch (e: Exception) {
                Timber.e(e, "[GuestCall] ERROR adding ICE candidate: ${e.message}")
            }
        }
    }
} 