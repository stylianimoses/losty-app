package com.fyp.losty.data

import com.fyp.losty.Post
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BookmarkRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun loadBookmarks(userId: String): Set<String> {
        val snapshot = firestore.collection("bookmarks")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getString("postId") }.toSet()
    }

    suspend fun toggleBookmark(post: Post, userId: String) {
        val q = firestore.collection("bookmarks")
            .whereEqualTo("userId", userId)
            .whereEqualTo("postId", post.id)
            .limit(1)
            .get()
            .await()

        if (q.isEmpty) {
            val bookmark = mapOf(
                "userId" to userId,
                "postId" to post.id,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("bookmarks").add(bookmark).await()
        } else {
            firestore.collection("bookmarks").document(q.documents.first().id).delete().await()
        }
    }
}