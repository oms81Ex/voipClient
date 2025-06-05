// File: data/models/User.kt
package com.voipex.android.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val id: String,
    val email: String,
    val name: String,
    val profileImage: String? = null,
    val isOnline: Boolean = false
) : Parcelable

// File: data/models/AuthResponse.kt
package com.voipex.android.data.models

data class AuthResponse(
    val token: String,
    val user: User
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

// File: data/models/Call.kt
package com.voipex.android.data.models

import java.util.Date

data class Call(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val callerName: String,
    val calleeName: String,
    val callType: CallType,
    val startTime: Date,
    val endTime: Date? = null,
    val duration: Int? = null,
    val status: CallStatus
)

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallStatus {
    COMPLETED,
    MISSED,
    REJECTED,
    ONGOING
}

data class InitiateCallRequest(
    val targetUserId: String,
    val callType: String
)

data class InitiateCallResponse(
    val success: Boolean,
    val callId: String,
    val message: String
)

// File: data/models/SocketEvents.kt
package com.voipex.android.data.models

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

data class OfferData(
    val targetUserId: String,
    val offer: SessionDescription
)

data class AnswerData(
    val targetUserId: String,
    val answer: SessionDescription
)

data class IceCandidateData(
    val targetUserId: String,
    val candidate: IceCandidate
)

data class RoomUser(
    val userId: String,
    val userName: String
)

data class CallState(
    val isInCall: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeakerOn: Boolean = false,
    val callDuration: Int = 0,
    val remoteUserId: String? = null,
    val remoteUserName: String? = null
)