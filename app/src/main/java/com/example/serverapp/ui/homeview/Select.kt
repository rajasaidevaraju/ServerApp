package com.example.serverapp.ui.homeview


import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.serverapp.ui.CommonButton
import com.example.serverapp.ui.StyledCard
import com.example.serverapp.ui.StyledRow
import com.example.serverapp.ui.StyledText
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
            Divider(color = Color.LightGray, thickness = 2.dp, modifier = Modifier.padding(10.dp))
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
fun FolderSelectButton(
    requestPermissionLauncher: ActivityResultLauncher<Uri?>,
    isFolderSelected: Boolean
) {
    val text = if (isFolderSelected) "Update Folder" else "Select Folder"
    CommonButton(buttonText = text, onClick = { requestPermissionLauncher.launch(null) })
}


