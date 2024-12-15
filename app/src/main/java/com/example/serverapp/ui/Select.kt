package com.example.serverapp.ui


import android.content.Context
import android.content.Context.STORAGE_SERVICE
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import com.example.serverapp.viewmodel.MainActivityViewModel
import helpers.FileHandlerHelper


@Composable
fun Select(mainActivityViewModel: MainActivityViewModel, requestPermissionLauncherSDCard: ActivityResultLauncher<Uri?>, requestPermissionLauncherInternal: ActivityResultLauncher<Uri?>, fileHandlerHelper:FileHandlerHelper){

    val sdCardUri by mainActivityViewModel.sdCardUri.observeAsState(null)
    val internalUri by mainActivityViewModel.internalUri.observeAsState(null)
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

            val sdCardFolderName=fileHandlerHelper.getFolderNameFromUri(sdCardUri)
            if(fileHandlerHelper.isSdCardAvailable()){

                StyledText("SD Card Folder:")
                if(sdCardFolderName==null){
                    FolderSelectButton(requestPermissionLauncherSDCard)
                }else{
                    Text(sdCardFolderName)
                }
                Text("")
            }
            val internalFolderName=fileHandlerHelper.getFolderNameFromUri(internalUri)

            StyledText("Internal Memory Folder:")
            if(internalFolderName==null){
                FolderSelectButton(requestPermissionLauncherInternal)
            }else{
                Text(internalFolderName)
            }

        }
    }
}



@Composable
fun FolderSelectButton(requestPermissionLauncher: ActivityResultLauncher<Uri?>) {
    Button(
        onClick = {
            requestPermissionLauncher.launch(null)
        }
    ) {
        StyledText(text = "Select Folder")
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
