@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNREACHABLE_CODE")

package com.fyp.losty.auth

import android.app.Activity
import android.net.Uri
import androidx.core.net.toUri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.BuildConfig
import com.fyp.losty.utils.FCMTokenManager
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import android.os.Build
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

// Defines the possible states of our UI
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState() // Represents successful authentication
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val realtime = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    init {
        if (BuildConfig.DEBUG) {
            try {
                // NOTE: Emulator wiring disabled per request. Original logic kept commented
                // below for easy re-enabling during local development.

                // Choose host depending on runtime environment: emulator vs physical device.
                // - Android emulator: use 10.0.2.2 to reach host localhost
                // - Physical device with adb reverse: use 127.0.0.1 (adb reverse forwards device localhost to host)
                // val emulatorHost = if (Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK built for x86")) {
                //     "10.0.2.2"
                // } else {
                //     // Physical device: instruct developer to run `adb reverse` for each emulator port
                //     "127.0.0.1"
                // }

                // auth.useEmulator(emulatorHost, 9099)
                // FirebaseDatabase.getInstance().useEmulator(emulatorHost, 9000)
                // FirebaseFirestore.getInstance().useEmulator(emulatorHost, 8080)
                // Note: FirebaseStorage.useEmulator may require firebase-storage-ktx; skip if not available
                // storage.useEmulator(emulatorHost, 9199)
                // println("AuthViewModel: Configured Firebase to use local emulator suite on ${'$'}{emulatorHost}")
            } catch (e: Exception) {
                println("AuthViewModel: Failed to configure emulator endpoints: ${'$'}{e.message}")
            }
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow() // The UI will observe this

    // For phone verification flow
    private val _verificationId = MutableStateFlow<String?>(null)
    val verificationId = _verificationId.asStateFlow()

    private val _resendToken = MutableStateFlow<PhoneAuthProvider.ForceResendingToken?>(null)
    val resendToken = _resendToken.asStateFlow()

    // Shared exception handler for background tasks (non-critical)
    private val backgroundHandler = CoroutineExceptionHandler { _, throwable ->
        // Log but do not surface to UI
        println("Background task failed: ${'$'}{throwable.message}")
    }

    fun sendPhoneVerification(activity: Activity, phoneNumber: String) {
        _authState.value = AuthState.Loading
        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Auto-retrieval or instant verification
                        _verificationId.value = null
                        _authState.value = AuthState.Success
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        _authState.value = AuthState.Error(e.message ?: "Verification failed")
                    }

                    override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                        _verificationId.value = verificationId
                        _resendToken.value = token
                        _authState.value = AuthState.Idle
                    }
                })
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Failed to send verification code")
        }
    }

    fun verifyOtpAndRegister(
        fullName: String,
        email: String,
        phoneNumber: String,
        password: String,
        otpCode: String,
        profileImageUri: Uri?
    ) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Create user with email/password first
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("User creation failed")

                // Link phone credential if verification available
                val vid = _verificationId.value
                if (!vid.isNullOrEmpty()) {
                    val credential = PhoneAuthProvider.getCredential(vid, otpCode)
                    try {
                        firebaseUser.linkWithCredential(credential).await()
                    } catch (linkEx: Exception) {
                        // If linking fails, log and continue (we still store phone number in db)
                        println("Warning: failed to link phone credential: ${'$'}{linkEx.message}")
                    }
                }

                // Update display name
                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(fullName).build()
                firebaseUser.updateProfile(profileUpdates).await()

                // IMPORTANT: Write user record first WITHOUT photoUrl and return success quickly
                val userMap = mapOf(
                    "uid" to firebaseUser.uid,
                    "fullName" to fullName,
                    "email" to email,
                    "phoneNumber" to phoneNumber,
                    "photoUrl" to "",
                    "createdAt" to System.currentTimeMillis()
                )

                // Write minimal user record immediately
                realtime.child("Users").child(firebaseUser.uid).setValue(userMap).await()

                // Mirror the minimal record to Firestore in background
                viewModelScope.launch(Dispatchers.IO + backgroundHandler) {
                    try {
                        firestore.collection("users").document(firebaseUser.uid).set(userMap).await()
                    } catch (e: Exception) {
                        println("Warning: failed to write user to Firestore: ${'$'}{e.message}")
                    }
                }

                // Set success so UI is responsive
                _authState.value = AuthState.Success

                // Now run non-critical tasks in background: upload profile image and update photoUrl, save FCM token
                viewModelScope.launch(Dispatchers.IO + backgroundHandler) {
                    try {
                        var photoUrl: String? = null
                        if (profileImageUri != null) {
                            val profileRef = storage.child("profile_pictures/${'$'}{firebaseUser.uid}.jpg")
                            profileRef.putFile(profileImageUri).await()
                            photoUrl = profileRef.downloadUrl.await().toString()

                            // Update auth profile photo (best effort)
                            try {
                                val photoUpdates = UserProfileChangeRequest.Builder().setPhotoUri(photoUrl.toUri()).build()
                                firebaseUser.updateProfile(photoUpdates).await()
                            } catch (e: Exception) {
                                println("Warning: failed to update profile photo on auth: ${'$'}{e.message}")
                            }

                            // Update Realtime DB photoUrl
                            try {
                                realtime.child("Users").child(firebaseUser.uid).child("photoUrl").setValue(photoUrl).await()
                            } catch (e: Exception) {
                                println("Warning: failed to update photoUrl in Realtime DB: ${'$'}{e.message}")
                            }

                            // Update Firestore photoUrl if available
                            try {
                                firestore.collection("users").document(firebaseUser.uid).update(mapOf("photoUrl" to (photoUrl ?: ""))).await()
                            } catch (e: Exception) {
                                println("Warning: failed to update photoUrl in Firestore: ${'$'}{e.message}")
                            }
                        }

                        // Save FCM token
                        try { FCMTokenManager.saveUserFCMToken(firebaseUser.uid) } catch (e: Exception) {
                            println("Warning: Failed to save FCM token: ${'$'}{e.message}")
                        }

                    } catch (bgEx: Exception) {
                        println("Background registration tasks failed: ${'$'}{bgEx.message}")
                    }
                }

            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun loginUser(credential: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val isEmail = Patterns.EMAIL_ADDRESS.matcher(credential).matches()
                val emailToLogin = if (isEmail) {
                    credential
                } else {
                    // lookup email by phone in Realtime Database
                    val snapshot = realtime.child("Users")
                        .orderByChild("phoneNumber")
                        .equalTo(credential)
                        .limitToFirst(1)
                        .get().await()

                    if (!snapshot.exists()) throw Exception("No account found with this phone number.")
                    val child = snapshot.children.first()
                    child.child("email").getValue(String::class.java) ?: throw Exception("Could not retrieve email for this phone number.")
                }

                // Attempt sign-in with a simple retry for transient network errors
                var attempts = 0
                var lastException: Exception? = null
                while (attempts < 3) {
                    try {
                        val authResult = auth.signInWithEmailAndPassword(emailToLogin, password).await()
                        val firebaseUser = authResult.user

                        // Set success immediately so UI won't wait for token saving
                        _authState.value = AuthState.Success

                        // Save FCM token in background
                        if (firebaseUser != null) {
                            viewModelScope.launch(Dispatchers.IO + backgroundHandler) {
                                try { FCMTokenManager.saveUserFCMToken(firebaseUser.uid) } catch (e: Exception) {
                                    println("Warning: Failed to save FCM token: ${'$'}{e.message}")
                                }
                            }
                        }

                        return@launch // success, exit
                    } catch (e: Exception) {
                        lastException = e
                        // Handle auth errors explicitly first
                        if (e is FirebaseAuthException) {
                            _authState.value = AuthState.Error(e.message ?: "Authentication failed: ${'$'}{e.errorCode}")
                            return@launch
                        }
                        // Treat other FirebaseExceptions as transient network issues and retry
                        if (e is FirebaseException) {
                            attempts++
                            kotlinx.coroutines.delay(500L * attempts)
                            continue
                        }
                        // Non-network/non-auth error, break and report
                        break
                    }
                }

                lastException?.let { ex ->
                    when (ex) {
                        is FirebaseAuthException -> _authState.value = AuthState.Error(ex.message ?: "Authentication failed: ${'$'}{ex.errorCode}")
                        is FirebaseException -> _authState.value = AuthState.Error("Network error: check your connection and try again")
                        else -> _authState.value = AuthState.Error(ex.message ?: "Login failed")
                    }
                } ?: run {
                    _authState.value = AuthState.Error("Login failed")
                }

            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send reset email")
            }
        }
    }

    fun resetState() { _authState.value = AuthState.Idle }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)

                // Try with a small retry loop to handle transient network issues
                var attempts = 0
                var lastEx: Exception? = null
                while (attempts < 3) {
                    try {
                        val result = auth.signInWithCredential(credential).await()
                        val firebaseUser = result.user
                        // Save FCM token in background if needed
                        if (firebaseUser != null) {
                            viewModelScope.launch(Dispatchers.IO + backgroundHandler) {
                                try { FCMTokenManager.saveUserFCMToken(firebaseUser.uid) } catch (e: Exception) {
                                    println("Warning: Failed to save FCM token: ${'$'}{e.message}")
                                }
                            }
                        }
                        _authState.value = AuthState.Success
                        return@launch
                    } catch (e: Exception) {
                        lastEx = e
                        if (e is FirebaseAuthException) {
                            _authState.value = AuthState.Error(e.message ?: "Google sign-in failed: ${'$'}{e.errorCode}")
                            return@launch
                        }
                        if (e is FirebaseException) {
                            attempts++
                            kotlinx.coroutines.delay(500L * attempts)
                            continue
                        }
                        break
                    }
                }

                lastEx?.let { ex ->
                    when (ex) {
                        is FirebaseAuthException -> _authState.value = AuthState.Error(ex.message ?: "Google sign-in failed: ${'$'}{ex.errorCode}")
                        is FirebaseException -> _authState.value = AuthState.Error("Network error: check your connection and try again")
                        else -> _authState.value = AuthState.Error(ex.message ?: "Google sign-in failed")
                    }
                } ?: run {
                    // If we reach here without setting state, set a generic error
                    _authState.value = AuthState.Error("Google sign-in failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google sign-in failed")
            }
        }
    }

    // End of AuthViewModel class
}
