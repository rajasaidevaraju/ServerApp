package com.example.serverapp.ui.homeview

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.serverapp.R
import com.example.serverapp.UserManagementActivity
import com.example.serverapp.ui.CommonButton
import com.example.serverapp.ui.StyledCard
import com.example.serverapp.ui.StyledText
import com.example.serverapp.viewmodel.MainActivityViewModel

@Composable
fun Info(mainActivityViewModel: MainActivityViewModel){
    val rowCount by mainActivityViewModel.rowCount.observeAsState(null)
    val uiServerMode by mainActivityViewModel.uiServerMode.observeAsState(null)
    val context = LocalContext.current

    StyledCard {

        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledText(text = "Total rows in database:")
            Spacer(Modifier.weight(1f))
            Text(text = "$rowCount")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val uiServerModeText =
                "UI requests: ${if (uiServerMode == true) "Next.JS Server" else "Static Assets"}"
            StyledText(text = uiServerModeText)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = uiServerMode == true,
                onCheckedChange = { newValue ->
                    mainActivityViewModel.setUIServerMode(newValue)
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledText(text = "Manage Users:")
            Spacer(Modifier.weight(1f))
            CommonButton(buttonText = "Manage",
                onClick = {
                    val intent = Intent(context, UserManagementActivity::class.java)
                    context.startActivity(intent)
                }, longPressToastMessage = "Manage Users"
            )
        }
    }

}

@Composable
fun ServerCard(
    serverName: String,
    url: String?,
    buttonText: String,
    longPressToastMessage: String,
    buttonAction: () -> Unit
) {
    StyledCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerImage()
            Spacer(Modifier.weight(1f))
            StyledText(text = serverName)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledText(text = "IP: ")
            DisplayIP(address = url)
            Spacer(Modifier.weight(1f))
            CommonButton(onClick = buttonAction, buttonText = buttonText,longPressToastMessage=longPressToastMessage)
        }

    }
}

@Composable
fun FrontEndServer(mainActivityViewModel: MainActivityViewModel) {
    val frontEndUrl by mainActivityViewModel.frontEndUrl.observeAsState(null)
    var showDialog by remember { mutableStateOf(false) }
    ServerCard(
        serverName = "Front End Server",
        url = frontEndUrl,
        buttonText = "Edit Address",
        longPressToastMessage = "Modify front end address",
        buttonAction = { ->showDialog = true  }
    )
    // TODO: add validation to the below input
    if(showDialog){
        MinimalDialog( frontEndUrl = frontEndUrl,onDismissRequest = {->showDialog=false},
            onSave = {frontEndUrlFromDialogBox:String->
                mainActivityViewModel.setFrontEndUrl(frontEndUrlFromDialogBox)
            })
    }
}

@Composable
fun MinimalDialog(frontEndUrl: String?,onDismissRequest: () -> Unit, onSave: (String) -> Unit) {
    var addressInput by remember { mutableStateOf(frontEndUrl ?: "") }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = 10.dp, bottom = 10.dp)

        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)

            ) {
                Text(
                    text = "Edit Front End Address",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = { Text("Address") }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onDismissRequest() },
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text("Cancel")
                    }
                    CommonButton( buttonText = "Save",
                        onClick = {
                            onSave(addressInput)
                            onDismissRequest()
                        }, longPressToastMessage = "Save front end address"
                    )
                }
            }
        }
    }
}

@Composable
fun BackendServer(mainActivityViewModel: MainActivityViewModel, startServer: () -> Unit,
                  stopServer: () -> Unit ) {
    val backEndUrl by mainActivityViewModel.backEndUrl.observeAsState(null)
    val isServerRunning by mainActivityViewModel.isServerRunning.observeAsState(false)
    ServerCard(
        serverName = "Back End Server",
        url = backEndUrl,
        buttonText = if (isServerRunning) "Stop Server" else "Start Server",
        longPressToastMessage = "Toggle Server State",
        buttonAction = {
            ->if (!isServerRunning)
            startServer()
        else stopServer()

        }
    )
}


@Composable
fun ServerImage() {
    Image(painter = painterResource(id = R.drawable.web), contentDescription = "Image of a Web", colorFilter = ColorFilter.tint(
        MaterialTheme.colorScheme.primary), modifier = Modifier.size(30.dp))
}

@Composable
fun DisplayIP(address:String?) {
    if(address!=null){
        Text(text = address)
    }else{
        Text(text = "NULL")
    }
}