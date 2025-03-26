package com.example.serverapp



import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.serverapp.ui.theme.ServerAppTheme
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import com.example.serverapp.ui.homeview.BackendServer
import com.example.serverapp.ui.homeview.FrontEndServer
import com.example.serverapp.ui.homeview.Info
import com.example.serverapp.ui.homeview.Select
import com.example.serverapp.viewmodel.MainActivityViewModel
import helpers.FileHandlerHelper


class MainActivity : ComponentActivity() {


    private val fileHandlerHelper by lazy { FileHandlerHelper(this) }
    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    private val mainActivityViewModel: MainActivityViewModel by viewModels()


    private val requestPermissionLauncherSDCard = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            mainActivityViewModel.saveSDCardUri(uri)
    }}

    private val requestPermissionLauncherInternal = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            mainActivityViewModel.saveInternalUri(uri)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverIntentFilter = IntentFilter().apply {
            addAction(SERVER_START_ACTION_NAME)
            addAction(SERVER_STOP_ACTION_NAME)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, serverIntentFilter, Context.RECEIVER_EXPORTED)

        } else {
            registerReceiver(receiver, serverIntentFilter)

        }

        setContent {
            ServerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column (
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )  {

                            Info(mainActivityViewModel)
                            FrontEndServer(mainActivityViewModel)
                            BackendServer(mainActivityViewModel,::startServer,::stopServer)
                            Select(mainActivityViewModel,requestPermissionLauncherSDCard,requestPermissionLauncherInternal, fileHandlerHelper)
                    }
                }
            }
        }
    }


    private fun startServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun stopServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        stopService(serviceIntent)
    }

    override fun onDestroy()  {
        super.onDestroy()
        stopServer()
        unregisterReceiver(receiver)
    }





    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if(intent.action == SERVER_START_ACTION_NAME|| intent.action == SERVER_STOP_ACTION_NAME){
                    mainActivityViewModel.updateBackEndUrl()
                    mainActivityViewModel.updateServerRunning()
                }
            }
        }
    }

}