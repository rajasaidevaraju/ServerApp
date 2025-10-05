
package com.example.serverapp.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import database.AppDatabase
import helpers.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefHandler = SharedPreferencesHelper(application)
    private val database  by lazy { AppDatabase.getDatabase(application) }
    val sdCardUri = MutableLiveData<Uri?>()
    val internalUri = MutableLiveData<Uri?>()
    val rowCount = MutableLiveData<Int>(0)
    val uiServerMode = MutableLiveData<Boolean>(false)
    val frontEndUrl=MutableLiveData<String?>()
    val backEndUrl=MutableLiveData<String?>()
    val isServerRunning= MutableLiveData<Boolean>(false)
    private val _hasPermission = MutableLiveData<Boolean>(Environment.isExternalStorageManager())
    val hasPermission: LiveData<Boolean> get() = _hasPermission
    val databaseActionStatus = MutableLiveData<String>("")

    init {
        loadPrefs()
        viewModelScope.launch {
            database.fileDao().getTotalFileCountLive()
                .asFlow()
                .collect { count ->
                    rowCount.value = count
                }
        }
    }

    private fun loadPrefs() {
        sdCardUri.value = prefHandler.getSDCardURI()
        internalUri.value = prefHandler.getInternalURI()
        uiServerMode.value=prefHandler.getUIServerMode()
        frontEndUrl.value=prefHandler.getFrontEndUrl()
    }

    fun saveSDCardUri(uri: Uri) {
        prefHandler.storeSDCardURI(uri)
        sdCardUri.value = uri
    }

    fun saveInternalUri(uri: Uri) {

        prefHandler.storeInternalURI(uri)
        internalUri.value = uri

    }

    fun setUIServerMode(mode:Boolean){

        prefHandler.storeUIServerMode(mode)
        uiServerMode.value=mode

    }

    fun setFrontEndUrl(url:String){

        prefHandler.storeFrontEndUrl(url)
        frontEndUrl.value=url
    }

    fun setBackEndUrl(url:String){

        prefHandler.storeBackEndUrl(url)
        backEndUrl.value=url

    }

    fun updateBackEndUrl(){
        backEndUrl.value= prefHandler.getBackEndUrl()
    }

    fun updateServerRunning(){
        isServerRunning.value=prefHandler.getBackEndUrl()!=null
    }

    fun checkPermission() {
        _hasPermission.value = Environment.isExternalStorageManager()
    }

    fun exportDatabase(context: Context, fileName: String) = viewModelScope.launch(Dispatchers.IO) {
        val databasePath = context.getDatabasePath("app_database").absolutePath
        val databaseFile = File(databasePath)

        if (!databaseFile.exists()) {
            databaseActionStatus.postValue("Error: Database file not found.")
            return@launch
        }

        val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        exportDir.mkdirs()
        val exportFile = File(exportDir, fileName)

        try {
            FileInputStream(databaseFile).use { input ->
                FileOutputStream(exportFile).use { output ->
                    input.copyTo(output)
                }
            }
            databaseActionStatus.postValue("Export successfully to Downloads/$fileName")
        } catch (e: Exception) {
            databaseActionStatus.postValue("Export failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun importDatabase(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val databasePath = getApplication<Application>().getDatabasePath("app_database").absolutePath
        val databaseFile = File(databasePath)
        val context = getApplication<Application>().applicationContext

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(databaseFile).use { output ->
                    input.copyTo(output)
                }
            }
            AppDatabase.resetInstance()

            databaseActionStatus.postValue("Import successful! App restart recommended.")
        } catch (e: Exception) {
            databaseActionStatus.postValue("Import failed: ${e.message}")
            e.printStackTrace()
        }
    }

}
