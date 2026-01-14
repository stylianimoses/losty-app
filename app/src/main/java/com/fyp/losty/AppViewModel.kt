package com.fyp.losty

import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
    // --- NEW SECURITY FIELDS ---
    val requiresSecurityCheck: Boolean = false,
    val securityQuestion: String = "",
    val securityAnswer: String = ""
)

data class UserProfile(val uid: String = "", val displayName: String = "", val email: String = "", val photoUrl: String = "")
data class Claim(val id: String = "", val postId: String = "", val postTitle: String = "", val postOwnerId: String = "", val claimerId: String = "", val status: String = "", val claimedAt: Long = 0L)
data class Notification(
    val id: String = "",
    val type: String = "",
    val fromUserName: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val postId: String = "",
    val claimId: String = "",
    val timestamp: Long = 0L
)

// Updated Message Data Class to include conversationId
data class Message(val id: String = "", val conversationId: String = "", val senderId: String = "", val senderName: String = "", val text: String = "", val timestamp: Long = 0L, val read: Boolean = false)
data class Conversation(val id: String = "", val postId: String = "", val postTitle: String = "", val postImageUrl: String = "", val participant1Id: String = "", val participant1Name: String = "", val participant2Id: String = "", val participant2Name: String = "", val lastMessage: String = "", val lastMessageTime: Long = 0L, val unreadCount: Int = 0)

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
sealed class ClaimEvent { data class Success(val message: String) : ClaimEvent(); data class Error(val message: String) : ClaimEvent() }
sealed class ConversationsState { object Loading : ConversationsState(); data class Success(val conversations: List<Conversation>) : ConversationsState(); data class Error(val message: String) : ConversationsState() }
sealed class MessagesState { object Loading : MessagesState(); data class Success(val messages: List<Message>) : MessagesState(); data class Error(val message: String) : MessagesState() }


