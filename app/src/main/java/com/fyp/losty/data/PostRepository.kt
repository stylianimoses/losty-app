package com.fyp.losty.data

import android.net.Uri
import android.util.Log
import com.fyp.losty.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PostRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    suspend fun getAllPosts(): List<Post> {
        val snapshot = firestore.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            } catch (e: Exception) {
                // Fallback to safe manual parsing if toObjects fails
                try {
                     Post(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        category = doc.getString("category") ?: "",
                        location = doc.getString("location") ?: "",
                        imageUrls = (doc.get("imageUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        authorId = doc.getString("authorId") ?: "",
                        authorName = doc.getString("authorName") ?: "",
                        authorImageUrl = doc.getString("authorImageUrl") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        status = doc.getString("status") ?: "",
                        type = doc.getString("type") ?: "LOST",
                        requiresSecurityCheck = doc.getBoolean("requiresSecurityCheck") ?: false,
                        securityQuestion = doc.getString("securityQuestion") ?: "",
                        securityAnswer = doc.getString("securityAnswer") ?: ""
                    )
                } catch (parseEx: Exception) {
                    Log.e("PostRepository", "Failed to parse post ${doc.id}", parseEx)
                    null
                }
            }
        }
    }

    suspend fun getMyPosts(userId: String): List<Post> {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("authorId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Post::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getPost(postId: String): Post? {
        val doc = firestore.collection("posts").document(postId).get().await()
        return doc.toObject(Post::class.java)?.copy(id = doc.id)
    }
    
    suspend fun uploadPostImages(userId: String, imageUris: List<Uri>): List<String> {
        val imageUrls = mutableListOf<String>()
        for (uri in imageUris) {
            val refPath = "post_images/$userId/${UUID.randomUUID()}"
            val ref = storage.reference.child(refPath)
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            imageUrls.add(downloadUrl)
        }
        return imageUrls
    }

    suspend fun createPost(postData: HashMap<String, Any>) {
        firestore.collection("posts").add(postData).await()
    }

    suspend fun updatePost(postId: String, newTitle: String, newDescription: String, newCategory: String, newLocation: String) {
        firestore.collection("posts").document(postId).update(mapOf(
            "title" to newTitle, 
            "description" to newDescription, 
            "category" to newCategory, 
            "location" to newLocation
        )).await()
    }

    suspend fun deletePost(post: Post) {
        post.imageUrls.forEach { storage.getReferenceFromUrl(it).delete().await() }
        firestore.collection("posts").document(post.id).delete().await()
    }
}