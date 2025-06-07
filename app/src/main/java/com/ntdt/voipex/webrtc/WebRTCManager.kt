package com.ntdt.voipex.webrtc

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WebRTCManager @Inject constructor(
    private val context: Context,
    private val webRTCConfig: WebRTCConfig
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    // localStream은 Unified Plan에서 사용하지 않음
    private var audioManager: AudioManager? = null

    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null

    private var onIceCandidateCallback: ((IceCandidate) -> Unit)? = null
    private var onConnectionStateChangeCallback: ((String) -> Unit)? = null

    fun initialize(
        localVideoView: SurfaceViewRenderer? = null,
        remoteVideoView: SurfaceViewRenderer? = null
    ) {
        this.localVideoView = localVideoView
        this.remoteVideoView = remoteVideoView
        
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        initializePeerConnectionFactory()
        if (localVideoView != null && remoteVideoView != null) {
            initializeVideoViews()
        }
    }
    
    fun initializeAudioOnly() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        Timber.d("[WebRTC] PeerConnection created successfully")
        
        val decoderFactory = DefaultVideoDecoderFactory(
            EglBase.create().eglBaseContext
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initializeVideoViews() {
        val eglContext = EglBase.create().eglBaseContext
        
        localVideoView?.apply {
            setMirror(true)
            init(eglContext, null)
            setZOrderMediaOverlay(true)
        }
        
        remoteVideoView?.apply {
            setMirror(false)
            init(eglContext, null)
            setZOrderMediaOverlay(false)
        }
    }

    fun createPeerConnection() {
        Timber.d("[WebRTC] Creating PeerConnection...")
        val iceServers = webRTCConfig.getIceServers()
        Timber.d("[WebRTC] ICE servers: ${iceServers.size}")

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        Timber.d("[WebRTC] RTCConfiguration created with UNIFIED_PLAN")

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Timber.d("Signaling state changed: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Timber.d("[WebRTC] *** ICE CONNECTION STATE CHANGED: $state ***")
                    when (state) {
                        PeerConnection.IceConnectionState.NEW -> Timber.d("[WebRTC] ICE: NEW")
                        PeerConnection.IceConnectionState.CHECKING -> Timber.d("[WebRTC] ICE: CHECKING")
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            Timber.d("[WebRTC] ICE: CONNECTED!")
                            onConnectionStateChangeCallback?.invoke("connected")
                        }
                        PeerConnection.IceConnectionState.COMPLETED -> Timber.d("[WebRTC] ICE: COMPLETED")
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            Timber.d("[WebRTC] ICE: DISCONNECTED")
                            onConnectionStateChangeCallback?.invoke("disconnected")
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            Timber.e("[WebRTC] ICE: FAILED!")
                            onConnectionStateChangeCallback?.invoke("failed")
                        }
                        PeerConnection.IceConnectionState.CLOSED -> Timber.d("[WebRTC] ICE: CLOSED")
                        else -> Timber.d("[WebRTC] ICE: Unknown state $state")
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Timber.d("ICE connection receiving changed: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Timber.d("[WebRTC] ICE gathering state changed: $state")
                    when (state) {
                        PeerConnection.IceGatheringState.NEW -> Timber.d("[WebRTC] ICE Gathering: NEW")
                        PeerConnection.IceGatheringState.GATHERING -> Timber.d("[WebRTC] ICE Gathering: GATHERING")
                        PeerConnection.IceGatheringState.COMPLETE -> Timber.d("[WebRTC] ICE Gathering: COMPLETE")
                        else -> Timber.d("[WebRTC] ICE Gathering: Unknown state $state")
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Timber.d("[WebRTC] New ICE candidate generated:")
                        Timber.d("[WebRTC]   sdpMid: ${it.sdpMid}")
                        Timber.d("[WebRTC]   sdpMLineIndex: ${it.sdpMLineIndex}")
                        Timber.d("[WebRTC]   candidate: ${it.sdp}")
                        onIceCandidateCallback?.invoke(it)
                    } ?: Timber.d("[WebRTC] Null ICE candidate received")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    Timber.d("ICE candidates removed")
                }

                override fun onAddStream(stream: MediaStream?) {
                    // Deprecated in Unified Plan - use onAddTrack instead
                    Timber.d("Stream added (deprecated)")
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    Timber.d("Stream removed")
                }

                override fun onDataChannel(dataChannel: DataChannel?) {
                    Timber.d("Data channel created")
                }

                override fun onRenegotiationNeeded() {
                    Timber.d("Renegotiation needed")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Timber.d("Track added")
                    val track = receiver?.track()
                    when (track) {
                        is VideoTrack -> {
                            Timber.d("Remote video track added")
                            track.addSink(remoteVideoView)
                        }
                        is AudioTrack -> {
                            Timber.d("Remote audio track added")
                        }
                    }
                }
            }
        )
    }

    fun startLocalStream(isVideoEnabled: Boolean) {
        // Audio track 생성
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local_stream"))
        }

        // Video track 생성 (비디오가 활성화된 경우)
        if (isVideoEnabled) {
            startVideoCapture()
        }
    }

    private fun startVideoCapture() {
        videoCapturer = createCameraCapturer()
        videoCapturer?.let { capturer ->
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                EglBase.create().eglBaseContext
            )
            
            val videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )
            
            capturer.startCapture(1280, 720, 30)
            
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
            localVideoTrack?.addSink(localVideoView)
            localVideoTrack?.let { track ->
                peerConnection?.addTrack(track, listOf("local_stream"))
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        
        // 전면 카메라 찾기
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // 전면 카메라가 없으면 후면 카메라 사용
        for (deviceName in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        return null
    }

    suspend fun createOffer(): SessionDescription? {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        return suspendCancellableCoroutine { continuation ->
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    sessionDescription?.let {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                continuation.resume(sessionDescription)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {
                                continuation.resume(null)
                            }
                        }, it)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Timber.e("Failed to create offer: $error")
                    continuation.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    suspend fun createAnswer(): SessionDescription? {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        return suspendCancellableCoroutine { continuation ->
            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    sessionDescription?.let {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                continuation.resume(sessionDescription)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {
                                continuation.resume(null)
                            }
                        }, it)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Timber.e("Failed to create answer: $error")
                    continuation.resume(null)
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        Timber.d("[WebRTC] Setting remote description...")
        Timber.d("[WebRTC] Type: ${sessionDescription.type}")
        Timber.d("[WebRTC] SDP length: ${sessionDescription.description.length}")
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                Timber.d("[WebRTC] onCreateSuccess called (unexpected)")
            }
            override fun onSetSuccess() {
                Timber.d("[WebRTC] Remote description set successfully!")
            }
            override fun onCreateFailure(p0: String?) {
                Timber.e("[WebRTC] onCreateFailure called: $p0")
            }
            override fun onSetFailure(error: String?) {
                Timber.e("[WebRTC] Failed to set remote description: $error")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        Timber.d("[WebRTC] Adding ICE candidate...")
        Timber.d("[WebRTC]   sdpMid: ${iceCandidate.sdpMid}")
        Timber.d("[WebRTC]   sdpMLineIndex: ${iceCandidate.sdpMLineIndex}")
        Timber.d("[WebRTC]   candidate: ${iceCandidate.sdp}")
        
        val result = peerConnection?.addIceCandidate(iceCandidate)
        Timber.d("[WebRTC] Add ICE candidate result: $result")
    }

    fun setMicrophoneMute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = speakerOn
    }

    fun setVideoEnabled(enabled: Boolean) {
        if (enabled) {
            if (localVideoTrack == null) {
                startVideoCapture()
            } else {
                localVideoTrack?.setEnabled(true)
            }
        } else {
            localVideoTrack?.setEnabled(false)
        }
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun endCall() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        
        localVideoView?.release()
        remoteVideoView?.release()
        
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = false
    }

    fun release() {
        endCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }

    fun setOnIceCandidateCallback(callback: (IceCandidate) -> Unit) {
        onIceCandidateCallback = callback
    }

    fun setOnConnectionStateChangeCallback(callback: (String) -> Unit) {
        onConnectionStateChangeCallback = callback
    }
}