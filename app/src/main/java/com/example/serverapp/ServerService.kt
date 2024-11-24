package com.example.serverapp
import helpers.NetworkHelper
import MKHttpServer
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import database.AppDatabase
import helpers.SharedPreferencesHelper

class ServerService: Service() {

    private lateinit var serverHandler: MKHttpServer
    private val networkHandler by lazy { NetworkHelper() }
    private val prefHandler by lazy { SharedPreferencesHelper(this) }
    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    override fun onCreate() {
        super.onCreate()
        serverHandler = MKHttpServer(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            startServer()
        }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
        if (serverHandler.isAlive()) {
            serverHandler.stop()
            val broadcastIntent = Intent(SERVER_STOP_ACTION_NAME)
            prefHandler.storeBackEndUrl(null)
            sendBroadcast(broadcastIntent)
            Log.d("MKServer", "Server Stopped")
        }
    }

}
