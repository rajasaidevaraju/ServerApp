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
            withContext(Dispatchers.IO){
                users.addAll(userDao.getAllUsers())
            }
        }
    }

    fun createUser(userName: String, password: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val salt=BCrypt.gensalt()
                val hashedPassword = BCrypt.hashpw(password, salt)
                val user = User(userName = userName, passwordHash = hashedPassword, salt = salt)
                userDao.insertUser(user)
                loadUsers()
            }
        }
    }

    fun changePassword(userId: Long, newPassword: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val salt=BCrypt.gensalt()
                val hashedPassword = BCrypt.hashpw(newPassword,salt)
                userDao.updatePasswordWithSalt(userId, hashedPassword,salt)
                loadUsers()
            }
        }

    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val user = userDao.getUserById(userId) ?: return@withContext
                userDao.deleteUser(user.id)
                loadUsers()
            }
        }


    }

    fun disableUser(userId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val user = userDao.getUserById(userId) ?: return@withContext
                if(!user.disabled){
                    userDao.setDisabled(userId, true)
                    loadUsers()
                }
            }
        }

    }

    fun enableUser(userId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val user = userDao.getUserById(userId) ?: return@withContext
                if(user.disabled){
                    userDao.setDisabled(userId, false)
                    loadUsers()
                }
            }
        }


    }

}