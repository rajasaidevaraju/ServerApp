package com.example.serverapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import database.AppDatabase
import database.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt

class UserManagementViewModel(application: Application): AndroidViewModel(application) {

    private val database  by lazy { AppDatabase.getDatabase(application) }
    private val userDao = database.userDao()
    val users = mutableStateListOf<User>()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            users.clear()
            users.addAll(userDao.getAllUsers())
        }
    }

    suspend fun createUser(userName: String, password: String) {
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = User(userName = userName, passwordHash = hashedPassword)
        userDao.insertUser(user)
        loadUsers()
    }

    suspend fun changePassword(userId: Long, newPassword: String) {
        val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userDao.updatePassword(userId, hashedPassword)
        loadUsers()
    }

    suspend fun deleteUser(userId: Long) {
        withContext(Dispatchers.IO) {
            val user = userDao.getUserById(userId) ?: return@withContext
            userDao.deleteUser(user.id)
            loadUsers()
        }
    }



    suspend fun disableUser(userId: Long) {
        val user = userDao.getUserById(userId) ?: return
        if(!user.disabled){
            userDao.setDisabled(userId, true)
            loadUsers()
        }
    }

    suspend fun enableUser(userId: Long) {
        val user = userDao.getUserById(userId) ?: return
        if(user.disabled){
            userDao.setDisabled(userId, false)
            loadUsers()
        }

    }

}