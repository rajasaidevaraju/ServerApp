
package com.example.serverapp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import database.AppDatabase
import helpers.SharedPreferencesHelper
import kotlinx.coroutines.launch

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefHandler = SharedPreferencesHelper(application)
    private val database  by lazy { AppDatabase.getDatabase(application) }
    val sdCardUri = MutableLiveData<Uri?>()
    val internalUri = MutableLiveData<Uri?>()
    val rowCount = MutableLiveData<Int>(0)
    val uiServerMode = MutableLiveData<Boolean>(false)
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
    }

    fun saveSDCardUri(uri: Uri) {
        viewModelScope.launch {
            prefHandler.storeSDCardURI(uri)
            sdCardUri.value = uri
        }
    }

    fun saveInternalUri(uri: Uri) {
        viewModelScope.launch {
            prefHandler.storeInternalURI(uri)
            internalUri.value = uri
        }
    }

    fun setUIServerMode(mode:Boolean){
        viewModelScope.launch {
            prefHandler.storeUIServerMode(mode)
            uiServerMode.value=mode
        }
    }
}
