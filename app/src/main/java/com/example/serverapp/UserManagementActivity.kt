package com.example.serverapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.serverapp.ui.theme.ServerAppTheme
import com.example.serverapp.ui.usermanagement.UserManagementScreen
import com.example.serverapp.viewmodel.UserManagementViewModel
import database.AppDatabase


class UserManagementActivity : ComponentActivity() {

    private val userManagementViewModel: UserManagementViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ServerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UserManagementScreen(userManagementViewModel)
                }
            }
        }
    }
}