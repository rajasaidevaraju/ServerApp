package helpers

import android.content.Context
import android.net.Uri

class SharedPreferencesHelper(private val context: Context) {

    private val name="MyPrefs"
    private val uriKeySDCard="selected_folder_uri_sd_card"
    private val uriKeyInternal="selected_folder_uri_internal"
    private val frontEndKey="front_end_url"
    private val backEndKey="back_end_url"
    private val uiServerModeKey="ui_server_mode"
    val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun getURI(uriKey:String):Uri?{
        val selectedFolderUriString = sharedPrefs.getString(uriKey, null)

        if (selectedFolderUriString != null) {
            val selectedFolderUri = Uri.parse(selectedFolderUriString)
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

    fun storeSDCardURI(selectedFolderUri:Uri){
        storeURI(selectedFolderUri,uriKeySDCard)
    }

    fun storeInternalURI(selectedFolderUri:Uri){
        storeURI(selectedFolderUri,uriKeyInternal)
    }

    private fun storeURI(selectedFolderUri:Uri, uriKey:String){

        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        editor.putString(uriKey, selectedFolderUri.toString())
        editor.apply()
    }


    fun getFrontEndUrl(): String? {
        return sharedPrefs.getString(frontEndKey, null)
    }
    fun storeFrontEndUrl(enteredFrontEndUrl:String?){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(frontEndKey, enteredFrontEndUrl)
        editor.apply()
    }
    fun getBackEndUrl(): String? {
        return sharedPrefs.getString(backEndKey, null)
    }
    fun storeBackEndUrl(enteredFrontEndUrl:String?){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(backEndKey, enteredFrontEndUrl)
        editor.apply()
    }

    fun getUIServerMode():Boolean{
        return sharedPrefs.getBoolean(uiServerModeKey, true)
    }

    fun storeUIServerMode(uiServerMode:Boolean){
        val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putBoolean(uiServerModeKey, uiServerMode)
        editor.apply()
    }
}