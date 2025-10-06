package helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.core.content.edit
import java.io.File

class SharedPreferencesHelper(private val context: Context) {

    private val name="MyPrefs"
    private val uriKeySDCard="selected_folder_uri_sd_card"
    private val uriKeyInternal="selected_folder_uri_internal"
    private val frontEndKey="front_end_url"
    private val backEndKey="back_end_url"
    private val uiServerModeKey="ui_server_mode"
    private val fileHandlerHelper: FileHandlerHelper = FileHandlerHelper(context)
    val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun getURI(uriKey:String):Uri?{
        val selectedFolderUriString = sharedPrefs.getString(uriKey, null)

        if (selectedFolderUriString != null) {
            val selectedFolderUri = selectedFolderUriString.toUri()
            return selectedFolderUri
        } else {
            return null
        }

    }

    fun getSDCardURI():Uri?{
        return getURI(uriKeySDCard)
    }

    fun getInternalURI():Uri?{
        return getURI(uriKeyInternal)
    }

    fun setupFolders(){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val internalFolder = File(Environment.getExternalStorageDirectory(), ".int")
        if (!internalFolder.exists()) {
            internalFolder.mkdir()
        }
        val internalUri = Uri.fromFile(internalFolder)
        var sdUri:Uri?=null
        val sdCardRoot = fileHandlerHelper.getExternalSdCardPath()
        if(sdCardRoot!=null){
            val sdFolder = File(sdCardRoot, ".and")
            if (!sdFolder.exists()) {
                sdFolder.mkdir()
            }
            sdUri = Uri.fromFile(sdFolder)
        }
        sharedPrefs.edit {
            putString(uriKeyInternal, internalUri.toString())
            if(sdUri!=null){
                putString(uriKeySDCard, sdUri.toString())
            }
        }
    }


    fun getFrontEndUrl(): String? {
        return sharedPrefs.getString(frontEndKey, null)
    }
    fun storeFrontEndUrl(enteredFrontEndUrl:String?){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        sharedPrefs.edit {
            putString(frontEndKey, enteredFrontEndUrl)
        }
    }
    fun getBackEndUrl(): String? {
        return sharedPrefs.getString(backEndKey, null)
    }
    fun storeBackEndUrl(enteredFrontEndUrl:String?){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        sharedPrefs.edit {
            putString(backEndKey, enteredFrontEndUrl)
        }
    }

    fun getUIServerMode():Boolean{
        return sharedPrefs.getBoolean(uiServerModeKey, true)
    }

    fun storeUIServerMode(uiServerMode:Boolean){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        sharedPrefs.edit {
            putBoolean(uiServerModeKey, uiServerMode)
        }
    }
}