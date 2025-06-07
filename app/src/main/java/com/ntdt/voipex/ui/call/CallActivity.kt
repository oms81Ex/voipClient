package com.ntdt.voipex.ui.call

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ntdt.voipex.databinding.ActivityCallBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import android.widget.Toast
import com.ntdt.voipex.data.signaling.GuestSignalingClient
import com.ntdt.voipex.data.signaling.SignalingEvent
import com.ntdt.voipex.data.signaling.IceCandidate as SignalingIceCandidate
import javax.inject.Inject
import timber.log.Timber
import org.webrtc.IceCandidate

@AndroidEntryPoint
class CallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()
    
    @Inject
    lateinit var guestSignalingClient: GuestSignalingClient

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_IS_GUEST_CALL = "extra_is_guest_call"
        const val EXTRA_CALL_TYPE = "extra_call_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract all extras with logging
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: intent.getStringExtra("extra_call_id")
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: intent.getStringExtra("room_id")
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: intent.getStringExtra("peer_id")
        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: intent.getStringExtra("peer_name") ?: "Unknown"
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, intent.getBooleanExtra("is_incoming", false))
        val isGuestCall = intent.getBooleanExtra(EXTRA_IS_GUEST_CALL, intent.getBooleanExtra("is_guest_call", false))
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: intent.getStringExtra("call_type") ?: "audio"

        android.util.Log.d("CallActivity", "[GuestCall] onCreate - " +
            "callId=$callId, roomId=$roomId, userId=$userId, userName=$userName, " +
            "isIncoming=$isIncoming, isGuestCall=$isGuestCall, callType=$callType")

        // 게스트 통화인 경우 상대방 ID 표시
        if (isGuestCall && userId != null) {
            binding.tvCallStatus.text = "상대: $userId"
        } else if (roomId != null && userId != null) {
            binding.tvCallStatus.text = "상대: $userName\n방: $roomId"
        } else if (callId != null && userId != null) {
            binding.tvCallStatus.text = "상대: $userName\n통화 ID: $callId"
        } else {
            android.util.Log.e("CallActivity", "[GuestCall] Missing required extras")
            Toast.makeText(this, "통화 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        
        // Guest call일 경우 시그널링 이벤트 구독을 먼저 시작
        if (isGuestCall) {
            observeSignalingEvents()
        }
        
        observeViewModel()
        
        // Initialize call with proper parameters
        if (isGuestCall) {
            // 약간의 딜레이를 주어 시그널링 이벤트 리스너가 준비되도록 함
            lifecycleScope.launch {
                delay(100)
                // 버퍼링된 이벤트를 플러시
                guestSignalingClient.stopBufferingAndFlush()
                
                viewModel.initializeGuestCall(
                    callId = callId,
                    roomId = roomId,
                    userId = userId,
                    userName = userName,
                    isIncoming = isIncoming,
                    callType = callType
                )
            }
        } else {
            viewModel.initializeCall(roomId, isIncoming, userId)  
        }
    }

    private fun setupUI() {
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall()
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.callState.collectLatest { state ->
                when (state) {
                    is CallState.Idle -> updateCallStatus("대기 중")
                    is CallState.Connecting -> updateCallStatus("연결 중...")
                    is CallState.Connected -> {
                        updateCallStatus("통화 중")
                        // 통화 시간 업데이트 시작
                        observeCallDuration()
                    }
                    is CallState.Ended -> {
                        updateCallStatus("통화 종료")
                        finish()
                    }
                    is CallState.Error -> {
                        updateCallStatus("에러: ${state.message}")
                        Toast.makeText(this@CallActivity, "통화 에러: ${state.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
    
    private fun observeCallDuration() {
        lifecycleScope.launch {
            viewModel.callDuration.collectLatest { seconds ->
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val durationText = String.format("%02d:%02d", minutes, remainingSeconds)
                binding.tvCallDuration.text = durationText
            }
        }
    }

    private fun observeSignalingEvents() {
        Timber.d("[CallActivity] Starting to observe signaling events...")
        android.util.Log.d("CallActivity", "[CallActivity] Starting to observe signaling events...")
        
        lifecycleScope.launch {
            guestSignalingClient.signalingEvents.collect { event ->
                android.util.Log.d("CallActivity", "[GuestCall] ========== SIGNALING EVENT RECEIVED ==========")
                android.util.Log.d("CallActivity", "[GuestCall] Event type: ${event::class.simpleName}")
                android.util.Log.d("CallActivity", "[GuestCall] Event data: $event")
                
                Timber.d("[GuestCall] ========== SIGNALING EVENT RECEIVED ==========")
                Timber.d("[GuestCall] Event type: ${event::class.simpleName}")
                Timber.d("[GuestCall] Event data: $event")
                
                when (event) {
                    is SignalingEvent.OfferReceived -> {
                        android.util.Log.d("CallActivity", "[GuestCall] OFFER RECEIVED!")
                        android.util.Log.d("CallActivity", "[GuestCall]   From: ${event.userId}")
                        android.util.Log.d("CallActivity", "[GuestCall]   SDP length: ${event.sdp.length}")
                        
                        Timber.d("[GuestCall] OFFER RECEIVED!")
                        Timber.d("[GuestCall]   From: ${event.userId}")
                        Timber.d("[GuestCall]   SDP length: ${event.sdp.length}")
                        
                        viewModel.handleReceivedOffer(event.userId, event.sdp)
                    }
                    
                    is SignalingEvent.AnswerReceived -> {
                        android.util.Log.d("CallActivity", "[GuestCall] ANSWER RECEIVED!")
                        android.util.Log.d("CallActivity", "[GuestCall]   From: ${event.userId}")
                        android.util.Log.d("CallActivity", "[GuestCall]   SDP length: ${event.sdp.length}")
                        
                        Timber.d("[GuestCall] ANSWER RECEIVED!")
                        Timber.d("[GuestCall]   From: ${event.userId}")
                        Timber.d("[GuestCall]   SDP length: ${event.sdp.length}")
                        
                        viewModel.handleReceivedAnswer(event.userId, event.sdp)
                    }
                    
                    is SignalingEvent.IceCandidateReceived -> {
                        android.util.Log.d("CallActivity", "[GuestCall] ICE CANDIDATE RECEIVED!")
                        android.util.Log.d("CallActivity", "[GuestCall]   From: ${event.userId}")
                        android.util.Log.d("CallActivity", "[GuestCall]   Candidate: ${event.candidate.sdp}")
                        
                        Timber.d("[GuestCall] ICE CANDIDATE RECEIVED!")
                        Timber.d("[GuestCall]   From: ${event.userId}")
                        Timber.d("[GuestCall]   Candidate: ${event.candidate.sdp}")
                        
                        val iceCandidate = IceCandidate(
                            event.candidate.sdpMid,
                            event.candidate.sdpMLineIndex,
                            event.candidate.sdp
                        )
                        viewModel.handleReceivedIceCandidate(event.userId, iceCandidate)
                    }
                    
                    is SignalingEvent.CallAccepted -> {
                        android.util.Log.d("CallActivity", "[GuestCall] Call accepted for room: ${event.roomId}")
                        Timber.d("[GuestCall] Call accepted for room: ${event.roomId}")
                        // 통화가 수락됨 - UI 업데이트
                    }
                    
                    is SignalingEvent.CallRejected -> {
                        android.util.Log.d("CallActivity", "[GuestCall] Call rejected for room: ${event.roomId}")
                        Timber.d("[GuestCall] Call rejected for room: ${event.roomId}")
                        Toast.makeText(this@CallActivity, "통화가 거절되었습니다", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    
                    is SignalingEvent.CallEnded -> {
                        android.util.Log.d("CallActivity", "[GuestCall] Call ended for room: ${event.roomId}")
                        Timber.d("[GuestCall] Call ended for room: ${event.roomId}")
                        Toast.makeText(this@CallActivity, "통화가 종료되었습니다", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    
                    else -> {
                        android.util.Log.d("CallActivity", "[GuestCall] Other event: $event")
                        // 다른 이벤트들은 무시
                    }
                }
            }
        }
    }

    private fun updateCallStatus(status: String) {
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: intent.getStringExtra("peer_id")
        val isGuestCall = intent.getBooleanExtra(EXTRA_IS_GUEST_CALL, intent.getBooleanExtra("is_guest_call", false))
        
        if (isGuestCall && userId != null) {
            binding.tvCallStatus.text = "상대: $userId\n$status"
        } else {
            binding.tvCallStatus.text = status
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Activity가 종료될 때 버퍼링 시작
        guestSignalingClient.startBuffering()
        viewModel.cleanup()
    }
} 