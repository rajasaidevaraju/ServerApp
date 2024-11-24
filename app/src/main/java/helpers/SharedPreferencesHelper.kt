package helpers

import android.content.Context
import android.net.Uri

class SharedPreferencesHelper(private val context: Context) {

    private val name="MyPrefs"
    private val uriKey="selected_folder_uri"
    private val frontEndKey="front_end_url"
    private val backEndKey="back_end_url"
    private val uiServerModeKey="ui_server_mode"
    private val PREFS_KEY_SERVER_STATE = "SERVER_STATE_KEY" // Key to identify server state in SharedPreferences
    val sharedPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getURI():Uri?{
        val selectedFolderUriString = sharedPrefs.getString(uriKey, null)

        if (selectedFolderUriString != null) {
            val selectedFolderUri = Uri.parse(selectedFolderUriString)
            return selectedFolderUri
        } else {
            return null
        }

    }

    fun storeURI(selectedFolderUri:Uri){

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