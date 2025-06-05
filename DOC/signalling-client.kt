// File: webrtc/SignallingClient.kt
package com.voipex.android.webrtc

import com.google.gson.Gson
import com.voipex.android.BuildConfig
import com.voipex.android.data.models.*
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignallingClient @Inject constructor() {
    private var socket: Socket? = null
    private val gson = Gson()
    
    // Callbacks
    var onOfferReceived: ((SessionDescription, String) -> Unit)? = null
    var onAnswerReceived: ((SessionDescription, String) -> Unit)? = null
    var onIceCandidateReceived: ((IceCandidate, String) -> Unit)? = null
    var onUserJoined: ((RoomUser) -> Unit)? = null
    var onUserLeft: ((RoomUser) -> Unit)? = null
    var onRoomUsers: ((List<RoomUser>) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onUserAudioToggle: ((String, Boolean) -> Unit)? = null
    var onUserVideoToggle: ((String, Boolean) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    
    fun connect(authToken: String) {
        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to authToken)
                transports = arrayOf("websocket")
                reconnection = true
                reconnectionAttempts = 3
                reconnectionDelay = 1000
            }
            
            socket = IO.socket(BuildConfig.SOCKET_URL, options)
            setupEventListeners()
            socket?.connect()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to socket")
            onError?.invoke("Failed to connect: ${e.message}")
        }
    }
    
    private fun setupEventListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT, onConnect)
            on(Socket.EVENT_DISCONNECT, onDisconnect)
            on(Socket.EVENT_CONNECT_ERROR, onConnectError)
            
            on("offer", onOffer)
            on("answer", onAnswer)
            on("ice-candidate", onIceCandidate)
            on("user-joined", onUserJoinedEvent)
            on("user-left", onUserLeftEvent)
            on("room-users", onRoomUsersEvent)
            on("user-audio-toggle", onUserAudioToggleEvent)
            on("user-video-toggle", onUserVideoToggleEvent)
            on("error", onErrorEvent)
        }
    }
    
    private val onConnect = Emitter.Listener {
        Timber.d("Socket connected")
        onConnected?.invoke()
    }
    
    private val onDisconnect = Emitter.Listener {
        Timber.d("Socket disconnected")
        onDisconnected?.invoke()
    }
    
    private val onConnectError = Emitter.Listener { args ->
        val error = args.firstOrNull()?.toString() ?: "Unknown error"
        Timber.e("Socket connection error: $error")
        onError?.invoke("Connection error: $error")
    }
    
    private val onOffer = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val offer = SessionDescription(
                SessionDescription.Type.OFFER,
                data.getJSONObject("offer").getString("sdp")
            )
            val userId = data.getString("userId")
            onOfferReceived?.invoke(offer, userId)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing offer")
        }
    }
    
    private val onAnswer = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val answer = SessionDescription(
                SessionDescription.Type.ANSWER,
                data.getJSONObject("answer").getString("sdp")
            )
            val userId = data.getString("userId")
            onAnswerReceived?.invoke(answer, userId)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing answer")
        }
    }
    
    private val onIceCandidate = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val candidateData = data.getJSONObject("candidate")
            val candidate = IceCandidate(
                candidateData.getString("sdpMid"),
                candidateData.getInt("sdpMLineIndex"),
                candidateData.getString("candidate")
            )
            val userId = data.getString("userId")
            onIceCandidateReceived?.invoke(candidate, userId)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing ICE candidate")
        }
    }
    
    private val onUserJoinedEvent = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val user = RoomUser(
                userId = data.getString("userId"),
                userName = data.getString("userName")
            )
            onUserJoined?.invoke(user)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing user joined event")
        }
    }
    
    private val onUserLeftEvent = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val user = RoomUser(
                userId = data.getString("userId"),
                userName = data.getString("userName")
            )
            onUserLeft?.invoke(user)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing user left event")
        }
    }
    
    private val onRoomUsersEvent = Emitter.Listener { args ->
        try {
            val users = mutableListOf<RoomUser>()
            val array = args[0] as org.json.JSONArray
            for (i in 0 until array.length()) {
                val userObj = array.getJSONObject(i)
                users.add(
                    RoomUser(
                        userId = userObj.getString("userId"),
                        userName = userObj.getString("userName")
                    )
                )
            }
            onRoomUsers?.invoke(users)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing room users")
        }
    }
    
    private val onUserAudioToggleEvent = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val userId = data.getString("userId")
            val isEnabled = data.getBoolean("isEnabled")
            onUserAudioToggle?.invoke(userId, isEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing audio toggle event")
        }
    }
    
    private val onUserVideoToggleEvent = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val userId = data.getString("userId")
            val isEnabled = data.getBoolean("isEnabled")
            onUserVideoToggle?.invoke(userId, isEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing video toggle event")
        }
    }
    
    private val onErrorEvent = Emitter.Listener { args ->
        try {
            val data = args[0] as JSONObject
            val message = data.getString("message")
            onError?.invoke(message)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing error event")
        }
    }
    
    fun joinRoom(roomId: String) {
        socket?.emit("join-room", roomId)
    }
    
    fun sendOffer(targetUserId: String, offer: SessionDescription) {
        val json = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("offer", JSONObject().apply {
                put("type", offer.type.toString())
                put("sdp", offer.description)
            })
        }
        socket?.emit("offer", json)
    }
    
    fun sendAnswer(targetUserId: String, answer: SessionDescription) {
        val json = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("answer", JSONObject().apply {
                put("type", answer.type.toString())
                put("sdp", answer.description)
            })
        }
        socket?.emit("answer", json)
    }
    
    fun sendIceCandidate(targetUserId: String, candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("targetUserId", targetUserId)
            put("candidate", JSONObject().apply {
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            })
        }
        socket?.emit("ice-candidate", json)
    }
    
    fun toggleAudio(isEnabled: Boolean) {
        socket?.emit("toggle-audio", isEnabled)
    }
    
    fun toggleVideo(isEnabled: Boolean) {
        socket?.emit("toggle-video", isEnabled)
    }
    
    fun sendMessage(message: String) {
        socket?.emit("send-message", message)
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
    
    fun isConnected(): Boolean = socket?.connected() ?: false
}