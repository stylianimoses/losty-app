package com.fyp.losty.auth

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.AuthState
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.UserRepository // Added UserRepository import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository() // Added UserRepository instance
    val authState = MutableStateFlow<AuthState>(AuthState.Idle)

    fun registerUser(
        email: String,
        fullName: String,
        username: String,
        password: String,
        imageUri: Uri?
    ) = viewModelScope.launch {
        authState.value = AuthState.Loading

        if (imageUri == null) {
            authState.value = AuthState.Error("Please select a profile image.")
            return@launch
        }
        if (email.isBlank() || fullName.isBlank() || username.isBlank() || password.isBlank()) {
            authState.value = AuthState.Error("Please fill in all fields.")
            return@launch
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            authState.value = AuthState.Error("Please enter a valid email address.")
            return@launch
        }
        if (password.length < 8) {
            authState.value = AuthState.Error("Password must be at least 8 characters.")
            return@launch
        }

        try {
            val firebaseUser = authRepository.registerUser(email, password)
            authRepository.sendEmailVerification()
            val photoUrl = userRepository.uploadProfileImage(firebaseUser.uid, imageUri) // Using userRepository
            userRepository.updateUserProfile(username, photoUrl) // Using userRepository

            val userMap = mapOf(
                "fullName" to fullName,
                "username" to username,
                "email" to email,
                "photoUrl" to photoUrl,
                "createdAt" to System.currentTimeMillis()
            )
            userRepository.createUserDocument(firebaseUser.uid, userMap) // Using userRepository
            authState.value = AuthState.Success(firebaseUser)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "An unknown error occurred"
            authState.value = if (errorMessage.contains("already in use", ignoreCase = true)) {
                AuthState.Error("This email is already registered. Please try to sign in.")
            } else {
                AuthState.Error(errorMessage)
            }
        }
    }

    fun loginUser(email: String, password: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                authState.value = AuthState.Error("Please enter a valid email address.")
                return@launch
            }
            authRepository.loginUser(email, password)
            val user = authRepository.getCurrentUser()
            if (user != null && !user.isEmailVerified) {
                authState.value = AuthState.Error("Please verify your email before logging in.")
                return@launch
            }
            authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "An unknown error occurred")
        }
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            authRepository.signInWithGoogle(idToken)
            val user = authRepository.getCurrentUser()
            authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
        }
    }
}