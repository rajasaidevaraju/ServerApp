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
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import com.example.serverapp.viewmodel.MainActivityViewModel
import androidx.core.net.toUri
import com.example.serverapp.ui.homeview.HomeScreen
import com.example.serverapp.ui.homeview.RequestPermission
import helpers.SharedPreferencesHelper


class MainActivity : ComponentActivity() {


    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    private val mainActivityViewModel: MainActivityViewModel by viewModels()
    private lateinit var preferencesHelper: SharedPreferencesHelper
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
        preferencesHelper= SharedPreferencesHelper(this)
        preferencesHelper.setupFolders()
        setContent {
            ServerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by mainActivityViewModel.hasPermission.observeAsState(Environment.isExternalStorageManager())
                    if (!hasPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RequestPermission(::launchPermission)
                        }
                    } else {
                        HomeScreen(
                            mainActivityViewModel = mainActivityViewModel,
                            startServer = ::startServer,
                            stopServer = ::stopServer,
                            exportDbAction = ::exportDatabase,
                            importDbLauncher = importDbLauncher
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The service may have started/stopped/died while the activity was away
        mainActivityViewModel.refreshServerState()
    }

    private fun startServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun stopServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        stopService(serviceIntent)
    }

    private val manageAllFilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        mainActivityViewModel.checkPermission()
    }

    private fun launchPermission(){
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:${this.packageName}".toUri()
        manageAllFilesLauncher.launch(intent)
    }

    private val importDbLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            mainActivityViewModel.importDatabase(it)
        }
    }

    private fun exportDatabase() {
        mainActivityViewModel.exportDatabase(applicationContext, "server_app_db_backup_${System.currentTimeMillis()}.db")
    }

    override fun onDestroy()  {
        super.onDestroy()
        // The server intentionally keeps running in the background; it is only
        // stopped via the Stop Server button or the service notification.
        unregisterReceiver(receiver)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if(intent.action == SERVER_START_ACTION_NAME|| intent.action == SERVER_STOP_ACTION_NAME){
                    mainActivityViewModel.refreshServerState()
                }
            }
        }
    }

}