package com.ntdt.voipex.data.signaling

import com.ntdt.voipex.domain.model.GuestUser
import com.ntdt.voipex.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuestSignalingClient @Inject constructor() {
    private var socket: Socket? = null
    
    private val _signalingEvents = MutableSharedFlow<SignalingEvent>()
    val signalingEvents: SharedFlow<SignalingEvent> = _signalingEvents

    private val _connectionState = MutableSharedFlow<SignalingConnectionState>()
    val connectionState: SharedFlow<SignalingConnectionState> = _connectionState

    fun connect(guestUser: GuestUser) {
        try {
            val options = IO.Options().apply {
                query = "userId=${guestUser.id}&name=${guestUser.name}&isGuest=true"
                reconnection = true
                reconnectionAttempts = 3
                reconnectionDelay = 1000
            }
            socket = IO.socket(Constants.SIGNALING_SERVER_URL, options)
            
            setupSocketListeners()
            socket?.connect()
            _connectionState.tryEmit(SignalingConnectionState.Connecting)
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to signaling server")
            _connectionState.tryEmit(SignalingConnectionState.Error(e.message ?: "Unknown error"))
        }
    }

    private fun setupSocketListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                Timber.d("Connected to signaling server")
                _connectionState.tryEmit(SignalingConnectionState.Connected)
            }

            on(Socket.EVENT_DISCONNECT) {
                Timber.d("Disconnected from signaling server")
                _connectionState.tryEmit(SignalingConnectionState.Disconnected)
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args[0] as Exception
                Timber.e(error, "Connection error")
                _connectionState.tryEmit(SignalingConnectionState.Error(error.message ?: "Connection error"))
            }

            on("guestJoined") { args ->
                val data = args[0] as JSONObject
                val guestId = data.getString("userId")
                val name = data.getString("name")
                _signalingEvents.tryEmit(SignalingEvent.GuestJoined(guestId, name))
            }

            on("guestLeft") { args ->
                val data = args[0] as JSONObject
                val guestId = data.getString("userId")
                _signalingEvents.tryEmit(SignalingEvent.GuestLeft(guestId))
            }

            on("offer") { args ->
                val data = args[0] as JSONObject
                val fromId = data.getString("from")
                val sdp = data.getString("sdp")
                _signalingEvents.tryEmit(SignalingEvent.OfferReceived(fromId, sdp))
            }

            on("answer") { args ->
                val data = args[0] as JSONObject
                val fromId = data.getString("from")
                val sdp = data.getString("sdp")
                _signalingEvents.tryEmit(SignalingEvent.AnswerReceived(fromId, sdp))
            }

            on("iceCandidate") { args ->
                val data = args[0] as JSONObject
                val fromId = data.getString("from")
                val candidate = data.getString("candidate")
                val sdpMid = data.getString("sdpMid")
                val sdpMLineIndex = data.getInt("sdpMLineIndex")
                _signalingEvents.tryEmit(
                    SignalingEvent.IceCandidateReceived(
                        fromId,
                        candidate,
                        sdpMid,
                        sdpMLineIndex
                    )
                )
            }
        }
    }

    fun sendOffer(toId: String, sdp: String) {
        val data = JSONObject().apply {
            put("to", toId)
            put("sdp", sdp)
        }
        socket?.emit("offer", data)
        Timber.d("Sent offer to $toId")
    }

    fun sendAnswer(toId: String, sdp: String) {
        val data = JSONObject().apply {
            put("to", toId)
            put("sdp", sdp)
        }
        socket?.emit("answer", data)
        Timber.d("Sent answer to $toId")
    }

    fun sendIceCandidate(
        toId: String,
        candidate: String,
        sdpMid: String,
        sdpMLineIndex: Int
    ) {
        val data = JSONObject().apply {
            put("to", toId)
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
        socket?.emit("iceCandidate", data)
        Timber.d("Sent ICE candidate to $toId")
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _connectionState.tryEmit(SignalingConnectionState.Disconnected)
    }
}

sealed class SignalingEvent {
    data class GuestJoined(val userId: String, val name: String) : SignalingEvent()
    data class GuestLeft(val userId: String) : SignalingEvent()
    data class OfferReceived(val fromId: String, val sdp: String) : SignalingEvent()
    data class AnswerReceived(val fromId: String, val sdp: String) : SignalingEvent()
    data class IceCandidateReceived(
        val fromId: String,
        val candidate: String,
        val sdpMid: String,
        val sdpMLineIndex: Int
    ) : SignalingEvent()
}

sealed class SignalingConnectionState {
    object Connecting : SignalingConnectionState()
    object Connected : SignalingConnectionState()
    object Disconnected : SignalingConnectionState()
    data class Error(val message: String) : SignalingConnectionState()
} 