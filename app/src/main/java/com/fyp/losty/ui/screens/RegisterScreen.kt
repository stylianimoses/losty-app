package com.fyp.losty.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun RegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isPhoneValid = Patterns.PHONE.matcher(phoneNumber).matches()
    val isPasswordLongEnough = password.length >= 8
    val passwordsMatch = password == confirmPassword

    // Firebase instances (reuse inside onClick)
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = email.isNotEmpty() && !isEmailValid,
            supportingText = {
                if (email.isNotEmpty() && !isEmailValid) {
                    Text("Please enter a valid email address")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phoneNumber.isNotEmpty() && !isPhoneValid,
            supportingText = {
                if (phoneNumber.isNotEmpty() && !isPhoneValid) {
                    Text("Please enter a valid phone number")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                IconButton(onClick = {passwordVisible = !passwordVisible}){
                    Icon(imageVector  = image, "toggle password visibility")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isError = password.isNotEmpty() && !isPasswordLongEnough,
            supportingText = {
                if (password.isNotEmpty() && !isPasswordLongEnough) {
                    Text("Password must be at least 8 characters long")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (confirmPasswordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                IconButton(onClick = {confirmPasswordVisible = !confirmPasswordVisible}){
                    Icon(imageVector  = image, "toggle password visibility")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
            supportingText = {
                if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    Text("Passwords do not match")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val emailInput = email.trim()
                val phoneInput = phoneNumber.trim()
                val passwordInput = password.trim()
                val confirmPasswordInput = confirmPassword.trim()

                // Basic validation
                if (emailInput.isEmpty() || phoneInput.isEmpty() || passwordInput.isEmpty() || confirmPasswordInput.isEmpty()) {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
                    Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!Patterns.PHONE.matcher(phoneInput).matches()) {
                    Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (passwordInput.length < 8) {
                    Toast.makeText(context, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (passwordInput != confirmPasswordInput) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                auth.createUserWithEmailAndPassword(emailInput, passwordInput)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid
                            if (userId == null) {
                                Toast.makeText(context, "Registration failed: Missing user ID", Toast.LENGTH_SHORT).show()
                                return@addOnCompleteListener
                            }
                            val user = hashMapOf(
                                "username" to phoneInput,
                                "email" to emailInput,
                                "createdAt" to System.currentTimeMillis()
                            )
                            firestore.collection("users").document(userId).set(user)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    navController.navigate("login")
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            val msg = task.exception?.message ?: "Registration failed"
                            // Friendly handling when email already exists
                            if (msg.contains("already in use", ignoreCase = true)) {
                                Toast.makeText(context, "Email already registered. Try signing in or reset your password.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Registration failed: $msg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
    }
}
