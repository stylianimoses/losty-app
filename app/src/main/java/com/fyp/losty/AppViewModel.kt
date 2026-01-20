package com.fyp.losty

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.claims.calculateVerificationScore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// --- DATA CLASSES ---
data class Post(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val location: String = "",
    val imageUrls: List<String> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String = "",
    val createdAt: Long = 0L,
    val status: String = "",
    val type: String = "",
    val requiresSecurityCheck: Boolean = false,
    val securityQuestion: String = "",
    val securityAnswer: String = "",
    val likes: List<String> = emptyList() // User IDs who liked the post
)

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val trustScore: Int = 0,
    val itemsReturned: Int = 0,
    val fcmToken: String = ""
)

data class Claim(
    val id: String = "",
    val postId: String = "",
    val postTitle: String = "",
    val postOwnerId: String = "",
    val claimerId: String = "",
    val claimerName: String = "",
    val status: String = "",
    val claimedAt: Long = 0L,
    val answer: String = "",
    val proofImageUrl: String? = null,
    val verificationScore: Int = 0,
    val securityQuestion: String = ""
)

data class Notification(
    val id: String = "",
    val type: String = "",
    val fromUserName: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val postId: String = "",
    val claimId: String = "",
    val timestamp: Long = 0L,
    val conversationId: String = ""
)

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0L,
    val read: Boolean = false
)

data class Conversation(
    val id: String = "",
    val postId: String = "",
    val postTitle: String = "",
    val postImageUrl: String = "",
    val participant1Id: String = "",
    val participant1Name: String = "",
    val participant2Id: String = "",
    val participant2Name: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val deletedBy: List<String> = emptyList()
)

data class NotificationSettings(
    val enabled: Boolean = true
)

// --- STATE HOLDERS ---
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser?) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthResult { object Success : AuthResult(); data class Error(val message: String) : AuthResult(); object Idle : AuthResult() }
sealed class PostFeedState { object Loading : PostFeedState(); data class Success(val posts: List<Post>) : PostFeedState(); data class Error(val message: String) : PostFeedState() }
sealed class SinglePostState { object Idle : SinglePostState(); object Loading : SinglePostState(); data class Success(val post: Post) : SinglePostState(); object Updated : SinglePostState(); data class Error(val message: String) : SinglePostState() }
sealed class MyClaimsState { object Loading : MyClaimsState(); data class Success(val claims: List<Claim>) : MyClaimsState(); data class Error(val message: String) : MyClaimsState() }
sealed class NotificationState { object Loading : NotificationState(); data class Success(val notifications: List<Notification>) : NotificationState(); data class Error(val message: String) : NotificationState() }
sealed class ClaimEvent {
    data class Success(val message: String) : ClaimEvent()
    data class Error(val message: String) : ClaimEvent()
    data class ReportSuccess(val conversationId: String) : ClaimEvent()
}
sealed class ConversationsState { object Loading : ConversationsState(); data class Success(val conversations: List<Conversation>) : ConversationsState(); data class Error(val message: String) : ConversationsState() }
sealed class MessagesState { object Loading : MessagesState(); data class Success(val messages: List<Message>) : MessagesState(); data class Error(val message: String) : MessagesState() }


