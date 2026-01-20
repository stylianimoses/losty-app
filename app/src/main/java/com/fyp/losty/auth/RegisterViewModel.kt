package com.fyp.losty.auth

import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.AuthState
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.UserRepository
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    fun registerUser(
        email: String,
        fullName: String,
        username: String,
        password: String,
        imageUri: Uri?
    ) = viewModelScope.launch {
        _authState.value = AuthState.Loading

        // Basic Validation
        if (email.isBlank() || fullName.isBlank() || username.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Please fill in all fields.")
            return@launch
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return@launch
        }
        if (password.length < 8) {
            _authState.value = AuthState.Error("Password must be at least 8 characters.")
            return@launch
        }

        try {
            // 1. Check if username is already taken BEFORE creating Auth account
            val usernameQuery = firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()
            
            if (!usernameQuery.isEmpty) {
                _authState.value = AuthState.Error("Username is already taken.")
                return@launch
            }

            // 2. Register in Firebase Auth
            val firebaseUser = authRepository.registerUser(email, password)
            
            // 3. Handle Profile Picture (Optional)
            var photoUrl = ""
            if (imageUri != null) {
                try {
                    photoUrl = userRepository.uploadProfileImage(firebaseUser.uid, imageUri)
                } catch (e: Exception) {
                    Log.e("RegisterViewModel", "Image upload failed: ${e.message}")
                }
            }

            // 4. Update Firebase Auth Profile
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .apply {
                    if (photoUrl.isNotEmpty()) {
                        setPhotoUri(Uri.parse(photoUrl))
                    }
                }
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // 5. Create Firestore User Document
            val userMap = hashMapOf(
                "fullName" to fullName,
                "username" to username,
                "email" to email,
                "photoUrl" to photoUrl,
                "trustScore" to 0,
                "itemsReturned" to 0,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(firebaseUser.uid).set(userMap).await()

            // 6. Send Verification Email
            try {
                authRepository.sendEmailVerification()
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Verification email failed: ${e.message}")
            }

            // 7. Success!
            _authState.value = AuthState.Success(firebaseUser)
            
        } catch (e: Exception) {
            val errorMessage = e.message ?: "An unknown error occurred"
            val userFriendlyError = when {
                errorMessage.contains("already in use", ignoreCase = true) -> "This email is already registered."
                errorMessage.contains("network", ignoreCase = true) -> "Network error. Please check your connection."
                else -> errorMessage
            }
            _authState.value = AuthState.Error(userFriendlyError)
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
                        "trustScore" to 0,
                        "itemsReturned" to 0,
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
