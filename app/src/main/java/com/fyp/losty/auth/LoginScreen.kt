package com.fyp.losty.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.fyp.losty.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

private val PrimaryBlue = Color(0xFF349BEB) // requested exact blue
private val DisabledGray = Color(0xFFDCE9F8)
private val LoginButtonBlue = Color(0xFF9FC6FF)

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    cm ?: return false
    val nw = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(nw) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var credential by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = (LocalContext.current as? Activity)

    // Configure GoogleSignIn client using the web client id in strings.xml
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(LocalContext.current.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    val googleClient: GoogleSignInClient? = activity?.let { GoogleSignIn.getClient(it, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(Exception::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrEmpty()) {
                    authViewModel.signInWithGoogle(idToken)
                } else {
                    Toast.makeText(context, "Google Sign-In failed (no token)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Google Sign-In error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                navController.navigate("main") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    val isEmail = Patterns.EMAIL_ADDRESS.matcher(credential).matches()
    val isPhone = Patterns.PHONE.matcher(credential).matches() && credential.length >= 10
    val isCredentialValid = isEmail || isPhone
    val isPasswordValid = password.length >= 8
    val isFormComplete = isCredentialValid && isPasswordValid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-screen card with margin around it
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
                // Top area: title + actions
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Title in all caps using app font
                    Text(text = "LOSTY", fontSize = 36.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sign in with Google button (full width blue)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_button")
                            .clickable(enabled = isNetworkAvailable(context) && authState !is AuthState.Loading) {
                                if (!isNetworkAvailable(context)) {
                                    Toast.makeText(context, "No network connection. Please connect to the internet and try again.", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        val intent = googleClient?.signInIntent
                                        if (intent != null) {
                                            launcher.launch(intent)
                                        } else {
                                            Toast.makeText(context, "Google Sign-In is not configured on this device.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (ex: ActivityNotFoundException) {
                                        Toast.makeText(context, "Unable to open Google Sign-In. Please ensure Google Play Services is available.", Toast.LENGTH_LONG).show()
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Google Sign-In failed: ${'$'}{ex.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .background(PrimaryBlue, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.google_icon),
                            contentDescription = "Google",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("  OR  ", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inputs
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        placeholder = { Text("phone number, username, or email") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("credential_field"),
                        shape = RoundedCornerShape(8.dp),
                        isError = credential.isNotEmpty() && !isCredentialValid
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_field"),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility")
                            }
                        },
                        isError = password.isNotEmpty() && !isPasswordValid
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { showResetDialog = true }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!isNetworkAvailable(context)) Toast.makeText(context, "No network connection", Toast.LENGTH_SHORT).show()
                            else authViewModel.loginUser(credential, password)
                        },
                        enabled = isFormComplete && authState !is AuthState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isFormComplete) PrimaryBlue else DisabledGray)
                    ) {
                        Text(text = "Log In", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }

                // Bottom area: sign up link
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(text = "Don't have an account? ")
                    Text(text = "sign up", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { navController.navigate("register") })
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Password") },
                text = {
                    Column {
                        Text("Enter the email associated with your account to receive reset instructions.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = resetEmail, onValueChange = { resetEmail = it }, placeholder = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = { authViewModel.sendPasswordReset(resetEmail); showResetDialog = false }) { Text("Send") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (authState is AuthState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
fun LoginScreenPreview() {
    val navController = rememberNavController()
    LoginScreen(navController = navController)
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, widthDp = 420)
@Composable
fun LoginScreenDarkPreview() {
    val navController = rememberNavController()
    LoginScreen(navController = navController)
}
