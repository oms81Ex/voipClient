// File: webrtc/WebRTCManager.kt
package com.voipex.android.webrtc

import android.content.Context
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    private val context: Context
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localStream: MediaStream? = null
    
    private val eglBase = EglBase.create()
    val eglBaseContext: EglBase.Context = eglBase.eglBaseContext
    
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )
    
    init {
        initializePeerConnectionFactory()
    }
    
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setAudioDeviceModule(createAudioDeviceModule())
            .createPeerConnectionFactory()
    }
    
    private fun createAudioDeviceModule(): AudioDeviceModule {
        return JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
    }
    
    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            audioJitterBufferMaxPackets = 50
            audioJitterBufferFastAccelerate = true
            iceConnectionReceivingTimeout = 5000
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }
    
    fun initializeLocalStream(isVideoCall: Boolean) {
        val mediaStream = peerConnectionFactory?.createLocalMediaStream("local_stream")
        
        // Add audio track
        val audioSource = peerConnectionFactory?.createAudioSource(createAudioConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
        localAudioTrack?.let { mediaStream?.addTrack(it) }
        
        // Add video track if video call
        if (isVideoCall) {
            val videoSource = peerConnectionFactory?.createVideoSource(false)
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
            localVideoTrack?.let { mediaStream?.addTrack(it) }
            
            // Initialize camera
            initializeCamera(videoSource)
        }
        
        localStream = mediaStream
        peerConnection?.addStream(mediaStream)
    }
    
    private fun initializeCamera(videoSource: VideoSource?) {
        videoCapturer = createCameraCapturer()
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBaseContext),
            context,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(1280, 720, 30)
    }
    
    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        
        // Try front camera first
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // Fall back to rear camera
        for (deviceName in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        return null
    }
    
    fun createOffer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(sdpObserver, constraints)
    }
    
    fun createAnswer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(sdpObserver, constraints)
    }
    
    fun setLocalDescription(sessionDescription: SessionDescription, observer: SdpObserver) {
        peerConnection?.setLocalDescription(observer, sessionDescription)
    }
    
    fun setRemoteDescription(sessionDescription: SessionDescription, observer: SdpObserver) {
        peerConnection?.setRemoteDescription(observer, sessionDescription)
    }
    
    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
    
    fun toggleAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }
    
    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }
    
    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }
    
    fun setSpeakerPhone(enabled: Boolean) {
        // This would be handled by AudioManager in the activity
    }
    
    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(eglBaseContext, null)
        localVideoTrack?.addSink(renderer)
    }
    
    fun attachRemoteRenderer(renderer: SurfaceViewRenderer, remoteStream: MediaStream?) {
        renderer.init(eglBaseContext, null)
        remoteStream?.videoTracks?.firstOrNull()?.addSink(renderer)
    }
    
    fun release() {
        Timber.d("Releasing WebRTC resources")
        
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        
        localVideoTrack?.dispose()
        localVideoTrack = null
        
        localAudioTrack?.dispose()
        localAudioTrack = null
        
        localStream?.dispose()
        localStream = null
        
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }
    
    private fun createAudioConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
    }
}

// File: webrtc/PeerConnectionObserver.kt
package com.voipex.android.webrtc

import org.webrtc.*
import timber.log.Timber

open class PeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        Timber.d("onSignalingChange: $state")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Timber.d("onIceConnectionChange: $state")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Timber.d("onIceConnectionReceivingChange: $receiving")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        Timber.d("onIceGatheringChange: $state")
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        Timber.d("onIceCandidate: $candidate")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Timber.d("onIceCandidatesRemoved")
    }

    override fun onAddStream(stream: MediaStream?) {
        Timber.d("onAddStream: ${stream?.id}")
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Timber.d("onRemoveStream: ${stream?.id}")
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Timber.d("onDataChannel")
    }

    override fun onRenegotiationNeeded() {
        Timber.d("onRenegotiationNeeded")
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        Timber.d("onAddTrack")
    }
}

// File: webrtc/SimpleSdpObserver.kt
package com.voipex.android.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import timber.log.Timber

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        Timber.d("onCreateSuccess: ${sessionDescription?.type}")
    }

    override fun onSetSuccess() {
        Timber.d("onSetSuccess")
    }

    override fun onCreateFailure(error: String?) {
        Timber.e("onCreateFailure: $error")
    }

    override fun onSetFailure(error: String?) {
        Timber.e("onSetFailure: $error")
    }
}