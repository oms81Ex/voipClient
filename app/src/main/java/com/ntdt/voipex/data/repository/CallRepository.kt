package com.ntdt.voipex.data.repository

import com.ntdt.voipex.data.api.CallApi
import com.ntdt.voipex.data.api.CreateCallRequest
import com.ntdt.voipex.data.api.CreateCallResponse
import com.ntdt.voipex.data.api.JoinCallResponse
import com.ntdt.voipex.data.api.CreateGuestCallRequest
import com.ntdt.voipex.data.api.CreateGuestCallResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepository @Inject constructor(
    private val callApi: CallApi
) {
    suspend fun createCall(calleeId: String): CreateCallResponse {
        return callApi.createCall(CreateCallRequest(calleeId))
    }

    suspend fun joinCall(callId: String): JoinCallResponse {
        return callApi.joinCall(callId)
    }
    
    suspend fun createGuestCall(
        callerId: String,
        calleeId: String,
        callerName: String,
        calleeName: String,
        callType: String = "audio"
    ): CreateGuestCallResponse {
        return callApi.createGuestCall(
            CreateGuestCallRequest(
                callerId = callerId,
                calleeId = calleeId,
                callerName = callerName,
                calleeName = calleeName,
                callType = callType
            )
        )
    }
} 