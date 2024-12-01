package com.example.serverapp



import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serverapp.ui.theme.ServerAppTheme
import helpers.SharedPreferencesHelper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.serverapp.ui.Select
import com.example.serverapp.viewmodel.MainActivityViewModel
import database.AppDatabase
import helpers.FileHandlerHelper


class MainActivity : ComponentActivity() {


    private val prefHandler by lazy { SharedPreferencesHelper(this) }
    private var isServerRunning by mutableStateOf(false)
    private var showAlertDialog by mutableStateOf(false)
    private var backEndUrl by mutableStateOf<String?>(null)
    private var frontEndUrl by mutableStateOf<String?>(null)
    private var uiServerMode by mutableStateOf<Boolean?>(null)
    private val fileHandlerHelper by lazy { FileHandlerHelper(this) }
    private val SERVER_START_ACTION_NAME="SERVER_START"
    private val SERVER_STOP_ACTION_NAME="SERVER_STOP"
    private val database  by lazy { AppDatabase.getDatabase(this) }
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
        }}

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
        frontEndUrl=prefHandler.getFrontEndUrl()
        uiServerMode=prefHandler.getUIServerMode()
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

                            Info()
                            FrontEndServer()
                            BackendServer()
                            Select(mainActivityViewModel,requestPermissionLauncherSDCard,requestPermissionLauncherInternal, fileHandlerHelper)
                    }
                }
            }
        }
    }


    @Composable
    fun ServerCard(
        serverName: String,
        url: String?,
        buttonText: String,
        buttonAction: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServerImage()
                    Spacer(Modifier.weight(1f))
                    StyledText(text = serverName)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledText(text = "IP: ")
                    Spacer(Modifier.weight(1f))
                    DisplayIP(address = url)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = buttonAction,
                        modifier = Modifier
                            .padding(5.dp)
                            .width(180.dp)
                    ) {
                        StyledText(text = buttonText)
                    }
                }
            }
        }
    }

    @Composable
    fun Info(){
        val rowCount by database.fileDao().getTotalFileCountLive().asFlow().collectAsStateWithLifecycle(
            initialValue = 0
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ){
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledText(text = "Total rows in database:")
                    Spacer(Modifier.weight(1f))
                    StyledText(text = "$rowCount")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val uiServerModeText="UI requests: ${if (uiServerMode==true) "Next.JS Server" else "Static Assets" }";
                    StyledText(text = uiServerModeText)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = uiServerMode==true,
                        onCheckedChange = { newValue ->
                            uiServerMode = newValue
                            prefHandler.storeUIServerMode(newValue)  // Store preference
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun FrontEndServer() {
        ServerCard(
            serverName = "Front End Server",
            url = frontEndUrl,
            buttonText = "Edit Address",
            buttonAction = { ->showAlertDialog=true }
        )
        if(showAlertDialog){
            MinimalDialog(onDismissRequest = {->showAlertDialog=false},
                onSave = {frontEndUrlFromDialogBox->
                frontEndUrl=frontEndUrlFromDialogBox
                prefHandler.storeFrontEndUrl(frontEndUrl)
            })
        }
    }

    @Composable
    fun MinimalDialog(onDismissRequest: () -> Unit, onSave: (String) -> Unit) {
        var addressInput by remember { mutableStateOf(frontEndUrl ?: "") }

        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = 10.dp, bottom = 10.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Edit Front End Address",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        textAlign = TextAlign.Center,
                    )
                    TextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        label = { Text("Front End Address") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onDismissRequest() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onSave(addressInput)
                                onDismissRequest()
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BackendServer( ) {
        ServerCard(
            serverName = "Back End Server",
            url = backEndUrl,
            buttonText = if (isServerRunning) "Stop Server" else "Start Server",
            buttonAction = {
                    ->if (!isServerRunning)
                        startServer()
                    else stopServer()

            }
        )
    }
    private fun startServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        startService(serviceIntent)
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



    @Composable
    fun ServerImage() {
        Image(painter = painterResource(id = R.drawable.web), contentDescription = "Image of a Web", colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary), modifier = Modifier.size(30.dp))
    }

    @Composable
    fun DisplayIP(address:String?) {
        if(address!=null){
            StyledText(text = address)
        }else{
            StyledText(text = "NULL")
        }
    }


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if(intent.action == SERVER_START_ACTION_NAME){
                    backEndUrl=prefHandler.getBackEndUrl()
                    isServerRunning=true
                }
                if(intent.action == SERVER_STOP_ACTION_NAME){
                    backEndUrl=null;
                    prefHandler.storeBackEndUrl(null)
                    isServerRunning=false;
                }

            }
        }
    }

    @Composable
    fun StyledText(text: String) {

            Text(
                text = text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(5.dp),
                fontWeight = FontWeight.Bold,
                fontSize=15.sp
            )
    }

}