package com.ntdt.voipex.data.signaling

import com.ntdt.voipex.data.local.PreferencesManager
import com.ntdt.voipex.domain.model.GuestUser
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class GuestSignalingClient @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private var socket: Socket? = null
    private var currentUser: GuestUser? = null
    
    private val _signalingEvents = MutableSharedFlow<SignalingEvent>()
    val signalingEvents: SharedFlow<SignalingEvent> = _signalingEvents
    
    // 버퍼링된 이벤트를 저장하는 리스트
    private val bufferedEvents = mutableListOf<SignalingEvent>()
    private var isBuffering = true
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 버퍼링 시작/종료
    fun startBuffering() {
        Timber.d("[GuestSignaling] Starting event buffering")
        isBuffering = true
    }
    
    fun stopBufferingAndFlush() {
        Timber.d("[GuestSignaling] Stopping buffering and flushing ${bufferedEvents.size} events")
        isBuffering = false
        
        // 버퍼링된 이벤트를 모두 전송
        scope.launch {
            bufferedEvents.forEach { event ->
                Timber.d("[GuestSignaling] Flushing buffered event: $event")
                _signalingEvents.emit(event)
            }
            bufferedEvents.clear()
        }
    }
    
    private suspend fun emitEvent(event: SignalingEvent) {
        if (isBuffering && shouldBuffer(event)) {
            Timber.d("[GuestSignaling] Buffering event: $event")
            bufferedEvents.add(event)
        } else {
            _signalingEvents.emit(event)
        }
    }
    
    private fun shouldBuffer(event: SignalingEvent): Boolean {
        return when (event) {
            is SignalingEvent.OfferReceived,
            is SignalingEvent.AnswerReceived,
            is SignalingEvent.IceCandidateReceived -> true
            else -> false
        }
    }
    
    fun connect(guestUser: GuestUser) {
        currentUser = guestUser
        
        try {
            val token = preferencesManager.authToken
            
            val options = IO.Options()
            options.forceNew = true
            options.reconnection = true
            options.query = "userId=${guestUser.id}&name=${guestUser.name}&isGuest=true"
            options.auth = mapOf("token" to token)
            
            socket = IO.socket("http://10.47.16.163:3004", options)
            
            setupEventListeners()
            
            socket?.connect()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to signaling server")
        }
    }
    
    private fun setupEventListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                Timber.d("Connected to signaling server")
                // 서버에서 인증 단계에서 이미 처리됨
            }
            
            on(Socket.EVENT_DISCONNECT) {
                Timber.d("Disconnected from signaling server")
            }
            
            // 게스트 사용자 목록 수신
            on("guest-list") { args ->
                try {
                    val data = args[0] as JSONObject
                    val users = data.getJSONArray("users")
                    
                    for (i in 0 until users.length()) {
                        val user = users.getJSONObject(i)
                        val guestUser = GuestUser(
                            id = user.getString("userId"),
                            name = user.getString("name")
                        )
                        
                        // 자기 자신은 제외
                        if (guestUser.id != currentUser?.id) {
                            scope.launch {
                                _signalingEvents.emit(SignalingEvent.GuestJoined(
                                    userId = guestUser.id,
                                    name = guestUser.name
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse guest list")
                }
            }
            
            // 새 게스트 사용자 입장
            on("guestJoined") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.getString("userId")
                    val name = data.getString("name")
                    
                    // 자기 자신은 제외
                    if (userId != currentUser?.id) {
                        scope.launch {
                            _signalingEvents.emit(SignalingEvent.GuestJoined(userId, name))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse guest joined event")
                }
            }
            
            // 게스트 사용자 퇴장
            on("guestLeft") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.getString("userId")
                    
                    scope.launch {
                        _signalingEvents.emit(SignalingEvent.GuestLeft(userId))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse guest left event")
                }
            }
            
            // 통화 요청 수신
            on("incoming-call") { args ->
                try {
                    val data = args[0] as JSONObject
                    Timber.d("[GuestSignaling] Received incoming-call event: $data")
                    
                    val callerId = data.getString("callerId")
                    val callerName = data.getString("callerName")
                    val callType = data.getString("callType")
                    val roomId = data.getString("roomId")
                    val callId = data.optString("callId", "")
                    
                    Timber.d("[GuestSignaling] Incoming call - callerId: $callerId, callerName: $callerName, roomId: $roomId, callId: $callId")
                    
                    scope.launch {
                        _signalingEvents.emit(SignalingEvent.IncomingCall(
                            callerId = callerId,
                            callerName = callerName,
                            callType = callType,
                            roomId = roomId,
                            callId = callId
                        ))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse incoming call")
                }
            }
            
            // WebRTC Offer 수신
            on("offer") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.optString("userId") ?: data.optString("from")
                    val sdp = data.optString("sdp") ?: data.optString("offer")
                    val roomId = data.optString("roomId", "")
                    
                    Timber.d("[GuestSignaling] Received offer from $userId, data: $data")
                    
                    if (userId.isNotEmpty() && sdp.isNotEmpty()) {
                        scope.launch {
                            emitEvent(SignalingEvent.OfferReceived(
                                userId = userId,
                                sdp = sdp,
                                roomId = roomId
                            ))
                        }
                    } else {
                        Timber.e("[GuestSignaling] Invalid offer data - userId: $userId, sdp length: ${sdp.length}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse offer")
                }
            }
            
            // WebRTC Answer 수신
            on("answer") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.optString("userId") ?: data.optString("from")
                    val sdp = data.optString("sdp") ?: data.optString("answer")
                    val roomId = data.optString("roomId", "")
                    
                    Timber.d("[GuestSignaling] Received answer from $userId, data: $data")
                    
                    if (userId.isNotEmpty() && sdp.isNotEmpty()) {
                        scope.launch {
                            emitEvent(SignalingEvent.AnswerReceived(
                                userId = userId,
                                sdp = sdp,
                                roomId = roomId
                            ))
                        }
                    } else {
                        Timber.e("[GuestSignaling] Invalid answer data - userId: $userId, sdp length: ${sdp.length}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse answer")
                }
            }
            
            // ICE Candidate 수신
            on("ice-candidate") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.optString("userId") ?: data.optString("from")
                    val candidate = data.getString("candidate")
                    val sdpMid = data.optString("sdpMid", null)
                    val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
                    
                    Timber.d("[GuestSignaling] Received ICE candidate from $userId, data: $data")
                    
                    if (userId.isNotEmpty()) {
                        val iceCandidate = IceCandidate(
                            sdp = candidate,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex
                        )
                        
                        scope.launch {
                            emitEvent(SignalingEvent.IceCandidateReceived(
                                userId = userId,
                                candidate = iceCandidate
                            ))
                        }
                    } else {
                        Timber.e("[GuestSignaling] Invalid ICE candidate data - userId: $userId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse ICE candidate")
                }
            }
            
            // 통화 수락됨
            on("call-accepted") { args ->
                try {
                    val data = args[0] as JSONObject
                    val roomId = data.getString("roomId")
                    
                    Timber.d("[GuestSignaling] Call accepted for room: $roomId")
                    
                    scope.launch {
                        _signalingEvents.emit(SignalingEvent.CallAccepted(roomId))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse call accepted")
                }
            }
            
            // 통화 거절됨
            on("call-rejected") { args ->
                try {
                    val data = args[0] as JSONObject
                    val roomId = data.getString("roomId")
                    
                    Timber.d("[GuestSignaling] Call rejected for room: $roomId")
                    
                    scope.launch {
                        _signalingEvents.emit(SignalingEvent.CallRejected(roomId))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse call rejected")
                }
            }
            
            // 통화 종료됨
            on("call-ended") { args ->
                try {
                    val data = args[0] as JSONObject
                    val roomId = data.getString("roomId")
                    
                    Timber.d("[GuestSignaling] Call ended for room: $roomId")
                    
                    scope.launch {
                        _signalingEvents.emit(SignalingEvent.CallEnded(roomId))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[GuestSignaling] Failed to parse call ended")
                }
            }
        }
    }
    
    fun initiateCall(
        targetUserId: String,
        callType: String = "audio",
        roomId: String? = null,
        callId: String? = null
    ) {
        Timber.d("[GuestSignaling] initiateCall - targetUserId: $targetUserId, callType: $callType, roomId: $roomId, callId: $callId")
        
        currentUser?.let { _ ->
            val data = JSONObject().apply {
                put("targetUserId", targetUserId)
                put("callType", callType)
                roomId?.let { put("roomId", it) }
                callId?.let { put("callId", it) }
            }
            
            Timber.d("[GuestSignaling] Emitting call-user event with data: $data")
            socket?.emit("call-user", data)
        } ?: run {
            Timber.e("[GuestSignaling] Cannot initiate call - no current user")
        }
    }
    
    fun sendOffer(targetUserId: String, sdp: String) {
        val data = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("sdp", sdp)
        }
        Timber.d("[GuestSignaling] Sending offer to $targetUserId")
        socket?.emit("offer", data)
    }
    
    fun sendAnswer(targetUserId: String, sdp: String) {
        val data = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("sdp", sdp)
        }
        Timber.d("[GuestSignaling] Sending answer to $targetUserId")
        socket?.emit("answer", data)
    }
    
    fun sendIceCandidate(targetUserId: String, candidate: org.webrtc.IceCandidate) {
        val data = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        Timber.d("[GuestSignaling] Sending ICE candidate to $targetUserId")
        socket?.emit("ice-candidate", data)
    }
    
    fun acceptCall(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("accept-call", data)
    }
    
    fun rejectCall(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("reject-call", data)
    }
    
    fun endCall(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        Timber.d("[GuestSignaling] Ending call for room: $roomId")
        socket?.emit("end-call", data)
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket = null
        currentUser = null
    }
}

sealed class SignalingEvent {
    data class GuestJoined(val userId: String, val name: String) : SignalingEvent()
    data class GuestLeft(val userId: String) : SignalingEvent()
    data class IncomingCall(
        val callerId: String,
        val callerName: String,
        val callType: String,
        val roomId: String,
        val callId: String = ""
    ) : SignalingEvent()
    data class CallAccepted(val roomId: String) : SignalingEvent()
    data class CallRejected(val roomId: String) : SignalingEvent()
    data class CallEnded(val roomId: String) : SignalingEvent()
    data class OfferReceived(val userId: String, val sdp: String, val roomId: String) : SignalingEvent()
    data class AnswerReceived(val userId: String, val sdp: String, val roomId: String) : SignalingEvent()
    data class IceCandidateReceived(val userId: String, val candidate: IceCandidate) : SignalingEvent()
}

data class IceCandidate(
    val sdp: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)
