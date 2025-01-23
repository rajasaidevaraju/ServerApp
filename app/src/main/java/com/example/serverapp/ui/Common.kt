package com.example.serverapp.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.serverapp.R
import com.example.serverapp.viewmodel.MainActivityViewModel

@Composable
fun Info(mainActivityViewModel: MainActivityViewModel){
    val rowCount by mainActivityViewModel.rowCount.observeAsState(null)
    val uiServerMode by mainActivityViewModel.uiServerMode.observeAsState(null)

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
                Text(text = "$rowCount")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val uiServerModeText="UI requests: ${if (uiServerMode==true) "Next.JS Server" else "Static Assets" }"
                StyledText(text = uiServerModeText)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = uiServerMode==true,
                    onCheckedChange = { newValue ->
                        mainActivityViewModel.setUIServerMode(newValue)
                    }
                )
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
                    onClick = buttonAction
                ) {
                    StyledText(text = buttonText)
                }
            }
        }
    }
}

@Composable
fun FrontEndServer(mainActivityViewModel: MainActivityViewModel) {
    val frontEndUrl by mainActivityViewModel.frontEndUrl.observeAsState(null)
    val showAlertDialog by mainActivityViewModel.showAlertDialog.observeAsState(false)
    ServerCard(
        serverName = "Front End Server",
        url = frontEndUrl,
        buttonText = "Edit Address",
        buttonAction = { ->mainActivityViewModel.setShowAlertDialog(true) }
    )
    if(showAlertDialog){
        MinimalDialog( frontEndUrl = frontEndUrl,onDismissRequest = {->mainActivityViewModel.setShowAlertDialog(false)},
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
fun BackendServer(mainActivityViewModel:MainActivityViewModel,startServer: () -> Unit,
                  stopServer: () -> Unit ) {
    val backEndUrl by mainActivityViewModel.backEndUrl.observeAsState(null)
    val isServerRunning by mainActivityViewModel.isServerRunning.observeAsState(false)
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


@Composable
fun ServerImage() {
    Image(painter = painterResource(id = R.drawable.web), contentDescription = "Image of a Web", colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary), modifier = Modifier.size(30.dp))
}

@Composable
fun DisplayIP(address:String?) {
    if(address!=null){
        Text(text = address)
    }else{
        Text(text = "NULL")
    }
}

@Composable
private fun StyledText(text: String) {

    Text(
        text = text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(5.dp),
        fontWeight = FontWeight.Bold,
        fontSize=15.sp
    )
}