// --- THE UNIFIED VIEWMODEL ---
class AppViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // --- STATES & EVENTS ---
    val authState = MutableStateFlow<AuthState>(AuthState.Idle)
    private val _authEvent = MutableSharedFlow<AuthResult>()
    val authEvent = _authEvent.asSharedFlow()
    
    val userProfile = MutableStateFlow(UserProfile())
    val postFeedState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val myPostsState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
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
    private var notificationListener: ListenerRegistration? = null

    // New state to signal sign-out completion for LaunchedEffect
    private val _isSignedOut = MutableStateFlow(false)
    val isSignedOut: StateFlow<Boolean> = _isSignedOut.asStateFlow()

    // --- BOOKMARK CACHE ---
    // Holds the set of postIds the current user has bookmarked
    val bookmarks = MutableStateFlow<Set<String>>(emptySet())

    init {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadUserProfile()
                loadAllPosts()
                loadMyPosts()
                loadBookmarks()
                listenForNotifications()
                // Reset sign out flag if the user logs back in
                _isSignedOut.value = false 
            }
        }
    }

    // --- AUTH ---
    fun registerUser(
        email: String,
        fullName: String,
        username: String,
        password: String,
        imageUri: Uri?
    ) = viewModelScope.launch {
        authState.value = AuthState.Loading

        if (imageUri == null) {
            _authEvent.emit(AuthResult.Error("Please select a profile image."))
            authState.value = AuthState.Idle
            return@launch
        }
        if (email.isBlank() || fullName.isBlank() || username.isBlank() || password.isBlank()) {
            _authEvent.emit(AuthResult.Error("Please fill in all fields."))
            authState.value = AuthState.Idle
            return@launch
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authEvent.emit(AuthResult.Error("Please enter a valid email address."))
            authState.value = AuthState.Idle
            return@launch
        }
        if (password.length < 8) {
            _authEvent.emit(AuthResult.Error("Password must be at least 8 characters."))
            authState.value = AuthState.Idle
            return@launch
        }

        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("User creation failed")

            firebaseUser.sendEmailVerification().await()

            val storageRef = storage.reference.child("profile_pictures/${firebaseUser.uid}.jpg")
            val uploadTask = storageRef.putFile(imageUri).await()
            val photoUrl = uploadTask.storage.downloadUrl.await().toString()

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .setPhotoUri(Uri.parse(photoUrl))
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val userMap = mapOf(
                "fullName" to fullName,
                "username" to username,
                "email" to email,
                "photoUrl" to photoUrl,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(firebaseUser.uid).set(userMap).await()

            authState.value = AuthState.Success(firebaseUser)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "An unknown error occurred"
            val error = if (errorMessage.contains("already in use", ignoreCase = true)) {
                AuthResult.Error("This email is already registered. Please try to sign in.")
            } else {
                AuthResult.Error(errorMessage)
            }
            _authEvent.emit(error)
        } finally {
            authState.value = AuthState.Idle
        }
    }

    fun loginUser(email: String, password: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _authEvent.emit(AuthResult.Error("Please enter a valid email address."))
                authState.value = AuthState.Idle
                return@launch
            }
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null && !user.isEmailVerified) {
                _authEvent.emit(AuthResult.Error("Please verify your email before logging in."))
                authState.value = AuthState.Idle
                return@launch
            }
            authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            _authEvent.emit(AuthResult.Error(e.message ?: "An unknown error occurred"))
        } finally {
             authState.value = AuthState.Idle
        }
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            authState.value = AuthState.Success(authResult.user)
        } catch (e: Exception) {
            _authEvent.emit(AuthResult.Error(e.message ?: "Google Sign-In failed"))
        } finally {
            authState.value = AuthState.Idle
        }
    }

    fun sendPasswordReset(email: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            auth.sendPasswordResetEmail(email).await()
            _authEvent.emit(AuthResult.Success)
        } catch (e: Exception) {
            _authEvent.emit(AuthResult.Error(e.message ?: "Failed to send reset email"))
        } finally {
            authState.value = AuthState.Idle
        }
    }
    
    fun signOut() = viewModelScope.launch {
        try {
            auth.signOut()
            _isSignedOut.value = true // Signal successful sign-out
            // Reset local states to prepare for the next user or state
            authState.value = AuthState.Idle
            userProfile.value = UserProfile()
            bookmarks.value = emptySet()
            // Important: Clear listeners if they were attached
            notificationListener?.remove()
        } catch (e: Exception) {
            // Handle error, though signOut rarely fails in this way
            Log.e("AppViewModel", "Sign out failed: ${e.message}")
        }
    }

    // --- PROFILE ---
    fun loadUserProfile() = viewModelScope.launch {
        val firebaseUser = auth.currentUser ?: return@launch
        try {
            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            userProfile.value = UserProfile(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: (userDoc.getString("username") ?: "User"),
                email = firebaseUser.email ?: (userDoc.getString("email") ?: "No email found"),
                photoUrl = firebaseUser.photoUrl?.toString() ?: (userDoc.getString("photoUrl") ?: "")
            )
        } catch (e: Exception) { /* Handle error */ }
    }

    fun updateDisplayName(newName: String) = viewModelScope.launch {
        val user = auth.currentUser ?: run { authState.value = AuthState.Error("Not logged in"); return@launch }
        try {
            // Update Firebase Auth profile displayName
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
            user.updateProfile(profileUpdates).await()

            // Update Firestore users doc
            firestore.collection("users").document(user.uid)
                .update(mapOf("username" to newName))
                .await()

            // Refresh local userProfile state
            loadUserProfile()
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "Failed to update name")
        }
    }

    // --- POSTS ---
    fun loadAllPosts(isRefresh: Boolean = false) = viewModelScope.launch {
        if (isRefresh) _isRefreshing.value = true
        else postFeedState.value = PostFeedState.Loading

        try {
            val snapshot = firestore.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val allPosts = snapshot.documents.mapNotNull { doc ->
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
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to parse post ${doc.id}", e)
                    null
                }
            }
            val activePosts = allPosts.filter { it.status == "active" }
            postFeedState.value = PostFeedState.Success(activePosts)
        } catch (e: Exception) {
            postFeedState.value = PostFeedState.Error(e.message ?: "Failed to load posts")
        } finally {
            if (isRefresh) _isRefreshing.value = false
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
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to parse post ${doc.id}", e)
                    null
                }
            }
            myPostsState.value = PostFeedState.Success(myPosts)
        } catch (e: Exception) {
            myPostsState.value = PostFeedState.Error(e.message ?: "Failed to load my posts")
        }
    }

    fun getPost(postId: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            val doc = firestore.collection("posts").document(postId).get().await()
            if (doc.exists()) {
                val post = try {
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
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to parse post ${doc.id}", e)
                    null
                }
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
            // Step 1: Upload images and build post data (including required "type")
            val imageUrls = mutableListOf<String>()
            for (uri in imageUris) {
                val refPath = "post_images/$userId/${UUID.randomUUID()}"
                val ref = storage.reference.child(refPath)
                try {
                    ref.putFile(uri).await()
                    val downloadUrl = ref.downloadUrl.await().toString()
                    imageUrls.add(downloadUrl)
                } catch (e: Exception) {
                    val msg = e.message ?: "Upload failed"
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("permission", ignoreCase = true)) {
                        createPostState.value = SinglePostState.Error("Permission denied: you don\'t have access to upload photos")
                    } else {
                        createPostState.value = SinglePostState.Error(msg)
                    }
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
                "type" to type, // Must be present to distinguish LOST vs FOUND
                "createdAt" to System.currentTimeMillis(),
                "status" to "active",
                "requiresSecurityCheck" to requiresSecurityCheck,
                "securityQuestion" to securityQuestion,
                "securityAnswer" to securityAnswer
            )

            // Step 2: Save to Firestore and await success
            firestore.collection("posts").add(postData).await()

            // Step 3: Immediately refresh the feed using one-shot fetching
            loadAllPosts()

            // Step 4: After reload, update state to close the screen
            createPostState.value = SinglePostState.Updated
        } catch (e: Exception) {
            createPostState.value = SinglePostState.Error(e.message ?: "An unknown error occurred.")
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
        } catch (e: Exception) { /* Handle error */ }
    }

    // --- CLAIMS ---
    fun createClaim(post: Post) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { _claimEventChannel.send(ClaimEvent.Error("You must be logged in.")); return@launch }
        if (userId == post.authorId) { _claimEventChannel.send(ClaimEvent.Error("You cannot claim your own item.")); return@launch }

        try {
            // Audit Log: Prevent a user from claiming the same item twice
            val existingClaims = firestore.collection("claims")
                .whereEqualTo("postId", post.id)
                .whereEqualTo("claimerId", userId)
                .get()
                .await()

            if (!existingClaims.isEmpty) {
                _claimEventChannel.send(ClaimEvent.Error("You have already claimed this item."))
                return@launch
            }

            val claimData = hashMapOf(
                "postId" to post.id,
                "postTitle" to post.title,
                "postOwnerId" to post.authorId,
                "claimerId" to userId,
                "claimerName" to (userProfile.value.displayName.takeIf { it.isNotBlank() } ?: "Someone"),
                "status" to "PENDING",
                "claimedAt" to System.currentTimeMillis()
            )
            firestore.collection("claims").add(claimData).await()
            _claimEventChannel.send(ClaimEvent.Success("Claim request sent!"))
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error(e.message ?: "Failed to create claim"))
        }
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
        val userId = auth.currentUser?.uid ?: run {
            claimsForMyPostsState.value = MyClaimsState.Error("Not logged in")
            return@launch
        }
        claimsForMyPostsState.value = MyClaimsState.Loading
        try {
            val query = firestore.collection("claims")
                .whereEqualTo("postOwnerId", userId)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val claims = query.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = query.documents[i].id) }
            claimsForMyPostsState.value = MyClaimsState.Success(claims)
        } catch (e: Exception) {
            claimsForMyPostsState.value = MyClaimsState.Error(e.message ?: "Failed to load claims for my posts")
        }
    }

    fun approveClaim(claimId: String, notificationId: String) = viewModelScope.launch {
        try {
            firestore.collection("claims").document(claimId).update("status", "approved").await()
            val claimDoc = firestore.collection("claims").document(claimId).get().await()
            val postId = claimDoc.getString("postId") ?: return@launch
            val otherClaims = firestore.collection("claims").whereEqualTo("postId", postId).whereEqualTo("status", "pending").get().await()
            otherClaims.documents.forEach { doc ->
                if (doc.id != claimId) { firestore.collection("claims").document(doc.id).update("status", "denied").await() }
            }
            firestore.collection("posts").document(postId).update("status", "claimed").await()
            markNotificationAsRead(notificationId)
            loadAllPosts()
        } catch (e: Exception) { /* Handle error */ }
    }

    fun rejectClaim(claimId: String, notificationId: String) = viewModelScope.launch {
        try {
            firestore.collection("claims").document(claimId).update("status", "denied").await()
            markNotificationAsRead(notificationId)
        } catch (e: Exception) { /* Handle error */ }
    }

    // --- NOTIFICATIONS ---
    fun listenForNotifications() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _unreadNotificationCount.value = 0
            return
        }

        notificationListener?.remove()

        val query = firestore.collection("users").document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)

        notificationListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Notifications", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                _unreadNotificationCount.value = snapshot.size()
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            firestore.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update("isRead", true).await()
        } catch (e: Exception) {
            Log.e("Notifications", "Failed to mark notification as read", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationListener?.remove()
    }

    // --- MESSAGING ---
    val conversationsState = MutableStateFlow<ConversationsState>(ConversationsState.Loading)
    val messagesState = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val currentConversationId = MutableStateFlow<String?>(null)

    // REWRITTEN: Loads chats using 'whereArrayContains' to match index
    fun loadConversations() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { conversationsState.value = ConversationsState.Error("Not logged in"); return@launch }

        try {
            val snapshot = firestore.collection("conversations")
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .get()
                .await()

            val conversations = snapshot.documents.mapNotNull { doc ->
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
                    unreadCount = doc.getLong("unreadCount")?.toInt() ?: 0
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
                "unreadCount" to 0
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

    fun sendMessage(conversationId: String, text: String) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        val userName = userProfile.value.displayName

        try {
            val messageData = hashMapOf(
                "conversationId" to conversationId,
                "senderId" to userId,
                "senderName" to userName,
                "text" to text,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )

            // 1. Add to main messages collection
            firestore.collection("messages").add(messageData).await()

            // 2. Update the conversation preview
            firestore.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "lastMessage" to text,
                        "lastMessageTime" to System.currentTimeMillis()
                        // Optional: Increment unreadCount logic here later
                    )
                )
                .await()
        } catch (e: Exception) { /* Handle error */ }
    }

    fun getUserNameById(userId: String, callback: (String) -> Unit) = viewModelScope.launch {
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            callback(userDoc.getString("displayName") ?: "User")
        } catch (e: Exception) {
            callback("User")
        }
    }

    // --- BOOKMARKS ---
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
            // Refresh bookmark cache
            loadBookmarks()
        } catch (e: Exception) {
            // Optionally expose an error channel/snackbar
        }
    }

    // --- BOOKMARK CACHE LOADER ---
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