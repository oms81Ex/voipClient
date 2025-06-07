package com.ntdt.voipex.data.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface CallApi {
    @POST("call/offer/{userId}")
    suspend fun sendOffer(
        @Path("userId") userId: String,
        @Body request: CallOfferRequest
    )

    @POST("call/answer/{userId}")
    suspend fun sendAnswer(
        @Path("userId") userId: String,
        @Body request: CallAnswerRequest
    )

    @POST("call/ice-candidate/{userId}")
    suspend fun sendIceCandidate(
        @Path("userId") userId: String,
        @Body request: IceCandidateRequest
    )

    // --- REST API for call create/join ---
    @POST("calls")
    suspend fun createCall(@Body request: CreateCallRequest): CreateCallResponse

    @POST("calls/{callId}/join")
    suspend fun joinCall(@Path("callId") callId: String): JoinCallResponse
    
    @POST("calls/create-guest-call")
    suspend fun createGuestCall(@Body request: CreateGuestCallRequest): CreateGuestCallResponse
}

data class CallOfferRequest(
    val sdp: String
)

data class CallAnswerRequest(
    val sdp: String
)

data class IceCandidateRequest(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
)

// --- REST Call Data Classes ---
data class CreateCallRequest(
    val calleeId: String
)
data class CreateCallResponse(
    val callId: String,
    val status: String
)
data class JoinCallResponse(
    val callId: String,
    val status: String
)

// Guest Call Data Classes
data class CreateGuestCallRequest(
    val callerId: String,
    val calleeId: String,
    val callerName: String,
    val calleeName: String,
    val callType: String = "audio"
)

data class CreateGuestCallResponse(
    val status: String,
    val data: GuestCallData
)

data class GuestCallData(
    val callId: String,
    val roomId: String,
    val callerId: String,
    val calleeId: String,
    val callerName: String,
    val calleeName: String,
    val callType: String,
    val status: String,
    val createdAt: String
) 