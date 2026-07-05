package com.example.serverapp.ui.usermanagement


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.serverapp.ui.CommonButton
import com.example.serverapp.ui.StatusPill
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Manage Users",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                val accountCount = viewModel.users.size
                Text(
                    text = if (accountCount == 1) "1 account" else "$accountCount accounts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            CommonButton("Create User", { showCreateUserForm = true })
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Create User",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Add an account that can sign in to the server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("Username") },
                    singleLine = true,
                    isError = usernameError.isNotEmpty(),
                    supportingText = if (usernameError.isNotEmpty()) {
                        { Text(usernameError) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordError.isNotEmpty(),
                    supportingText = if (passwordError.isNotEmpty()) {
                        { Text(passwordError) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = {
                        viewModel.resetError()
                        onDismissRequest()
                    }) { Text("Cancel") }
                    Button(
                        onClick = { coroutineScope.launch { viewModel.createUser(newUsername, newPassword) } },
                        shape = RoundedCornerShape(12.dp),
                        enabled = newUsername.isNotBlank() && newPassword.isNotBlank()
                    ) { Text("Create") }
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
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.userName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = user.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    active = !user.disabled,
                    activeText = "Active",
                    inactiveText = "Disabled"
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                val toggleUserStatus = fun(){
                    if(user.disabled){
                        coroutineScope.launch { viewModel.enableUser(user.id)}
                    }else{
                        coroutineScope.launch { viewModel.disableUser(user.id)}
                    }
                }

                val userStatusText=if (user.disabled) "Enable" else "Disable"

                CommonButton(userStatusText,toggleUserStatus)
                CommonButton("Change Password",{ showPasswordChangeDialog = true })
                Button(
                    onClick = { showDeleteAccountDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("Delete") }
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

