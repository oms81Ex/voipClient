package com.ntdt.voipex.utils

object Constants {
    // API Gateway (서버 환경)
    // 에뮬레이터에서는 10.0.2.2가 호스트 머신의 localhost를 가리킴
    private const val API_GATEWAY = "http://10.0.2.2:3000"
    
    // Server URLs
    const val SIGNALING_SERVER_URL = "http://10.0.2.2:3004"
    
    // WebRTC Configuration
    const val STUN_SERVER_URL = "stun:stun.l.google.com:19302"
    val TURN_SERVER_URLS = listOf(
        "turn:openrelay.metered.ca:80",
        "turn:openrelay.metered.ca:443",
        "turn:openrelay.metered.ca:443?transport=tcp"
    )
    const val TURN_USERNAME = "openrelayproject"
    const val TURN_CREDENTIAL = "openrelayproject"
    
    // Video Configuration
    const val VIDEO_WIDTH = 1280
    const val VIDEO_HEIGHT = 720
    const val VIDEO_FPS = 30
    const val VIDEO_BITRATE = 1500000 // 1.5 Mbps
    const val AUDIO_BITRATE = 32000 // 32 kbps
    
    // Network Quality Thresholds
    const val POOR_NETWORK_THRESHOLD_MS = 300 // Round trip time threshold
    const val PACKET_LOSS_THRESHOLD = 5.0 // Packet loss percentage threshold
    
    // Debug
    const val DEBUG_MODE = true
    
    // API Endpoints
    const val AUTH_ENDPOINT = "$API_GATEWAY/auth"
    const val GUEST_LOGIN_ENDPOINT = "$AUTH_ENDPOINT/guest"
    
    const val SOCKET_URL = "https://your-signaling-server.com"
    const val API_URL = "https://your-api-server.com"
    
    const val CALL_TIMEOUT = 30000L // 30 seconds
    const val RECONNECT_INTERVAL = 5000L // 5 seconds
    
    const val NOTIFICATION_CHANNEL_ID = "voipex_channel"
    const val NOTIFICATION_CHANNEL_NAME = "VoipEx Calls"
    const val CALL_NOTIFICATION_ID = 1001
    
    // Define permissions as individual constants
    const val PERMISSION_CAMERA = android.Manifest.permission.CAMERA
    const val PERMISSION_RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
    const val PERMISSION_BLUETOOTH = android.Manifest.permission.BLUETOOTH
    const val PERMISSION_BLUETOOTH_CONNECT = android.Manifest.permission.BLUETOOTH_CONNECT
    const val PERMISSION_POST_NOTIFICATIONS = android.Manifest.permission.POST_NOTIFICATIONS
} 