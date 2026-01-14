package com.fyp.losty.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.AuthState
import com.fyp.losty.data.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    fun loginUser(credential: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        try {
            val emailToUse = if (!Patterns.EMAIL_ADDRESS.matcher(credential).matches()) {
                val userQuery = firestore.collection("users")
                    .whereEqualTo("username", credential)
                    .get()
                    .await()
                
                if (userQuery.isEmpty) {
                    _authState.value = AuthState.Error("Invalid username or password.")
                    return@launch
                }
                userQuery.documents[0].getString("email") ?: throw Exception("User email not found.")
            } else {
                credential
            }

            authRepository.loginUser(emailToUse, password)
            val user = authRepository.getCurrentUser()
            if (user != null && !user.isEmailVerified) {
                _authState.value = AuthState.Error("Please verify your email before logging in.")
                return@launch
            }
            _authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "An unknown error occurred")
        }
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        try {
            authRepository.signInWithGoogle(idToken)
            val user = authRepository.getCurrentUser()
            
            if (user != null) {
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                if (!userDoc.exists()) {
                    val userMap = mapOf(
                        "fullName" to (user.displayName ?: ""),
                        "username" to (user.displayName?.filter { !it.isWhitespace() }?.lowercase() ?: "user_${user.uid.take(5)}"),
                        "email" to (user.email ?: ""),
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "createdAt" to System.currentTimeMillis()
                    )
                    firestore.collection("users").document(user.uid).set(userMap).await()
                }
            }
            _authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
        }
    }
}