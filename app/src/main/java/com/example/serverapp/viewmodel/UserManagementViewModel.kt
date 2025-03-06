package com.example.serverapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import database.AppDatabase
import database.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import server.service.SessionManager
import server.service.UserService

class UserManagementViewModel(application: Application): AndroidViewModel(application) {

    private val database  by lazy { AppDatabase.getDatabase(application) }
    private val userDao = database.userDao()
    private val sessionManager = SessionManager()
    private val userService by lazy { UserService(database,sessionManager) }

    private val _usernameError = MutableStateFlow("")
    val usernameError: StateFlow<String> = _usernameError

    private val _passwordError = MutableStateFlow("")
    val passwordError: StateFlow<String> = _passwordError

    private val _createResult = MutableStateFlow(false)
    val createResult: StateFlow<Boolean> = _createResult

    private val _passwordUpdateResult = MutableStateFlow(false)
    val passwordUpdateResult: StateFlow<Boolean> = _passwordUpdateResult

    fun initPasswordUpdate() {
        _passwordUpdateResult.value = false
    }

    fun initCreateUser() {
        _createResult.value=false
    }

    fun resetError(){
        _usernameError.value=""
        _passwordError.value=""
    }
    private fun setUsernameError(error: String) {
        _usernameError.value = error
    }

    private fun setPasswordError(error: String) {
        _passwordError.value = error
    }

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


    suspend fun createUser(userName: String, password: String){
        setUsernameError("")
        setPasswordError("")
        val usernameCheck = userService.checkUsername(userName)
        if (!usernameCheck.first) {
            setUsernameError(usernameCheck.second ?: "Invalid username")

        }

        val passwordCheck = userService.checkPassword(password)
        if (!passwordCheck.first) {
            setPasswordError(passwordCheck.second ?: "Invalid password")
        }

        if (!passwordCheck.first||!usernameCheck.first) {
            _createResult.value=false
            return
        }


        withContext(Dispatchers.IO) {
            val salt = BCrypt.gensalt()
            val hashedPassword = BCrypt.hashpw(password, salt)
            val userWithName = userDao.getUserByUsername(userName)
            if (userWithName == null) {
                val user = User(userName = userName, passwordHash = hashedPassword, salt = salt)
                userDao.insertUser(user)
                loadUsers()
                _createResult.value=true
            } else {
                setUsernameError("username already exists")
                _createResult.value=false
            }
        }
    }

    fun changePassword(userId: Long, newPassword: String) {
        setPasswordError("")

        val passwordCheck = userService.checkPassword(newPassword)
        if (!passwordCheck.first) {
            setPasswordError(passwordCheck.second ?: "Invalid password")
            _passwordUpdateResult.value = false
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val salt = BCrypt.gensalt()
                val hashedPassword = BCrypt.hashpw(newPassword, salt)
                userDao.updatePasswordWithSalt(userId, hashedPassword, salt)
                _passwordUpdateResult.value = true
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