package com.fyp.losty.data

import android.net.Uri
import com.fyp.losty.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    suspend fun uploadProfileImage(userId: String, imageUri: Uri): String {
        val storageRef = storage.reference.child("profile_pictures/$userId.jpg")
        val uploadTask = storageRef.putFile(imageUri).await()
        return uploadTask.storage.downloadUrl.await().toString()
    }

    suspend fun updateUserProfile(displayName: String, photoUrl: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .setPhotoUri(Uri.parse(photoUrl))
            .build()
        auth.currentUser?.updateProfile(profileUpdates)?.await()
    }

    suspend fun createUserDocument(userId: String, userMap: Map<String, Any>) {
        firestore.collection("users").document(userId).set(userMap).await()
    }

    suspend fun updateUserDocument(userId: String, newName: String) {
        firestore.collection("users").document(userId).update(mapOf("username" to newName)).await()
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val userDoc = firestore.collection("users").document(userId).get().await()
        val firebaseUser = auth.currentUser
        return userDoc.toObject(UserProfile::class.java)?.copy(
            uid = userId,
            displayName = firebaseUser?.displayName ?: (userDoc.getString("username") ?: "User"),
            email = firebaseUser?.email ?: (userDoc.getString("email") ?: "No email found"),
            photoUrl = firebaseUser?.photoUrl?.toString() ?: (userDoc.getString("photoUrl") ?: "")
        )
    }
}