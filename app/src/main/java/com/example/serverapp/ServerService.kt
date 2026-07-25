package com.example.serverapp
import helpers.NetworkHelper
import MKHttpServer
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import helpers.SharedPreferencesHelper

class ServerService: Service() {

    companion object {
        // Read by the UI to know the real service state; a killed process resets
        // this to false, unlike the persisted back-end url pref.
        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var serverHandler: MKHttpServer
    private val networkHandler by lazy { NetworkHelper() }
    private val prefHandler by lazy { SharedPreferencesHelper(this) }
    private val notificationID=101
    private lateinit var waveLock:PowerManager.WakeLock
    private var wifiLock: WifiManager.WifiLock? = null
    private var serverThread: Thread? = null

    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    private val LEGACY_CHANNEL_ID = "ServerServiceChannel"
    private val CHANNEL_ID = "ServerServiceChannelQuiet"
    private val TITLE="Server Running"

    override fun onCreate() {
        super.onCreate()
        serverHandler = MKHttpServer(applicationContext)
        createNotificationChannel()
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!serverHandler.isAlive) {
            startForegroundNotification()
            serverThread = Thread {
                serverHandler.start()
                isRunning = true
                updateStoredPref()
                waveLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ServerApp::Tag"
                )
                waveLock.acquire()
               @Suppress("DEPRECATION")
                wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ServerApp::WifiLock")
                wifiLock?.acquire()
            }
            serverThread?.start()
        }else{
            updateStoredPref()
            Log.d("MKServer","Attempting to start the server while it is already alive")
        }

        return START_STICKY
    }


    private fun updateStoredPref(){
        var ipAddress=networkHandler.getIpAddress(this)
        val broadcastIntent = Intent(SERVER_START_ACTION_NAME)
        if(ipAddress==null || ipAddress=="null"){
            ipAddress="localhost"
        }
        Log.d("MKServer Address","Server address $ipAddress:${serverHandler.listeningPort}")
        prefHandler.storeBackEndUrl("$ipAddress:${serverHandler.listeningPort}")
        sendBroadcast(broadcastIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (serverHandler.isAlive) {
            serverHandler.stop()
            val broadcastIntent = Intent(SERVER_STOP_ACTION_NAME)
            prefHandler.storeBackEndUrl(null)
            sendBroadcast(broadcastIntent)
            Log.d("MKServer", "Server Stopped")
        }else{
            Log.d("MKServer","Attempting to stop the server while it is already dead")
        }
        if (this::waveLock.isInitialized && waveLock.isHeld) {
            waveLock.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        wifiLock = null
        serverThread?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundNotification() {
        startForeground(notificationID, buildNotification())
    }

    private fun createNotificationChannel() {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Server Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(TITLE)
            .setSmallIcon(R.drawable.notification_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

}
