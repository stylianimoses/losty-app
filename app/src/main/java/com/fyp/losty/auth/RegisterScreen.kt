package com.fyp.losty.auth

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import coil.request.ImageRequest
import com.fyp.losty.R

private val PrimaryBlue = Color(0xFF349BEB)
private val DisabledGray = Color(0xFFDCE9F8)

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var contact by remember { mutableStateOf("") } // mobile or email
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var otpCode by remember { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as? Activity

    val authState by authViewModel.authState.collectAsState()
    val verificationId by authViewModel.verificationId.collectAsState()
    var submitted by remember { mutableStateOf(false) }

    // Image picker launcher
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) profileImageUri = uri
    }

    // Validation
    val isContactEmail = Patterns.EMAIL_ADDRESS.matcher(contact).matches()
    val isContactPhone = contact.isNotBlank() && contact.length >= 10
    val isContactValid = isContactEmail || isContactPhone
    val isPasswordValid = password.length >= 8
    val isFormComplete = isContactValid && fullName.isNotBlank() && username.isNotBlank() && isPasswordValid

    // React to auth state changes: on success navigate to main; on error show toast
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                Log.i("RegisterScreen", "AuthState.Success detected, navigating to main")
                Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                // Navigate to main and clear backstack so user can't go back to auth screens
                navController.navigate("main") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                // Reset auth state in ViewModel to avoid repeated navigation from stale state
                authViewModel.resetState()
                submitted = false
            }
            is AuthState.Error -> {
                val msg = (authState as AuthState.Error).message
                Log.w("RegisterScreen", "AuthState.Error: $msg")
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                submitted = false
            }
            else -> Unit
        }
    }

    // Registration UI
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE6E9EE))
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
                    Text(text = "LOSTY", fontSize = 36.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(18.dp))

                    // Keep a top social button similar to login
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("social_button")
                            .clickable {
                                // Placeholder: show a toast for now
                                Toast.makeText(context, "Social sign-up placeholder", Toast.LENGTH_SHORT).show()
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
                        Text("  OR  ", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Profile image picker aligned to the top of fields
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.BottomEnd) {
                        if (profileImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(profileImageUri).crossfade(true).build(),
                                contentDescription = "Profile image",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Pick image")
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Pick image",
                            modifier = Modifier
                                .size(28.dp)
                                .offset((-6).dp, (-6).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { picker.launch("image/*") }
                                .padding(6.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fields in the exact order from the screenshot
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        placeholder = { Text("Mobile Number or Email") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contact_field"),
                        shape = RoundedCornerShape(8.dp),
                        isError = contact.isNotEmpty() && !isContactValid
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = fullName.isNotBlank() && fullName.length < 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = username.isNotBlank() && username.length < 3
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
                        isError = password.isNotBlank() && !isPasswordValid
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // If the contact is a phone number, either show Send OTP or OTP input/verify depending on verificationId
                    if (isContactPhone) {
                        if (verificationId == null) {
                            // show Send OTP button (changed to directly register without OTP)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Button(
                                    onClick = {
                                        if (activity == null) {
                                            Toast.makeText(context, "Unable to register (no Activity)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Register immediately without OTP: create a synthetic email from phone so
                                            // the existing email/password registration flow in ViewModel can run.
                                            // Note: this is a development shortcut — consider using proper phone
                                            // verification or linking in production.
                                            val syntheticEmail = "${'$'}contact@losty.local"
                                            Log.i("RegisterScreen", "Register (phone) clicked: fullName=${fullName}, phone=${contact}")
                                            submitted = true
                                            authViewModel.verifyOtpAndRegister(
                                                 fullName = fullName,
                                                 email = syntheticEmail,
                                                 phoneNumber = contact,
                                                 password = password,
                                                 otpCode = "",
                                                 profileImageUri = profileImageUri
                                             )
                                        }
                                    },
                                    enabled = contact.isNotBlank() && authState !is AuthState.Loading && !submitted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Sign up")
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it },
                                    placeholder = { Text("Enter OTP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        // Verify OTP and register (ViewModel handles flow)
                                        Log.i("RegisterScreen", "Verify & Register clicked: fullName=${fullName}, phone=${contact}, otp=${otpCode}")
                                        submitted = true
                                        authViewModel.verifyOtpAndRegister(
                                             fullName = fullName,
                                             email = if (isContactEmail) contact else "",
                                             phoneNumber = contact,
                                             password = password,
                                             otpCode = otpCode,
                                             profileImageUri = profileImageUri
                                         )
                                    },
                                    enabled = otpCode.length >= 4 && authState !is AuthState.Loading && !submitted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Verify & Register")
                                }
                            }
                        }
                    } else {
                        // contact is email or empty — show regular Sign up button
                        Button(
                            onClick = {
                                if (!isContactValid) Toast.makeText(context, "Enter a valid contact (phone or email)", Toast.LENGTH_SHORT).show()
                                else if (!isPasswordValid) Toast.makeText(context, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                                else {
                                    Log.i("RegisterScreen", "Sign up (email) clicked: fullName=${fullName}, email=${contact}")
                                    submitted = true
                                    authViewModel.verifyOtpAndRegister(
                                         fullName = fullName,
                                         email = contact,
                                         phoneNumber = "",
                                         password = password,
                                         otpCode = "",
                                         profileImageUri = profileImageUri
                                     )
                                }
                            },
                            enabled = isFormComplete && authState !is AuthState.Loading && !submitted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isFormComplete) PrimaryBlue else DisabledGray)
                        ) {
                            Text(text = "Sign up", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(text = "Have an account? ")
                    Text(text = "Log in", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { navController.navigate("login") })
                }
            }
        }

        if (authState is AuthState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun RegisterScreenPreview() {
    val navController = rememberNavController()
    RegisterScreen(navController = navController)
}
