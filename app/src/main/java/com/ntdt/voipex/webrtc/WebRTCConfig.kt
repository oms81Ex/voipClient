package com.ntdt.voipex.webrtc

import com.ntdt.voipex.utils.Constants
import org.webrtc.PeerConnection
import org.webrtc.MediaConstraints
import javax.inject.Inject
import javax.inject.Singleton

data class IceServerConfig(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@Singleton
class WebRTCConfig @Inject constructor() {
    
    private val iceServerConfigs: List<IceServerConfig> = listOf(
        IceServerConfig(listOf(Constants.STUN_SERVER_URL)),
        IceServerConfig(
            urls = Constants.TURN_SERVER_URLS,
            username = Constants.TURN_USERNAME,
            credential = Constants.TURN_CREDENTIAL
        )
    )
    
    fun getIceServers(): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        
        // Add STUN server
        iceServers.add(
            PeerConnection.IceServer.builder(Constants.STUN_SERVER_URL)
                .createIceServer()
        )
        
        // Add TURN servers
        Constants.TURN_SERVER_URLS.forEach { url ->
            iceServers.add(
                PeerConnection.IceServer.builder(url)
                    .setUsername(Constants.TURN_USERNAME)
                    .setPassword(Constants.TURN_CREDENTIAL)
                    .createIceServer()
            )
        }
        
        return iceServers
    }
    
    fun getMediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            )
            mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
            )
        }
    }
    
    fun getVideoConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(
                MediaConstraints.KeyValuePair("maxWidth", Constants.VIDEO_WIDTH.toString())
            )
            mandatory.add(
                MediaConstraints.KeyValuePair("maxHeight", Constants.VIDEO_HEIGHT.toString())
            )
            mandatory.add(
                MediaConstraints.KeyValuePair("maxFrameRate", Constants.VIDEO_FPS.toString())
            )
        }
    }
    
    fun getAudioConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(
                MediaConstraints.KeyValuePair("echoCancellation", "true")
            )
            mandatory.add(
                MediaConstraints.KeyValuePair("noiseSuppression", "true")
            )
            mandatory.add(
                MediaConstraints.KeyValuePair("autoGainControl", "true")
            )
        }
    }
} 