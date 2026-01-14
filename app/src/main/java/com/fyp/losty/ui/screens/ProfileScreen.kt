package com.fyp.losty.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.fyp.losty.AppViewModel
import com.fyp.losty.R
import com.fyp.losty.ui.components.BackButton
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val userProfile by appViewModel.userProfile.collectAsState()
    val isSignedOut by appViewModel.isSignedOut.collectAsState() // Observe sign-out state
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        appViewModel.loadUserProfile()
    }
    
    // FIX: State-driven navigation using LaunchedEffect
    LaunchedEffect(isSignedOut) {
        if (isSignedOut) {
            navController.navigate("auth_graph") { // Navigate to the auth_graph
                popUpTo("main_graph") { inclusive = true } // Clear main graph
                launchSingleTop = true
            }
        }
    }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                actions = {
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = Color(0xFFE91E63))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Profile Picture
            val painter = if (userProfile.photoUrl.isNotEmpty()) {
                rememberAsyncImagePainter(userProfile.photoUrl)
            } else {
                painterResource(id = R.drawable.outline_account_circle_24)
            }
            Image(
                painter = painter,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Display Name & Edit Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = userProfile.displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { newName = userProfile.displayName; showEditNameDialog = true }) {
                    Icon(Icons.Default.Create, contentDescription = "Edit Name", tint = Color(0xFFE91E63))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Email
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = userProfile.email,
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }

            // Spacer to push content up
            Spacer(modifier = Modifier.weight(1f))

            // Sign Out Button (alternative position)
            // Button(onClick = { showSignOutDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) {
            //     Text("Sign Out")
            // }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.signOut() // Use the ViewModel's state-updating sign-out function
                        showSignOutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) { Text("Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New display name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.updateDisplayName(newName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") } }
        )
    }
}
