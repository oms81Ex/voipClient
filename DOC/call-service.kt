// File: service/CallService.kt
package com.voipex.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {
    
    @Inject lateinit var notificationManager: CallNotificationManager
    
    private var callTimer: Timer? = null
    private var callDuration = 0
    private var remoteUserName: String = "Unknown"
    
    companion object {
        const val ACTION_START_CALL = "com.voipex.android.START_CALL"
        const val ACTION_END_CALL = "com.voipex.android.END_CALL"
        const val ACTION_MUTE = "com.voipex.android.MUTE"
        const val NOTIFICATION_ID = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                remoteUserName = intent.getStringExtra("remoteUserName") ?: "Unknown"
                startCall()
            }
            ACTION_END_CALL -> {
                endCall()
            }
            ACTION_MUTE -> {
                // Handle mute action
            }
        }
        return START_STICKY
    }
    
    private fun startCall() {
        val notification = notificationManager.createCallNotification(
            "Ongoing call with $remoteUserName",
            "00:00"
        )
        startForeground(NOTIFICATION_ID, notification)
        
        startCallTimer()
    }
    
    private fun startCallTimer() {
        callTimer = Timer()
        callTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                callDuration++
                updateNotification()
            }
        }, 0, 1000)
    }
    
    private fun updateNotification() {
        val minutes = callDuration / 60
        val seconds = callDuration % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)
        
        val notification = notificationManager.createCallNotification(
            "Ongoing call with $remoteUserName",
            timeString
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun endCall() {
        callTimer?.cancel()
        callTimer = null
        stopForeground(true)
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

// File: service/CallNotificationManager.kt
package com.voipex.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voipex.android.R
import com.voipex.android.presentation.ui.call.CallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        const val CHANNEL_ID = "voipex_call_channel"
        const val CHANNEL_NAME = "Ongoing Calls"
        const val INCOMING_CALL_CHANNEL_ID = "voipex_incoming_call_channel"
        const val INCOMING_CALL_CHANNEL_NAME = "Incoming Calls"
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Ongoing call channel
            val callChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for ongoing calls"
                setShowBadge(false)
            }
            
            // Incoming call channel
            val incomingCallChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                INCOMING_CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(incomingCallChannel)
        }
    }
    
    fun createCallNotification(title: String, time: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val endCallIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val muteIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_MUTE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(time)
            .setSmallIcon(R.drawable.ic_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_mute, "Mute", muteIntent)
            .addAction(R.drawable.ic_end_call, "End Call", endCallIntent)
            .build()
    }
    
    fun createIncomingCallNotification(callerName: String, callType: String): Notification {
        val acceptIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, CallActivity::class.java).apply {
                putExtra("isIncoming", true)
                putExtra("callType", callType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val declineIntent = PendingIntent.getService(
            context,
            4,
            Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
            .setContentTitle("Incoming $callType call")
            .setContentText("$callerName is calling")
            .setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(acceptIntent, true)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_call_accept, "Accept", acceptIntent)
            .addAction(R.drawable.ic_call_decline, "Decline", declineIntent)
            .build()
    }
    
    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }
    
    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }
}