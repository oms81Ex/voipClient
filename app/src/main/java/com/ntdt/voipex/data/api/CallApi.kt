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