// --- THE UNIFIED VIEWMODEL ---
class AppViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)
    private val _authEvent = MutableSharedFlow<AuthResult>()
    val authEvent = _authEvent.asSharedFlow()
    
    val userProfile = MutableStateFlow(UserProfile())
    val postFeedState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val myPostsState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val bookmarkedPostsState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val postToEditState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)
    val createPostState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)
    val myClaimsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    val claimsForMyPostsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    private val _claimEventChannel = Channel<ClaimEvent>()
    val claimEvents = _claimEventChannel.receiveAsFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    val notificationState = MutableStateFlow<NotificationState>(NotificationState.Loading)

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount
    private var unreadNotificationListener: ListenerRegistration? = null
    private var allNotificationsListener: ListenerRegistration? = null

    private val _isSignedOut = MutableStateFlow(false)
    val isSignedOut: StateFlow<Boolean> = _isSignedOut.asStateFlow()

    val notificationSettings = MutableStateFlow(NotificationSettings())

    private fun calculateVerificationScore(
        userAnswer: String, 
        correctAnswer: String, 
        hasPhotoProof: Boolean
    ): Int {
        var score = 0
        val claimText = userAnswer.lowercase().trim()
        val actualAnswer = correctAnswer.lowercase().trim()

        if (claimText.contains(actualAnswer) && actualAnswer.isNotEmpty()) {
            score += 80
        } else {
            val keywords = actualAnswer.split(" ", ",", ".").filter { it.length > 2 }
            if (keywords.isNotEmpty()) {
                val matches = keywords.count { claimText.contains(it) }
                val matchPercentage = matches.toFloat() / keywords.size
                score += (matchPercentage * 70).toInt()
            }
        }

        if (hasPhotoProof) {
            score += 20
        }

        if (claimText.length > actualAnswer.length + 10) {
            score += 10
        }

        return score.coerceAtMost(100)
    }

    val bookmarks = MutableStateFlow<Set<String>>(emptySet())

    init {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadUserProfile()
                loadAllPosts()
                loadMyPosts()
                loadBookmarks()
                loadBookmarkedPosts()
                loadNotificationSettings()
                listenForUnreadNotifications()
                listenForAllNotifications()
                updateFcmToken()
                _isSignedOut.value = false 
            }
        }
    }

    private fun updateFcmToken() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            val token = messaging.token.await()
            firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .await()
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to update FCM token", e)
        }
    }

    fun sendPasswordReset(email: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            auth.sendPasswordResetEmail(email).await()
            _authEvent.emit(AuthResult.Success)
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "Failed to send reset email")
        } finally {
            if (authState.value !is AuthState.Error) authState.value = AuthState.Idle
        }
    }
    
    fun signOut() = viewModelScope.launch {
        try {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                firestore.collection("users").document(userId)
                    .update("fcmToken", FieldValue.delete())
                    .await()
            }
            
            auth.signOut()
            _isSignedOut.value = true
            authState.value = AuthState.Idle
            userProfile.value = UserProfile()
            bookmarks.value = emptySet()
            unreadNotificationListener?.remove()
            allNotificationsListener?.remove()
        } catch (e: Exception) {
            Log.e("AppViewModel", "Sign out failed: ${e.message}")
        }
    }

    fun loadUserProfile() = viewModelScope.launch {
        val firebaseUser = auth.currentUser ?: return@launch
        try {
            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            userProfile.value = UserProfile(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: (userDoc.getString("username") ?: "User"),
                email = firebaseUser.email ?: (userDoc.getString("email") ?: "No email found"),
                photoUrl = firebaseUser.photoUrl?.toString() ?: (userDoc.getString("photoUrl") ?: ""),
                trustScore = userDoc.getLong("trustScore")?.toInt() ?: 0,
                itemsReturned = userDoc.getLong("itemsReturned")?.toInt() ?: 0,
                fcmToken = userDoc.getString("fcmToken") ?: ""
            )
        } catch (e: Exception) { /* Handle error */ }
    }

    fun updateDisplayName(newName: String) = viewModelScope.launch {
        val user = auth.currentUser ?: run { authState.value = AuthState.Error("Not logged in"); return@launch }
        try {
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
            user.updateProfile(profileUpdates).await()

            firestore.collection("users").document(user.uid)
                .update(mapOf("username" to newName))
                .await()

            loadUserProfile()
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "Failed to update name")
        }
    }

    fun updateProfilePicture(uri: Uri) = viewModelScope.launch {
        val user = auth.currentUser ?: run { authState.value = AuthState.Error("Not logged in"); return@launch }
        try {
            val ref = storage.reference.child("profile_pictures/${user.uid}.jpg")
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setPhotoUri(Uri.parse(downloadUrl))
                .build()
            user.updateProfile(profileUpdates).await()

            firestore.collection("users").document(user.uid)
                .update(mapOf("photoUrl" to downloadUrl))
                .await()

            loadUserProfile()
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to update profile picture: ${e.message}")
            authState.value = AuthState.Error("Failed to upload image.")
        }
    }

    fun loadNotificationSettings() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            val doc = firestore.collection("users").document(userId).collection("settings").document("notifications").get().await()
            if (doc.exists()) {
                val settings = doc.toObject(NotificationSettings::class.java)
                if (settings != null) {
                    notificationSettings.value = settings
                }
            } else {
                val defaultSettings = NotificationSettings()
                firestore.collection("users").document(userId).collection("settings").document("notifications").set(defaultSettings).await()
                notificationSettings.value = defaultSettings
            }
        } catch (e: Exception) { }
    }

    fun updateNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            firestore.collection("users").document(userId).collection("settings").document("notifications").set(settings).await()
            notificationSettings.value = settings
            
            if (settings.enabled) {
                listenForUnreadNotifications()
                listenForAllNotifications()
            } else {
                unreadNotificationListener?.remove()
                allNotificationsListener?.remove()
                _unreadNotificationCount.value = 0
            }
        } catch (e: Exception) { }
    }

    fun loadAllPosts(isRefresh: Boolean = false) = viewModelScope.launch {
        if (isRefresh) _isRefreshing.value = true
        else postFeedState.value = PostFeedState.Loading

        try {
            val snapshot = firestore.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val allPosts = snapshot.documents.mapNotNull { doc ->
                mapDocumentToPost(doc)
            }
            val activePosts = allPosts.filter { it.status == "active" }
            postFeedState.value = PostFeedState.Success(activePosts)
        } catch (e: Exception) {
            postFeedState.value = PostFeedState.Error(e.message ?: "Failed to load posts")
        } finally {
            if (isRefresh) _isRefreshing.value = false
        }
    }

    private fun mapDocumentToPost(doc: com.google.firebase.firestore.DocumentSnapshot): Post? {
        return try {
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
                securityAnswer = doc.getString("securityAnswer") ?: "",
                likes = (doc.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to parse post ${doc.id}", e)
            null
        }
    }
    
    fun loadMyPosts() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        myPostsState.value = PostFeedState.Loading
        try {
            val snapshot = firestore.collection("posts")
                .whereEqualTo("authorId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val myPosts = snapshot.documents.mapNotNull { doc ->
                 mapDocumentToPost(doc)
            }
            myPostsState.value = PostFeedState.Success(myPosts)
        } catch (e: Exception) {
            myPostsState.value = PostFeedState.Error(e.message ?: "Failed to load my posts")
        }
    }

    fun loadBookmarkedPosts() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        bookmarkedPostsState.value = PostFeedState.Loading
        try {
            val snapshot = firestore.collection("bookmarks")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val postIds = snapshot.documents.mapNotNull { it.getString("postId") }
            if (postIds.isEmpty()) {
                bookmarkedPostsState.value = PostFeedState.Success(emptyList())
                return@launch
            }

            val posts = mutableListOf<Post>()
            for (postId in postIds) {
                val postDoc = firestore.collection("posts").document(postId).get().await()
                if (postDoc.exists()) {
                    mapDocumentToPost(postDoc)?.let { posts.add(it) }
                }
            }
            bookmarkedPostsState.value = PostFeedState.Success(posts)
        } catch (e: Exception) {
            bookmarkedPostsState.value = PostFeedState.Error(e.message ?: "Failed to load bookmarked posts")
        }
    }

    fun getPost(postId: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            val doc = firestore.collection("posts").document(postId).get().await()
            if (doc.exists()) {
                val post = mapDocumentToPost(doc)
                if (post != null) {
                    postToEditState.value = SinglePostState.Success(post)
                } else {
                    postToEditState.value = SinglePostState.Error("Failed to parse post data.")
                }
            } else {
                postToEditState.value = SinglePostState.Error("Post not found.")
            }
        } catch (e: Exception) {
            postToEditState.value = SinglePostState.Error(e.message ?: "An unknown error occurred.")
        }
    }

    fun createPost(
        title: String,
        description: String,
        category: String,
        location: String,
        imageUris: List<Uri>,
        type: String = "LOST",
        requiresSecurityCheck: Boolean = false,
        securityQuestion: String = "",
        securityAnswer: String = ""
    ) = viewModelScope.launch {
        createPostState.value = SinglePostState.Loading
        val userId = auth.currentUser?.uid ?: run { createPostState.value = SinglePostState.Error("Not logged in"); return@launch }
        val userName = userProfile.value.displayName
        val userImageUrl = userProfile.value.photoUrl
        try {
            val imageUrls = mutableListOf<String>()
            for (uri in imageUris) {
                val refPath = "post_images/$userId/${UUID.randomUUID()}"
                val ref = storage.reference.child(refPath)
                try {
                    ref.putFile(uri).await()
                    val downloadUrl = ref.downloadUrl.await().toString()
                    imageUrls.add(downloadUrl)
                } catch (e: Exception) {
                    createPostState.value = SinglePostState.Error(e.message ?: "Upload failed")
                    return@launch
                }
            }

            val postData = hashMapOf(
                "title" to title,
                "description" to description,
                "category" to category,
                "location" to location,
                "imageUrls" to imageUrls,
                "authorId" to userId,
                "authorName" to userName,
                "authorImageUrl" to userImageUrl,
                "type" to type,
                "createdAt" to System.currentTimeMillis(),
                "status" to "active",
                "requiresSecurityCheck" to requiresSecurityCheck,
                "securityQuestion" to securityQuestion,
                "securityAnswer" to securityAnswer,
                "likes" to emptyList<String>()
            )

            val newPostRef = firestore.collection("posts").add(postData).await()
            val newPostId = newPostRef.id

            if (type == "FOUND") {
                try {
                    val matches = firestore.collection("posts")
                        .whereEqualTo("type", "LOST")
                        .whereEqualTo("category", category)
                        .whereEqualTo("status", "active")
                        .get()
                        .await()

                    for (doc in matches.documents) {
                        val lostAuthorId = doc.getString("authorId") ?: continue
                        if (lostAuthorId == userId) continue

                        val notifId = UUID.randomUUID().toString()
                        val notification = Notification(
                            id = notifId,
                            type = "MATCH_ALERT",
                            fromUserName = "Losty Match",
                            message = "A new '$category' post was found near $location. Is this yours?",
                            isRead = false,
                            postId = newPostId,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        firestore.collection("users").document(lostAuthorId)
                            .collection("notifications").document(notifId).set(notification)
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Match logic failed: ${e.message}")
                }
            }

            loadAllPosts()
            loadMyPosts()
            createPostState.value = SinglePostState.Updated
        } catch (e: Exception) {
            createPostState.value = SinglePostState.Error(e.message ?: "An unknown error occurred.")
        }
    }

    fun toggleLike(postId: String) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            val postRef = firestore.collection("posts").document(postId)
            val postDoc = postRef.get().await()
            val likes = (postDoc.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (userId in likes) {
                postRef.update("likes", FieldValue.arrayRemove(userId)).await()
            } else {
                postRef.update("likes", FieldValue.arrayUnion(userId)).await()
            }
            
            // Refresh feeds to show updated like count
            loadAllPosts()
            loadMyPosts()
            loadBookmarkedPosts()
        } catch (e: Exception) {
            Log.e("AppViewModel", "Toggle like failed: ${e.message}")
        }
    }

    fun updatePost(postId: String, newTitle: String, newDescription: String, newCategory: String, newLocation: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            firestore.collection("posts").document(postId).update(mapOf("title" to newTitle, "description" to newDescription, "category" to newCategory, "location" to newLocation)).await()
            postToEditState.value = SinglePostState.Updated
        } catch (e: Exception) { postToEditState.value = SinglePostState.Error(e.message ?: "") }
    }

    fun deletePost(post: Post) = viewModelScope.launch {
        try {
            post.imageUrls.forEach { storage.getReferenceFromUrl(it).delete().await() }
            firestore.collection("posts").document(post.id).delete().await()
        } catch (e: Exception) { }
    }

    fun resolvePost(postId: String) = viewModelScope.launch {
        try {
            firestore.collection("posts").document(postId)
                .update("status", "RESOLVED")
                .await()
            loadMyPosts()
            loadAllPosts()
        } catch (e: Exception) { }
    }

    fun finalizeReturn(claimerId: String) = viewModelScope.launch {
        try {
            val userRef = firestore.collection("users").document(claimerId)
            userRef.update(
                mapOf(
                    "trustScore" to FieldValue.increment(10),
                    "itemsReturned" to FieldValue.increment(1)
                )
            ).await()
            if (auth.currentUser?.uid == claimerId) {
                loadUserProfile()
            }
        } catch (e: Exception) { }
    }

    fun penalizeFalseClaim(claimerId: String) = viewModelScope.launch {
        try {
            val userRef = firestore.collection("users").document(claimerId)
            userRef.update("trustScore", FieldValue.increment(-20)).await()
            if (auth.currentUser?.uid == claimerId) {
                loadUserProfile()
            }
        } catch (e: Exception) { }
    }

    fun submitClaim(
        post: Post,
        answer: String,
        proofUri: Uri?
    ) = viewModelScope.launch {
        val currentUser = auth.currentUser ?: return@launch
        val claimId = UUID.randomUUID().toString()
        
        try {
            val existingClaims = firestore.collection("claims")
                .whereEqualTo("postId", post.id)
                .whereEqualTo("claimerId", currentUser.uid)
                .get()
                .await()

            if (!existingClaims.isEmpty) {
                _claimEventChannel.send(ClaimEvent.Error("You have already claimed this item."))
                return@launch
            }

            var finalProofUrl: String? = null

            if (proofUri != null) {
                val ref = storage.reference.child("proofs/$claimId.jpg")
                ref.putFile(proofUri).await()
                finalProofUrl = ref.downloadUrl.await().toString()
            }

            val score = calculateVerificationScore(
                userAnswer = answer,
                correctAnswer = post.securityAnswer,
                hasPhotoProof = finalProofUrl != null
            )

            val claimData = Claim(
                id = claimId,
                postId = post.id,
                postTitle = post.title,
                postOwnerId = post.authorId,
                claimerId = currentUser.uid,
                claimerName = currentUser.displayName ?: "Anonymous",
                status = "pending",
                claimedAt = System.currentTimeMillis(),
                answer = answer,
                proofImageUrl = finalProofUrl,
                verificationScore = score,
                securityQuestion = post.securityQuestion.ifBlank { "Describe this item in detail." }
            )

            firestore.collection("claims").document(claimId).set(claimData).await()
            
            sendNotification(
                recipientId = post.authorId,
                type = "CLAIM",
                fromUserName = currentUser.displayName ?: "Someone",
                message = "New claim for '${post.title}' with $score% match.",
                postId = post.id,
                claimId = claimId
            )

            _claimEventChannel.send(ClaimEvent.Success("Claim submitted! Verification score: $score%"))
            
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error(e.message ?: "Submission failed"))
        }
    }

    fun sendNotification(
        recipientId: String,
        type: String,
        fromUserName: String,
        message: String,
        postId: String = "",
        claimId: String = "",
        conversationId: String = ""
    ) = viewModelScope.launch {
        if (recipientId.isBlank()) return@launch
        try {
            val notifId = UUID.randomUUID().toString()
            val notification = Notification(
                id = notifId,
                type = type,
                fromUserName = fromUserName,
                message = message,
                isRead = false,
                postId = postId,
                claimId = claimId,
                conversationId = conversationId,
                timestamp = System.currentTimeMillis()
            )
            firestore.collection("users").document(recipientId)
                .collection("notifications").document(notifId).set(notification).await()
        } catch (e: Exception) { }
    }

    fun loadMyClaims() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run {
            myClaimsState.value = MyClaimsState.Error("Not logged in")
            return@launch
        }
        myClaimsState.value = MyClaimsState.Loading
        try {
            val query = firestore.collection("claims")
                .whereEqualTo("claimerId", userId)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val claims = query.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = query.documents[i].id) }
            myClaimsState.value = MyClaimsState.Success(claims)
        } catch (e: Exception) {
            myClaimsState.value = MyClaimsState.Error(e.message ?: "Failed to load my claims")
        }
    }

    fun loadClaimsForMyPosts() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        claimsForMyPostsState.value = MyClaimsState.Loading
        try {
            val snapshot = firestore.collection("claims")
                .whereEqualTo("postOwnerId", userId)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get().await()

            val claims = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Claim::class.java)?.copy(id = doc.id)
            }
            claimsForMyPostsState.value = MyClaimsState.Success(claims)
        } catch (e: Exception) {
            claimsForMyPostsState.value = MyClaimsState.Error("Failed to load claims")
        }
    }

    fun approveClaim(claimId: String, postId: String, notificationId: String? = null) = viewModelScope.launch {
        try {
            val claimDoc = firestore.collection("claims").document(claimId).get().await()
            val claimerId = claimDoc.getString("claimerId") ?: ""
            val postTitle = claimDoc.getString("postTitle") ?: "Item"

            firestore.collection("claims").document(claimId).update("status", "approved").await()
            
            val otherClaims = firestore.collection("claims")
                .whereEqualTo("postId", postId)
                .whereEqualTo("status", "pending")
                .get().await()

            for (doc in otherClaims.documents) {
                if (doc.id != claimId) {
                    val otherClaimerId = doc.getString("claimerId") ?: ""
                    doc.reference.update("status", "denied").await()
                    if (otherClaimerId.isNotBlank()) {
                        penalizeFalseClaim(otherClaimerId)
                    }
                }
            }
            
            firestore.collection("posts").document(postId).update("status", "claimed").await()
            
            if (!notificationId.isNullOrBlank()) {
                markNotificationAsRead(notificationId)
            }

            sendNotification(
                recipientId = claimerId,
                type = "CLAIM_APPROVED",
                fromUserName = userProfile.value.displayName.ifBlank { "Owner" },
                message = "Your claim for '$postTitle' has been approved!",
                postId = postId,
                claimId = claimId
            )

            finalizeReturn(auth.currentUser?.uid ?: "")

            loadAllPosts()
            loadClaimsForMyPosts() 
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error("Approval failed"))
        }
    }

    fun rejectClaim(claimId: String, notificationId: String) = viewModelScope.launch {
        try {
            val claimDoc = firestore.collection("claims").document(claimId).get().await()
            val claimerId = claimDoc.getString("claimerId") ?: return@launch
            val postTitle = claimDoc.getString("postTitle") ?: "Item"
            val postId = (claimDoc.getString("postId") ?: "")

            firestore.collection("claims").document(claimId).update("status", "denied").await()
            penalizeFalseClaim(claimerId)

            sendNotification(
                recipientId = claimerId,
                type = "CLAIM_REJECTED",
                fromUserName = userProfile.value.displayName.ifBlank { "Owner" },
                message = "Your claim for '$postTitle' was not approved.",
                postId = postId,
                claimId = claimId
            )

            markNotificationAsRead(notificationId)
            loadClaimsForMyPosts()
        } catch (e: Exception) { }
    }

    fun listenForUnreadNotifications() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _unreadNotificationCount.value = 0
            return
        }

        unreadNotificationListener?.remove()

        val query = firestore.collection("users").document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)

        unreadNotificationListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) { return@addSnapshotListener }
            if (snapshot != null) {
                val count = snapshot.documents.count { doc ->
                    val type = doc.getString("type") ?: ""
                    type in listOf("MESSAGE", "CLAIM", "CLAIM_APPROVED", "CLAIM_REJECTED", "FOUND_REPORT")
                }
                _unreadNotificationCount.value = count
            }
        }
    }

    fun listenForAllNotifications() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            notificationState.value = NotificationState.Error("Not logged in")
            return
        }

        allNotificationsListener?.remove()

        val query = firestore.collection("users").document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        allNotificationsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                notificationState.value = NotificationState.Error(error.message ?: "Unknown error")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val notifications = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Notification::class.java)?.copy(id = doc.id)
                }
                val filtered = notifications.filter { 
                    it.type in listOf("MESSAGE", "CLAIM", "CLAIM_APPROVED", "CLAIM_REJECTED", "FOUND_REPORT")
                }
                notificationState.value = NotificationState.Success(filtered)
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        if (notificationId.isBlank()) return@launch
        try {
            firestore.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update("isRead", true).await()
        } catch (e: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        unreadNotificationListener?.remove()
        allNotificationsListener?.remove()
    }

    val conversationsState = MutableStateFlow<ConversationsState>(ConversationsState.Loading)
    val messagesState = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val currentConversationId = MutableStateFlow<String?>(null)

    fun loadConversations() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { conversationsState.value = ConversationsState.Error("Not logged in"); return@launch }

        try {
            val snapshot = firestore.collection("conversations")
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .get()
                .await()

            val conversations = snapshot.documents.mapNotNull { doc ->
                val deletedBy = (doc.get("deletedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (userId in deletedBy) return@mapNotNull null
                
                val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                if (userId !in participants) return@mapNotNull null
                Conversation(
                    id = doc.id,
                    postId = doc.getString("postId") ?: "",
                    postTitle = doc.getString("postTitle") ?: "",
                    postImageUrl = doc.getString("postImageUrl") ?: "",
                    participant1Id = doc.getString("participant1Id") ?: "",
                    participant1Name = doc.getString("participant1Name") ?: "",
                    participant2Id = doc.getString("participant2Id") ?: "",
                    participant2Name = doc.getString("participant2Name") ?: "",
                    lastMessage = doc.getString("lastMessage") ?: "",
                    lastMessageTime = doc.getLong("lastMessageTime") ?: 0L,
                    unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0,
                    deletedBy = deletedBy
                )
            }

            conversationsState.value = ConversationsState.Success(conversations)
        } catch (e: Exception) {
            conversationsState.value = ConversationsState.Error(e.message ?: "Failed to load chats")
        }
    }

    fun getOrCreateConversation(postId: String, postTitle: String, postImageUrl: String, postOwnerId: String, postOwnerName: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { callback(Result.failure(Exception("Not logged in"))); return@launch }
        val userDisplayName = userProfile.value.displayName.takeIf { it.isNotBlank() } ?: userProfile.value.email

        try {
            val existingConversation = firestore.collection("conversations")
                .whereEqualTo("postId", postId)
                .whereArrayContains("participants", userId)
                .get().await()
                .documents
                .firstOrNull { doc ->
                    val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    postOwnerId in participants
                }

            if (existingConversation != null) {
                firestore.collection("conversations").document(existingConversation.id)
                    .update("deletedBy", FieldValue.arrayRemove(userId))
                
                callback(Result.success(existingConversation.id))
                return@launch
            }

            val conversationData = hashMapOf(
                "postId" to postId,
                "postTitle" to postTitle,
                "postImageUrl" to postImageUrl,
                "participant1Id" to userId,
                "participant1Name" to userDisplayName,
                "participant2Id" to postOwnerId,
                "participant2Name" to postOwnerName,
                "participants" to listOf(userId, postOwnerId),
                "lastMessage" to "",
                "lastMessageTime" to System.currentTimeMillis(),
                "unreadCount" to 0,
                "deletedBy" to emptyList<String>()
            )
            val newConversation = firestore.collection("conversations").add(conversationData).await()
            callback(Result.success(newConversation.id))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    fun loadMessages(conversationId: String, isRefresh: Boolean = false) = viewModelScope.launch {
        if (!isRefresh) {
            currentConversationId.value = conversationId
            messagesState.value = MessagesState.Loading
            firestore.collection("messages")
                .whereEqualTo("conversationId", conversationId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        messagesState.value = MessagesState.Error(error.message ?: "")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.toObjects(Message::class.java).mapIndexed { i, m ->
                            m.copy(id = snapshot.documents[i].id)
                        }
                        messagesState.value = MessagesState.Success(messages)
                    }
                }
        } else {
             try {
                val snapshot = firestore.collection("messages")
                    .whereEqualTo("conversationId", conversationId)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .await()
                val messages = snapshot.toObjects(Message::class.java).mapIndexed { i, m ->
                    m.copy(id = snapshot.documents[i].id)
                }
                messagesState.value = MessagesState.Success(messages)
            } catch (e: Exception) {
                messagesState.value = MessagesState.Error(e.message ?: "Failed to load messages")
            }
        }
    }

    fun sendMessage(conversationId: String, text: String, imageUri: Uri? = null) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        val userName = userProfile.value.displayName

        try {
            var imageUrl: String? = null
            if (imageUri != null) {
                val refPath = "chat_images/$conversationId/${UUID.randomUUID()}.jpg"
                val ref = storage.reference.child(refPath)
                ref.putFile(imageUri).await()
                imageUrl = ref.downloadUrl.await().toString()
            }

            val messageData = hashMapOf(
                "conversationId" to conversationId,
                "senderId" to userId,
                "senderName" to userName,
                "text" to text,
                "imageUrl" to imageUrl,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )

            firestore.collection("messages").add(messageData).await()

            val lastMsgText = if (imageUrl != null && text.isBlank()) "Sent an image" else text
            firestore.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "lastMessage" to lastMsgText,
                        "lastMessageTime" to System.currentTimeMillis(),
                        "deletedBy" to emptyList<String>()
                    )
                )
                .await()

            val conversationDoc = firestore.collection("conversations").document(conversationId).get().await()
            val participant1 = conversationDoc.getString("participant1Id")
            val participant2 = conversationDoc.getString("participant2Id")
            val recipientId = if (participant1 == userId) participant2 else participant1
            
            if (!recipientId.isNullOrBlank()) {
                sendNotification(
                    recipientId = recipientId,
                    type = "MESSAGE",
                    fromUserName = userName,
                    message = if (imageUrl != null) "Sent an image" else "New message: ${text.take(30)}...",
                    postId = conversationDoc.getString("postId") ?: "",
                    conversationId = conversationId
                )
            }
        } catch (e: Exception) { }
    }

    fun deleteMessage(messageId: String) = viewModelScope.launch {
        try {
            firestore.collection("messages").document(messageId).delete().await()
        } catch (e: Exception) { }
    }

    fun deleteConversation(conversationId: String) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            firestore.collection("conversations").document(conversationId)
                .update("deletedBy", FieldValue.arrayUnion(userId))
                .await()
            loadConversations()
        } catch (e: Exception) { }
    }

    fun clearAllChats() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            val conversations = firestore.collection("conversations")
                .whereArrayContains("participants", userId)
                .get()
                .await()

            for (doc in conversations.documents) {
                doc.reference.update("deletedBy", FieldValue.arrayUnion(userId))
            }
            loadConversations()
        } catch (e: Exception) { }
    }

    fun getUserNameById(userId: String, callback: (String) -> Unit) = viewModelScope.launch {
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            callback(userDoc.getString("displayName") ?: "User")
        } catch (e: Exception) {
            callback("User")
        }
    }

    fun toggleBookmark(post: Post) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
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
            loadBookmarks()
            loadBookmarkedPosts()
        } catch (e: Exception) { }
    }

    private fun loadBookmarks() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { bookmarks.value = emptySet(); return@launch }
        try {
            val snapshot = firestore.collection("bookmarks")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val ids = snapshot.documents.mapNotNull { it.getString("postId") }.toSet()
            bookmarks.value = ids
        } catch (e: Exception) {
            bookmarks.value = emptySet()
        }
    }
}
