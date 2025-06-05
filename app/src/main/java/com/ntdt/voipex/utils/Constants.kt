package com.ntdt.voipex.utils

object Constants {
    // API Gateway (로컬 테스트용)
    private const val API_GATEWAY = "http://192.168.0.10:8080"
    
    // Server URLs
    const val SIGNALING_SERVER_URL = "http://192.168.0.10:3000"
    
    // WebRTC Configuration
    const val STUN_SERVER_URL = "stun:192.168.0.10:3478"
    val TURN_SERVER_URLS = listOf(
        "turn:192.168.0.10:3478?transport=udp",
        "turn:192.168.0.10:3478?transport=tcp"
    )
    const val TURN_USERNAME = "voipex"
    const val TURN_CREDENTIAL = "voipex123"
    
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