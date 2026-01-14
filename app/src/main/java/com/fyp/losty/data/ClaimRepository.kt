package com.fyp.losty.data

import com.fyp.losty.Claim
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ClaimRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun createClaim(claimData: HashMap<String, Any>) {
        firestore.collection("claims").add(claimData).await()
    }

    suspend fun getExistingClaim(postId: String, userId: String): Boolean {
        val existingClaims = firestore.collection("claims")
            .whereEqualTo("postId", postId)
            .whereEqualTo("claimerId", userId)
            .get()
            .await()
        return !existingClaims.isEmpty
    }

    suspend fun loadMyClaims(userId: String): List<Claim> {
        val query = firestore.collection("claims")
            .whereEqualTo("claimerId", userId)
            .orderBy("claimedAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return query.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = query.documents[i].id) }
    }

    suspend fun loadClaimsForMyPosts(userId: String): List<Claim> {
        val query = firestore.collection("claims")
            .whereEqualTo("postOwnerId", userId)
            .orderBy("claimedAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return query.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = query.documents[i].id) }
    }

    private suspend fun getClaim(claimId: String): Claim? {
        val doc = firestore.collection("claims").document(claimId).get().await()
        return doc.toObject(Claim::class.java)?.copy(id = doc.id)
    }

    suspend fun approveClaim(claimId: String) {
        val claim = getClaim(claimId) ?: throw Exception("Claim not found")

        val postRef = firestore.collection("posts").document(claim.postId)
        val claimRef = firestore.collection("claims").document(claimId)

        // 1. Fetch other pending claims for the same post BEFORE the transaction
        val otherClaimsQuery = firestore.collection("claims")
            .whereEqualTo("postId", claim.postId)
            .whereEqualTo("status", "PENDING")
            .get()
            .await()

        firestore.runTransaction { transaction ->
            // 2. Approve the current claim
            transaction.update(claimRef, "status", "APPROVED")

            // 3. Reject all other pending claims fetched earlier
            for (doc in otherClaimsQuery.documents) {
                if (doc.id != claimId) { // Ensure we don't deny the claim we just approved
                    transaction.update(doc.reference, "status", "DENIED")
                }
            }

            // 4. Mark the original post as claimed
            transaction.update(postRef, "status", "claimed")

            // Transactions in Kotlin must return a value (null is fine if you don't need a result)
            null
        }.await()
    }

    suspend fun rejectClaim(claimId: String) {
        firestore.collection("claims").document(claimId).update("status", "DENIED").await()
    }
}
