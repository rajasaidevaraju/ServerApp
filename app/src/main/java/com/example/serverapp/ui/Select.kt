package com.example.serverapp.ui


import android.content.Context
import android.content.Context.STORAGE_SERVICE
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import com.example.serverapp.viewmodel.MainActivityViewModel
import helpers.FileHandlerHelper


@Composable
fun Select(
    mainActivityViewModel: MainActivityViewModel,
    requestPermissionLauncherSDCard: ActivityResultLauncher<Uri?>,
    requestPermissionLauncherInternal: ActivityResultLauncher<Uri?>,
    fileHandlerHelper: FileHandlerHelper
) {

    val sdCardUri by mainActivityViewModel.sdCardUri.observeAsState(null)
    val internalUri by mainActivityViewModel.internalUri.observeAsState(null)
    StyledCard {

        val isSdCardAvailable = fileHandlerHelper.isSdCardAvailable()
        val sdCardFolderName = fileHandlerHelper.getFolderNameFromUri(sdCardUri)
            ?: "SD Card Folder not selected"

        if (isSdCardAvailable) {
            StyledText("SD Card Folder:")

            StyledRow {
                Text(
                    sdCardFolderName,
                    modifier = Modifier.padding(5.dp),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(10.dp))
                FolderSelectButton(requestPermissionLauncherSDCard, sdCardUri != null)
            }
        }

        if (isSdCardAvailable) {
            Divider(color = Color.LightGray, thickness = 1.dp)
        }


        StyledText("Internal Memory Folder:")

        val internalFolderName = fileHandlerHelper.getFolderNameFromUri(internalUri)
            ?: "Internal Storage Folder not selected"

        StyledRow {
            Text(
                internalFolderName,
                modifier = Modifier.padding(5.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.width(10.dp))

            FolderSelectButton(requestPermissionLauncherInternal, internalUri != null)
        }
    }
}



@Composable
fun FolderSelectButton(requestPermissionLauncher: ActivityResultLauncher<Uri?>,isFolderSelected:Boolean) {

    val text= if (isFolderSelected) "Update Folder" else "Select Folder"


    Button(
        onClick = {
            requestPermissionLauncher.launch(null)
        }
    ) {

        StyledText(text = text)
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

@Composable
private fun NewLine(){
    Row{

    }
}
