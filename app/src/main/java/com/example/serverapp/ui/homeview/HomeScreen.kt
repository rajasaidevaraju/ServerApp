package com.example.serverapp.ui.homeview
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.serverapp.R
import com.example.serverapp.UserManagementActivity
import com.example.serverapp.ui.CommonButton
import com.example.serverapp.ui.StatusPill
import com.example.serverapp.ui.StyledCard
import com.example.serverapp.viewmodel.MainActivityViewModel

@Composable
fun HomeScreen(
    mainActivityViewModel: MainActivityViewModel,
    startServer: () -> Unit,
    stopServer: () -> Unit,
    exportDbAction: () -> Unit,
    importDbLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        HomeHeader()
        BackendServer(mainActivityViewModel, startServer, stopServer)
        FrontEndServer(mainActivityViewModel)
        Info(mainActivityViewModel)
        DatabaseManagement(mainActivityViewModel, exportDbAction, importDbLauncher)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun HomeHeader() {
    Text(
        text = "Media Server",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp)
    )
}

@Composable
fun Info(mainActivityViewModel: MainActivityViewModel){
    val rowCount by mainActivityViewModel.rowCount.observeAsState(0)
    val context = LocalContext.current

    StyledCard(title = "Library") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Files in database", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = "$rowCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "User accounts", style = MaterialTheme.typography.bodyLarge)
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
fun FrontEndServer(mainActivityViewModel: MainActivityViewModel) {
    val frontEndUrl by mainActivityViewModel.frontEndUrl.observeAsState(null)
    var showDialog by remember { mutableStateOf(false) }

    StyledCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerImage()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Front End Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = frontEndUrl ?: "Not configured",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            CommonButton(
                buttonText = "Edit",
                onClick = { showDialog = true },
                longPressToastMessage = "Modify front end address"
            )
        }
    }
    if(showDialog){
        MinimalDialog( frontEndUrl = frontEndUrl,onDismissRequest = {showDialog=false},
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
                    .padding(16.dp),
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

    StyledCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerImage()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Back End Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isServerRunning) (backEndUrl ?: "Starting…") else "Not running",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isServerRunning) FontFamily.Monospace else FontFamily.Default,
                    color = if (isServerRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(active = isServerRunning)
        }
        CommonButton(
            buttonText = if (isServerRunning) "Stop Server" else "Start Server",
            onClick = { if (!isServerRunning) startServer() else stopServer() },
            longPressToastMessage = "Toggle Server State",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RequestPermission(onRequestPermission: () -> Unit){
    StyledCard(title = "Permission Required") {
        Text(
            text = "This app needs access to all files to serve your media library.",
            style = MaterialTheme.typography.bodyLarge
        )
        CommonButton(
            buttonText = "Launch Settings",
            onClick = { onRequestPermission() },
            longPressToastMessage = "Grant file access permission",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DatabaseManagement(
    mainActivityViewModel: MainActivityViewModel,
    exportDbAction: () -> Unit,
    importDbLauncher: ActivityResultLauncher<Array<String>>
) {
    StyledCard(title = "Database") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Export to Downloads", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            CommonButton(
                buttonText = "Export",
                onClick = exportDbAction,
                longPressToastMessage = "Export Database to Backup File"
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Import from backup", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            CommonButton(
                buttonText = "Import",
                onClick = { importDbLauncher.launch(arrayOf("*/*")) },
                longPressToastMessage = "Import/Restore Database from Backup File"
            )
        }
        val databaseActionStatus by mainActivityViewModel.databaseActionStatus.observeAsState("")
        if (databaseActionStatus.isNotEmpty()) {
            Text(
                text = databaseActionStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ServerImage() {
    Image(painter = painterResource(id = R.drawable.web), contentDescription = "Image of a Web", colorFilter = ColorFilter.tint(
        MaterialTheme.colorScheme.primary), modifier = Modifier.size(30.dp))
}
