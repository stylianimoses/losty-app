package com.fyp.losty.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.ui.components.BackToHomeButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun ProfileScreen(
    navController: NavController,
    appNavController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val userProfile by appViewModel.userProfile.collectAsState()
    var isEditing by remember { mutableStateOf(false) }
    var editingName by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Load the user profile as soon as the screen is displayed
    LaunchedEffect(Unit) {
        appViewModel.loadUserProfile()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            selectedImageUri = uri
            // Upload and persist the image
            uri?.let {
                val storageRef = Firebase.storage.reference.child("profile_pictures/${Firebase.auth.currentUser?.uid}.jpg")
                storageRef.putFile(it)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUri: Uri ->
                            // Save the download URL to Firestore under the user's profile
                            val userRef = Firebase.firestore.collection("users").document(Firebase.auth.currentUser?.uid!!)
                            userRef.update("profilePicture", downloadUri.toString())
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e: Exception ->
                                    Toast.makeText(context, "Failed to update profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        Toast.makeText(context, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Profile", color = Color.Black) },
                navigationIcon = { BackToHomeButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            AsyncImage(
                model = selectedImageUri ?: userProfile.photoUrl.ifEmpty { "https://i.imgur.com/8A2nO7N.png" },
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        singleLine = true,
                        modifier = Modifier.widthIn(min = 120.dp, max = 260.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        val nameToSave = editingName.trim()
                        if (nameToSave.isNotEmpty()) {
                            appViewModel.updateDisplayName(nameToSave)
                        }
                        isEditing = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save name")
                    }
                } else {
                    Text(text = userProfile.displayName.ifBlank { "User" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { editingName = userProfile.displayName; isEditing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit name")
                    }
                }
            }

            // Basic Information
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = userProfile.email, fontSize = 14.sp, color = Color.Gray)

            // Actions
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    // Change Password flow
                    val user = Firebase.auth.currentUser
                    user?.let {
                        val email = it.email
                        Firebase.auth.sendPasswordResetEmail(email!!)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Failed to send password reset email: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0), contentColor = Color.Black)
            ) { Text("Change Password") }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    // Navigate to login and clear back stack safely (placeholder for actual sign-out)
                    appNavController.navigate("login") {
                        popUpTo(appNavController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) { Text("Log Out") }
        }
    }
}