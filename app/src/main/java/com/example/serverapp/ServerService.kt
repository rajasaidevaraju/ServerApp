package com.example.serverapp
import helpers.NetworkHelper
import MKHttpServer
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateUtils.formatElapsedTime
import android.util.Log
import androidx.core.app.NotificationCompat
import database.AppDatabase
import helpers.SharedPreferencesHelper
import java.util.Locale

class ServerService: Service() {

    private lateinit var serverHandler: MKHttpServer
    private val networkHandler by lazy { NetworkHelper() }
    private val prefHandler by lazy { SharedPreferencesHelper(this) }
    private var startTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var waveLock:PowerManager.WakeLock
    private val updateRunnable = object : Runnable {
        override fun run() {
            val elapsedTime = SystemClock.elapsedRealtime() - startTime
            val elapsedTimeFormatted = formatElapsedTime(elapsedTime)
            updateNotification(elapsedTimeFormatted)
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    private val CHANNEL_ID = "ServerServiceChannel"
    private val TITLE="Server Running"

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        serverHandler = MKHttpServer(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            startServer()
            startTime = SystemClock.elapsedRealtime()
            handler.post(updateRunnable)
            waveLock=(getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ServerApp::Tag")
            waveLock.acquire()
        }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        stopServer()
        if (this::waveLock.isInitialized && waveLock.isHeld) {
            waveLock.release()
        }

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Server Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        val initialNotification = buildNotification(TITLE, "Elapsed time: 0:00")

        startForeground(101, initialNotification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.new_launcher_foreground) // Ensure this icon is valid
            .build()
    }

    private fun updateNotification(elapsedTime: String) {
        var isHeld=false
        if(::waveLock.isInitialized && waveLock.isHeld ){
            isHeld=true
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = buildNotification(TITLE, "Elapsed time: $elapsedTime\nWavelock Acquired:${isHeld}")
        notificationManager.notify(101, notification)
    }

    private fun formatElapsedTime(elapsedTime: Long): String {
        val seconds = (elapsedTime / 1000) % 60
        val minutes = (elapsedTime / (1000 * 60)) % 60
        val hours = (elapsedTime / (1000 * 60 * 60)) % 24
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startServer() {
        if (!serverHandler.isAlive) {
            serverHandler.start()
            val ipAddress=networkHandler.getIpAddress(this)
            val broadcastIntent = Intent(SERVER_START_ACTION_NAME)
            prefHandler.storeBackEndUrl("$ipAddress:${serverHandler.listeningPort}")
            sendBroadcast(broadcastIntent)
            if(ipAddress!=null){
                Log.d("MKServer Address","Server Started with $ipAddress:${serverHandler.listeningPort}")
            }else{
                Log.d("MKServer","Server live status:${serverHandler.isAlive}")
            }
        }
    }


    private fun stopServer() {
        if (serverHandler.isAlive) {
            serverHandler.stop()
            val broadcastIntent = Intent(SERVER_STOP_ACTION_NAME)
            prefHandler.storeBackEndUrl(null)
            sendBroadcast(broadcastIntent)
            Log.d("MKServer", "Server Stopped")
        }
    }

}
