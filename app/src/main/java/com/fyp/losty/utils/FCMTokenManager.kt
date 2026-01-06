package com.fyp.losty.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Utility class for managing FCM tokens
 * Call saveUserFCMToken() when the user logs in or when the token is refreshed
 */
object FCMTokenManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val realtime = FirebaseDatabase.getInstance().reference
    // Do not cache a FirebaseMessaging instance (it holds a Context). Get it inside suspending function.

    /**
     * Retrieves the device's FCM token and updates the corresponding user record
     * in the Realtime Database at /Users/{userId}/fcmToken
     */
    suspend fun saveUserFCMToken(userId: String) {
        try {
            // Get the FCM token for this device
            val token = FirebaseMessaging.getInstance().token.await()

            // Update the user node in Realtime Database with the FCM token
            realtime.child("Users").child(userId).child("fcmToken").setValue(token).await()
        } catch (e: Exception) {
            // Propagate the error to callers
            throw Exception("Failed to save FCM token: ${e.message}", e)
        }
    }

    /**
     * Convenience function that uses the current authenticated user
     */
    suspend fun saveCurrentUserFCMToken() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            saveUserFCMToken(userId)
        } else {
            throw Exception("No authenticated user found")
        }
    }
}
