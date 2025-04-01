package com.example.serverapp.ui.usermanagement


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.serverapp.ui.CommonButton
import com.example.serverapp.ui.StyledError
import kotlinx.coroutines.launch
import com.example.serverapp.viewmodel.UserManagementViewModel
import database.entity.User
import kotlinx.coroutines.CoroutineScope

@Composable
fun UserManagementScreen(viewModel: UserManagementViewModel) {

    val coroutineScope = rememberCoroutineScope()
    var showCreateUserForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.padding(top = 50.dp),){
            Text("Manage Users", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            val buttonText=if (showCreateUserForm) "Hide Create User" else "Create New User"
            CommonButton(buttonText,{ showCreateUserForm = !showCreateUserForm })
        }

        if(showCreateUserForm){
            CreateUserDialog(coroutineScope,viewModel, onDismissRequest = {->showCreateUserForm=false})
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(viewModel.users) { user -> UserItem(user = user, viewModel) }
        }
    }
}


@Composable
fun CreateUserDialog(coroutineScope: CoroutineScope, viewModel: UserManagementViewModel,onDismissRequest: () -> Unit){
    // Create User Section
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val usernameError by viewModel.usernameError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()
    val createResult by viewModel.createResult.collectAsState()

    LaunchedEffect(createResult) {
        if (createResult) {
            onDismissRequest()
            viewModel.resetError()
            viewModel.initCreateUser()
        }
    }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = 10.dp, bottom = 10.dp),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Create User", style = MaterialTheme.typography.headlineSmall)

                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if(usernameError.isNotEmpty()){
                        StyledError(usernameError)
                    }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if(passwordError.isNotEmpty()){
                        StyledError(passwordError)
                    }

                }
                Row( modifier=Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton( modifier = Modifier.padding(end = 10.dp), onClick = {
                        viewModel.resetError()
                        onDismissRequest()
                    }) { Text("Cancel") }
                    CommonButton("Create",{ coroutineScope.launch { viewModel.createUser(newUsername, newPassword) } })
                }
            }
        }
    }

}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserItem(
    user: User,
    viewModel: UserManagementViewModel
) {
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
        ) {
            Column {
                Text("Username: ${user.userName}", style = MaterialTheme.typography.bodyLarge)
                Text("Disabled: ${user.disabled}", style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                val toggleUserStatus = fun(){
                    if(user.disabled){
                        coroutineScope.launch { viewModel.enableUser(user.id)}
                    }else{
                        coroutineScope.launch { viewModel.disableUser(user.id)}
                    }
                }

                val userStatusText=if (user.disabled) "Enable Account" else "Disable Account"

                CommonButton(userStatusText,toggleUserStatus)
                CommonButton("Delete Account",{ showDeleteAccountDialog=true })
                CommonButton("Change Password",{ showPasswordChangeDialog = true })
            }
        }
    }

    if (showPasswordChangeDialog) {
        EditPasswordDialog(user,viewModel,onDismissRequest = {->showPasswordChangeDialog=false})
    }
    if (showDeleteAccountDialog) {
        DeleteAccountDialog(user,viewModel,onDismissRequest = {->showDeleteAccountDialog=false})
    }
}

@Composable
fun EditPasswordDialog(user: User, viewModel: UserManagementViewModel, onDismissRequest: () -> Unit) {
    var passwordInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val passwordError by viewModel.passwordError.collectAsState()
    val passwordUpdateResult by viewModel.passwordUpdateResult.collectAsState()

    LaunchedEffect(passwordUpdateResult) {
        if (passwordUpdateResult) {
            onDismissRequest()
            viewModel.resetError()
            viewModel.initPasswordUpdate()
        }
    }

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text("Change Password for ${user.userName}") },
        text = {
            Column {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (passwordError.isNotEmpty()) {
                    StyledError(passwordError)
                }
            }
        },
        confirmButton = {
            CommonButton("Update", {
                    if (passwordInput.isNotBlank()) {
                        coroutineScope.launch { viewModel.changePassword(user.id, passwordInput) }
                    }
            })
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun DeleteAccountDialog(user:User, viewModel: UserManagementViewModel, onDismissRequest: () -> Unit){

    val coroutineScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text("Delete the Account of ${user.userName}?") },
        confirmButton = {
            CommonButton("Delete",
                {
                    coroutineScope.launch { viewModel.deleteUser(user.id) }
                    onDismissRequest()
                })
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest() }) {
                Text("Cancel")
            }
        }
    )

}

