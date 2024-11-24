package helpers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionsHelper(private val activity: ComponentActivity) {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private val permissionsToRequest: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    fun requestPermissions() {

        val permissionsNotGranted = permissionsNotGranted(permissionsToRequest)
        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                if(allGranted){
                    Log.d("testing","PERMISSIOIN GRANTED")
                }else{
                    Log.d("testing","PERMISSIOIN DENIED")
                }
            }

            requestPermissionLauncher.launch(permissionsNotGranted)
        }
    }

    fun requiredPermissionGranted():Boolean{
        val permissionsNotGranted = permissionsNotGranted(permissionsToRequest)
        return permissionsNotGranted.isEmpty();
    }

    private fun permissionsNotGranted(permissionsToRequest: Array<String>): Array<String> {
        return permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }


}
