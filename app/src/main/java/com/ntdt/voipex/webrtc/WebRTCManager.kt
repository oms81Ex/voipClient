package com.ntdt.voipex.webrtc

import android.content.Context
import org.webrtc.*
import javax.inject.Inject

class WebRTCManager @Inject constructor(private val context: Context) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    
    init {
        initializePeerConnectionFactory()
    }
    
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
    
    fun createLocalMediaStream(): MediaStream? {
        val mediaStream = peerConnectionFactory?.createLocalMediaStream("ARDAMS")
        
        // Create and add video track
        localVideoSource = peerConnectionFactory?.createVideoSource(false)
        val videoTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", localVideoSource)
        mediaStream?.addTrack(videoTrack)
        
        // Create and add audio track
        localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        mediaStream?.addTrack(audioTrack)
        
        return mediaStream
    }
    
    fun release() {
        localVideoSource?.dispose()
        localAudioSource?.dispose()
        peerConnectionFactory?.dispose()
    }
} 