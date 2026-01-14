package com.fyp.losty.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.fyp.losty.AuthState
import com.fyp.losty.R
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private val PrimaryBlue = Color(0xFF349BEB)
private val DisabledGray = Color(0xFFDCE9F8)

@Composable
fun RegisterScreen(navController: NavController, viewModel: RegisterViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val credentialManager = remember { CredentialManager.create(context) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isPasswordValid = password.length >= 8
    val isFormComplete = isEmailValid && fullName.isNotBlank() && username.isNotBlank() && isPasswordValid

    LaunchedEffect(key1 = authState) {
        when (authState) {
            is AuthState.Success -> {
                Log.i("RegisterScreen", "AuthState.Success detected, navigating to main")
                Toast.makeText(context, "Registration successful! Please check your email for verification.", Toast.LENGTH_LONG).show()
                navController.navigate("main_graph") {
                    popUpTo("auth_graph") { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthState.Error -> {
                val msg = (authState as AuthState.Error).message
                Log.w("RegisterScreen", "AuthState.Error: $msg")
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "LOSTY", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("social_button")
                            .clickable(enabled = authState !is AuthState.Loading) {
                                coroutineScope.launch {
                                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                    val isConnected = cm.activeNetwork?.let {
                                        cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    } ?: false

                                    if (!isConnected) {
                                        Toast.makeText(context, "No internet connection.", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    try {
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(context.getString(R.string.default_web_client_id))
                                            .setAutoSelectEnabled(true)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential

                                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            try {
                                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                                viewModel.signInWithGoogle(googleIdToken.idToken)
                                            } catch (e: GoogleIdTokenParsingException) {
                                                Toast.makeText(context, "Parsing error: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Unexpected credential type.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: GetCredentialException) {
                                        Log.e("RegisterScreen", "Sign In failed: ${e.message}")
                                        if (!e.message.toString().contains("User cancelled")) {
                                            Toast.makeText(context, "Sign In Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .background(PrimaryBlue, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.google_icon),
                            contentDescription = "Social",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Sign up with Google", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("  OR  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .testTag("profile_image_picker"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Add profile picture",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("contact_field"),
                        shape = RoundedCornerShape(8.dp),
                        isError = email.isNotEmpty() && !isEmailValid,
                        supportingText = { 
                            if (email.isNotEmpty() && !isEmailValid) {
                                Text("A verification link will be sent to this email.")
                            } else {
                                Text(" ")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = fullName.isNotBlank() && fullName.length < 2,
                        supportingText = { 
                            if (fullName.isNotBlank() && fullName.length < 2) {
                                Text("Full name must be at least 2 characters.")
                            } else {
                                Text(" ")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = username.isNotBlank() && username.length < 3,
                        supportingText = { 
                            if (username.isNotBlank() && username.length < 3) {
                                Text("Username must be at least 3 characters.")
                            } else {
                                Text(" ")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                Icon(imageVector = icon, contentDescription = "Toggle password visibility")
                            }
                        },
                        isError = password.isNotBlank() && !isPasswordValid,
                        supportingText = { 
                            if (password.isNotEmpty() && !isPasswordValid) {
                                Text("Password must be at least 8 characters")
                            } else {
                                Text(" ")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.registerUser(
                                email = email,
                                fullName = fullName,
                                username = username,
                                password = password,
                                imageUri = selectedImageUri
                            )
                        },
                        enabled = isFormComplete && authState !is AuthState.Loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isFormComplete) PrimaryBlue else DisabledGray)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(text = "Sign Up", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Have an account? ", color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { navController.navigate("login") }) {
                        Text("Log In")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun RegisterScreenPreview() {
    val navController = rememberNavController()
    RegisterScreen(navController = navController)
}
