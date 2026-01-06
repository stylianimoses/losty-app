package com.fyp.losty

import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// --- DATA CLASSES ---
data class Post(val id: String = "", val title: String = "", val description: String = "", val category: String = "", val location: String = "", val imageUrls: List<String> = emptyList(), val authorId: String = "", val authorName: String = "", val createdAt: Long = 0L, val status: String = "", val type: String = "")
data class UserProfile(val uid: String = "", val displayName: String = "", val email: String = "", val photoUrl: String = "", val phoneNumber: String = "")
data class Claim(val id: String = "", val postId: String = "", val postTitle: String = "", val postOwnerId: String = "", val claimerId: String = "", val status: String = "", val claimedAt: Long = 0L)
// Updated Message Data Class to include conversationId
data class Message(val id: String = "", val conversationId: String = "", val senderId: String = "", val senderName: String = "", val text: String = "", val timestamp: Long = 0L, val read: Boolean = false)
data class Conversation(val id: String = "", val postId: String = "", val postTitle: String = "", val postImageUrl: String = "", val participant1Id: String = "", val participant1Name: String = "", val participant2Id: String = "", val participant2Name: String = "", val lastMessage: String = "", val lastMessageTime: Long = 0L, val unreadCount: Int = 0)

// --- STATE HOLDERS ---
sealed class AuthState { object Idle : AuthState(); object Loading : AuthState(); object Success : AuthState(); data class Error(val message: String) : AuthState() }
sealed class PostFeedState { object Loading : PostFeedState(); data class Success(val posts: List<Post>) : PostFeedState(); data class Error(val message: String) : PostFeedState() }
sealed class SinglePostState { object Idle : SinglePostState(); object Loading : SinglePostState(); data class Success(val post: Post) : SinglePostState(); object Updated : SinglePostState(); data class Error(val message: String) : SinglePostState() }
sealed class MyClaimsState { object Loading : MyClaimsState(); data class Success(val claims: List<Claim>) : MyClaimsState(); data class Error(val message: String) : MyClaimsState() }
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
    val userProfile = MutableStateFlow(UserProfile())
    val postFeedState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val myPostsState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val postToEditState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)
    val createPostState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)
    val myClaimsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    private val _claimEventChannel = Channel<ClaimEvent>()
    val claimEvents = _claimEventChannel.receiveAsFlow()

    // --- BOOKMARK CACHE ---
    // Holds the set of postIds the current user has bookmarked
    val bookmarks = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadAllPosts()
        loadBookmarks()
    }

    // --- AUTH ---
    fun registerUser(email: String, password: String, phoneNumber: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("User creation failed")
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(email.substringBefore("@")).build()
            firebaseUser.updateProfile(profileUpdates).await()
            val userMap = mapOf("displayName" to email.substringBefore("@"), "email" to email, "phoneNumber" to phoneNumber, "createdAt" to System.currentTimeMillis())
            firestore.collection("users").document(firebaseUser.uid).set(userMap).await()
            authState.value = AuthState.Success
        } catch (e: Exception) {
            // Handle case where the email is already used by another account and provide guidance
            if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                try {
                    val methodsResult = auth.fetchSignInMethodsForEmail(email).await()
                    val methods = methodsResult.signInMethods ?: emptyList()
                    val suggestion = when {
                        methods.contains("google.com") -> "Sign in with Google (Google Sign-In) or link providers in account settings."
                        methods.contains("password") -> "An account exists — try signing in or use 'Forgot password' to reset."
                        methods.isNotEmpty() -> "This email is already registered with: ${methods.joinToString()}. Try signing in with that provider."
                        else -> "This email is already registered. Try signing in or reset your password."
                    }
                    authState.value = AuthState.Error("The email address is already in use. $suggestion")
                } catch (inner: Exception) {
                    authState.value = AuthState.Error("The email address is already in use.")
                }
            } else {
                authState.value = AuthState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    fun loginUser(credential: String, password: String) = viewModelScope.launch {
        authState.value = AuthState.Loading
        try {
            val emailToLogin = if (Patterns.EMAIL_ADDRESS.matcher(credential).matches()) credential else {
                val usersQuery = firestore.collection("users").whereEqualTo("phoneNumber", credential).limit(1).get().await()
                if (usersQuery.isEmpty) throw Exception("No account found with this phone number.")
                usersQuery.documents.first().getString("email") ?: throw Exception("Could not retrieve email for phone number.")
            }
            auth.signInWithEmailAndPassword(emailToLogin, password).await()
            authState.value = AuthState.Success
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "An unknown error occurred")
        }
    }

    // --- PROFILE ---
    fun loadUserProfile() = viewModelScope.launch {
        val firebaseUser = auth.currentUser ?: return@launch
        try {
            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            userProfile.value = UserProfile(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "User",
                email = firebaseUser.email ?: "No email found",
                photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                phoneNumber = userDoc.getString("phoneNumber") ?: ""
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
                .update(mapOf("displayName" to newName))
                .await()

            // Refresh local userProfile state
            loadUserProfile()
        } catch (e: Exception) {
            authState.value = AuthState.Error(e.message ?: "Failed to update name")
        }
    }

    // --- POSTS ---
    fun loadAllPosts() = viewModelScope.launch {
        // Note: We don't set 'Loading' here to avoid flashing the screen
        try {
            val snapshot = firestore.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val allPosts = snapshot.toObjects(Post::class.java).mapIndexed { i, p ->
                p.copy(id = snapshot.documents[i].id)
            }
            val activePosts = allPosts.filter { it.status == "active" }
            postFeedState.value = PostFeedState.Success(activePosts)
        } catch (e: Exception) {
            postFeedState.value = PostFeedState.Error(e.message ?: "Failed to load posts")
        }
    }

    fun loadMyPosts() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            val snapshot = firestore.collection("posts")
                .whereEqualTo("authorId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            myPostsState.value = PostFeedState.Success(snapshot.toObjects(Post::class.java).mapIndexed { i, p -> p.copy(id = snapshot.documents[i].id) })
        } catch (e: Exception) {
            myPostsState.value = PostFeedState.Error(e.message ?: "Failed to load my posts")
        }
    }

    fun getPost(postId: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            val post = firestore.collection("posts").document(postId).get().await().toObject(Post::class.java)?.copy(id = postId)
            if (post != null) { postToEditState.value = SinglePostState.Success(post) } else { postToEditState.value = SinglePostState.Error("Post not found.") }
        } catch (e: Exception) { postToEditState.value = SinglePostState.Error(e.message ?: "") }
    }

    fun createPost(title: String, description: String, category: String, location: String, imageUris: List<Uri>, type: String = "LOST") = viewModelScope.launch {
        createPostState.value = SinglePostState.Loading
        val userId = auth.currentUser?.uid ?: run { createPostState.value = SinglePostState.Error("Not logged in"); return@launch }
        val userName = userProfile.value.displayName
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
                        createPostState.value = SinglePostState.Error("Permission denied: you don't have access to upload photos")
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
                "type" to type, // Must be present to distinguish LOST vs FOUND
                "createdAt" to System.currentTimeMillis(),
                "status" to "active"
            )

            // Step 2: Save to Firestore and await success
            firestore.collection("posts").add(postData).await()

            // Step 3: Immediately refresh the feed using one-shot fetching
            loadAllPosts()

            // Step 4: After reload, update state to close the screen
            createPostState.value = SinglePostState.Updated
        } catch (e: Exception) {
            createPostState.value = SinglePostState.Error(e.message ?: "")
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
    fun claimItem(post: Post) = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { _claimEventChannel.send(ClaimEvent.Error("You must be logged in.")); return@launch }
        if (userId == post.authorId) { _claimEventChannel.send(ClaimEvent.Error("You cannot claim your own item.")); return@launch }
        try {
            val claim = mapOf("postId" to post.id, "postTitle" to post.title, "postOwnerId" to post.authorId, "claimerId" to userId, "status" to "pending", "claimedAt" to System.currentTimeMillis())
            firestore.collection("claims").add(claim).await()
            _claimEventChannel.send(ClaimEvent.Success("Item claimed successfully!"))
        } catch (e: Exception) { _claimEventChannel.send(ClaimEvent.Error(e.message ?: "")) }
    }

    fun loadMyClaims() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { myClaimsState.value = MyClaimsState.Error("Not logged in"); return@launch }
        try {
            val snapshot = firestore.collection("claims")
                .whereEqualTo("claimerId", userId)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val claims = snapshot.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = snapshot.documents[i].id) }
            myClaimsState.value = MyClaimsState.Success(claims)
        } catch (e: Exception) {
            myClaimsState.value = MyClaimsState.Error(e.message ?: "Failed to load claims")
        }
    }

    val claimsForMyPostsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)

    fun loadClaimsForMyPosts() = viewModelScope.launch {
        val userId = auth.currentUser?.uid ?: run { claimsForMyPostsState.value = MyClaimsState.Error("Not logged in"); return@launch }
        try {
            val snapshot = firestore.collection("claims")
                .whereEqualTo("postOwnerId", userId)
                .orderBy("claimedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val claims = snapshot.toObjects(Claim::class.java).mapIndexed { i, c -> c.copy(id = snapshot.documents[i].id) }
            claimsForMyPostsState.value = MyClaimsState.Success(claims)
        } catch (e: Exception) {
            claimsForMyPostsState.value = MyClaimsState.Error(e.message ?: "Failed to load claims for my posts")
        }
    }

    fun approveClaim(claimId: String) = viewModelScope.launch {
        try {
            firestore.collection("claims").document(claimId).update("status", "approved").await()
            val claimDoc = firestore.collection("claims").document(claimId).get().await()
            val postId = claimDoc.getString("postId") ?: return@launch
            val otherClaims = firestore.collection("claims").whereEqualTo("postId", postId).whereEqualTo("status", "pending").get().await()
            otherClaims.documents.forEach { doc ->
                if (doc.id != claimId) { firestore.collection("claims").document(doc.id).update("status", "denied").await() }
            }
            firestore.collection("posts").document(postId).update("status", "claimed").await()
            loadAllPosts()
        } catch (e: Exception) { /* Handle error */ }
    }

    fun rejectClaim(claimId: String) = viewModelScope.launch {
        try { firestore.collection("claims").document(claimId).update("status", "denied").await() } catch (e: Exception) { /* Handle error */ }
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
        val userDisplayName = userProfile.value.displayName

        try {
            val existingConversation = firestore.collection("conversations")
                .whereEqualTo("postId", postId)
                .whereArrayContains("participants", userId)
                .get().await()
                .documents
                .firstOrNull { doc ->
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
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

    // REWRITTEN: Loads messages from the MAIN collection
    fun loadMessages(conversationId: String) = viewModelScope.launch {
        currentConversationId.value = conversationId

        firestore.collection("messages") // Using main collection
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
    }

    // One-shot fetch for messages (for pull-to-refresh or initial load without realtime listeners)
    fun loadMessagesOnce(conversationId: String) = viewModelScope.launch {
        currentConversationId.value = conversationId
        messagesState.value = MessagesState.Loading
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

    fun isBookmarked(postId: String): Boolean = bookmarks.value.contains(postId)
}