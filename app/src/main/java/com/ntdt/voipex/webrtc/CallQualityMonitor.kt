package com.ntdt.voipex.webrtc

import com.ntdt.voipex.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallQualityMonitor @Inject constructor() {
    private val _callQuality = MutableStateFlow<CallQuality>(CallQuality.Unknown)
    val callQuality: StateFlow<CallQuality> = _callQuality

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.New)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var lastStats: CallStats? = null

    fun updateConnectionState(state: PeerConnection.PeerConnectionState) {
        val newState = when (state) {
            PeerConnection.PeerConnectionState.NEW -> ConnectionState.New
            PeerConnection.PeerConnectionState.CONNECTING -> ConnectionState.Connecting
            PeerConnection.PeerConnectionState.CONNECTED -> ConnectionState.Connected
            PeerConnection.PeerConnectionState.DISCONNECTED -> ConnectionState.Disconnected
            PeerConnection.PeerConnectionState.FAILED -> ConnectionState.Failed
            PeerConnection.PeerConnectionState.CLOSED -> ConnectionState.Closed
        }
        _connectionState.value = newState
        Timber.d("Connection state changed to: $newState")
    }

    fun processStats(report: RTCStatsReport) {
        // Process WebRTC stats and update call quality metrics
        val currentTime = System.currentTimeMillis()
        val audioLevel = extractAudioLevel(report)
        val packetLoss = calculatePacketLoss(report)
        val bitrate = calculateBitrate(report)
        
        lastStats = CallStats(
            timestamp = currentTime,
            audioLevel = audioLevel,
            packetLoss = packetLoss,
            bitrate = bitrate
        )
    }

    private fun extractAudioLevel(report: RTCStatsReport): Double {
        // Extract audio level from stats
        return 0.0 // TODO: Implement
    }
    
    private fun calculatePacketLoss(report: RTCStatsReport): Double {
        // Calculate packet loss from stats
        return 0.0 // TODO: Implement
    }
    
    private fun calculateBitrate(report: RTCStatsReport): Double {
        // Calculate bitrate from stats
        return 0.0 // TODO: Implement
    }

    fun getLastStats(): CallStats? = lastStats

    fun processStatsReport(report: RTCStatsReport) {
        var roundTripTime = 0.0
        var packetLoss = 0.0
        var audioLevel = 0.0
        var videoBitrate = 0L
        var audioBitrate = 0L

        report.statsMap.values.forEach { stats ->
            when (stats.type) {
                "candidate-pair" -> {
                    roundTripTime = stats.members["currentRoundTripTime"] as? Double ?: 0.0
                }
                "outbound-rtp" -> {
                    val mediaType = stats.members["mediaType"] as? String
                    val bytesSent = stats.members["bytesSent"] as? Long ?: 0L
                    val currentTimestamp = stats.members["timestamp"] as? Double ?: 0.0
                    
                    if (mediaType == "video") {
                        videoBitrate = calculateBitrate(bytesSent, currentTimestamp.toLong())
                    } else if (mediaType == "audio") {
                        audioBitrate = calculateBitrate(bytesSent, currentTimestamp.toLong())
                    }
                }
                "media-source" -> {
                    val mediaType = stats.members["mediaType"] as? String
                    if (mediaType == "audio") {
                        audioLevel = stats.members["audioLevel"] as? Double ?: 0.0
                    }
                }
            }
        }

        val quality = when {
            roundTripTime > Constants.POOR_NETWORK_THRESHOLD_MS -> CallQuality.Poor
            packetLoss > Constants.PACKET_LOSS_THRESHOLD -> CallQuality.Poor
            videoBitrate < Constants.VIDEO_BITRATE / 2 -> CallQuality.Fair
            audioBitrate < Constants.AUDIO_BITRATE / 2 -> CallQuality.Fair
            else -> CallQuality.Good
        }

        _callQuality.value = quality
        logCallStats(roundTripTime, packetLoss, audioLevel, videoBitrate, audioBitrate)
    }

    private fun calculateBitrate(bytes: Long, timestamp: Long): Long {
        // Implementation for bitrate calculation
        return bytes * 8 // Simple conversion to bits
    }

    private fun logCallStats(
        rtt: Double,
        packetLoss: Double,
        audioLevel: Double,
        videoBitrate: Long,
        audioBitrate: Long
    ) {
        if (Constants.DEBUG_MODE) {
            Timber.d("""
                Call Stats:
                - RTT: $rtt ms
                - Packet Loss: $packetLoss%
                - Audio Level: $audioLevel
                - Video Bitrate: $videoBitrate bps
                - Audio Bitrate: $audioBitrate bps
            """.trimIndent())
        }
    }
}

enum class CallQuality {
    Unknown,
    Poor,
    Fair,
    Good
}

enum class ConnectionState {
    New,
    Connecting,
    Connected,
    Disconnected,
    Failed,
    Closed
}

data class CallStats(
    val timestamp: Long,
    val audioLevel: Double,
    val packetLoss: Double,
    val bitrate: Double
) 