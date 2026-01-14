package com.fyp.losty.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageRepository {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    suspend fun uploadProofImage(claimId: String, proofUri: Uri): String {
        val ref = storage.reference.child("proofs/$claimId.jpg")
        ref.putFile(proofUri).await()
        return ref.downloadUrl.await().toString()
    }
}
