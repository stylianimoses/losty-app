package com.fyp.losty.claims

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.AppViewModel
import com.fyp.losty.ClaimEvent
import com.fyp.losty.MyClaimsState
import com.fyp.losty.Post
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.ClaimRepository
import com.fyp.losty.data.StorageRepository
import com.fyp.losty.data.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ClaimsViewModel(private val appViewModel: AppViewModel) : ViewModel() {
    private val claimRepository = ClaimRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val storageRepository = StorageRepository()
    private val firestore = FirebaseFirestore.getInstance()
    
    val myClaimsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    val claimsForMyPostsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    private val _claimEventChannel = Channel<ClaimEvent>()
    val claimEvents = _claimEventChannel.receiveAsFlow()

    init {
        loadMyClaims()
        loadClaimsForMyPosts()
    }

    // User B clicks "I Found This!"
    fun reportFoundItem(post: Post, description: String, proofUri: Uri?) = viewModelScope.launch {
        val currentUserId = authRepository.getCurrentUser()?.uid ?: run {
            _claimEventChannel.send(ClaimEvent.Error("You must be logged in."))
            return@launch
        }
        
        try {
            var proofImageUrl: String? = null
            val claimId = UUID.randomUUID().toString()
            if (proofUri != null) {
                proofImageUrl = storageRepository.uploadProofImage(claimId, proofUri)
            }

            // 1. Create a "Claim" which acts as the 'Found' notification
            val user = userRepository.getUserProfile(currentUserId)
            val claimData = hashMapOf<String, Any?>(
                "postId" to post.id,
                "postTitle" to post.title,
                "postOwnerId" to post.authorId, // User A
                "claimerId" to currentUserId,    // User B
                "claimerName" to (user?.displayName ?: "Someone"),
                "status" to "pending",
                "type" to "FOUND_REPORT", // Distinguish from 'Lost' claims
                "timestamp" to System.currentTimeMillis(),
                "answer" to description,
                "proofImageUrl" to proofImageUrl
            )
            
            val finalClaimId = claimRepository.createClaim(claimData.filterValues { it != null } as HashMap<String, Any>)

            // 2. Create notification for owner
            appViewModel.sendNotification(
                recipientId = post.authorId,
                type = "FOUND_REPORT",
                fromUserName = user?.displayName ?: "Someone",
                message = "Someone found your item: '${post.title}'",
                postId = post.id,
                claimId = finalClaimId
            )

            // 3. Create or get conversation and send navigation event
            appViewModel.getOrCreateConversation(
                postId = post.id,
                postTitle = post.title,
                postImageUrl = post.imageUrls.firstOrNull() ?: "",
                postOwnerId = post.authorId,
                postOwnerName = post.authorName
            ) { result ->
                viewModelScope.launch {
                    result.onSuccess { conversationId ->
                        _claimEventChannel.send(ClaimEvent.ReportSuccess(conversationId))
                    }.onFailure {
                        _claimEventChannel.send(ClaimEvent.Error("Report sent, but failed to open chat."))
                    }
                }
            }
            
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error("Failed to report: ${e.message}"))
        }
    }

    fun markPostAsResolved(postId: String, claimId: String) = viewModelScope.launch {
        try {
            // 1. Update Post status to RESOLVED
            firestore.collection("posts").document(postId)
                .update("status", "RESOLVED")
                .await()
            
            // 2. Update the specific claim/report to APPROVED
            claimRepository.approveClaim(claimId)
            
            _claimEventChannel.send(ClaimEvent.Success("Item marked as found! Post closed."))
            loadMyClaims()
            loadClaimsForMyPosts()
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error("Error closing post."))
        }
    }

    fun createClaim(post: Post) = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: run { 
            _claimEventChannel.send(ClaimEvent.Error("You must be logged in."))
            return@launch 
        }
        if (userId == post.authorId) { 
            _claimEventChannel.send(ClaimEvent.Error("You cannot claim your own item."))
            return@launch 
        }

        try {
            val hasClaimed = claimRepository.getExistingClaim(post.id, userId)
            if (hasClaimed) {
                _claimEventChannel.send(ClaimEvent.Error("You have already claimed this item."))
                return@launch
            }
            val user = userRepository.getUserProfile(userId)
            val claimData = hashMapOf<String, Any>(
                "postId" to post.id,
                "postTitle" to post.title,
                "postOwnerId" to post.authorId,
                "claimerId" to userId,
                "claimerName" to (user?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"),
                "status" to "pending",
                "claimedAt" to System.currentTimeMillis()
            )
            val finalClaimId = claimRepository.createClaim(claimData)

            // Create notification for owner
            appViewModel.sendNotification(
                recipientId = post.authorId,
                type = "CLAIM",
                fromUserName = user?.displayName ?: "Someone",
                message = "New claim for your item: '${post.title}'",
                postId = post.id,
                claimId = finalClaimId
            )

            _claimEventChannel.send(ClaimEvent.Success("Claim request sent!"))
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error(e.message ?: "Failed to create claim"))
        }
    }

    fun loadMyClaims() = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: run {
            myClaimsState.value = MyClaimsState.Error("Not logged in")
            return@launch
        }
        myClaimsState.value = MyClaimsState.Loading
        try {
            myClaimsState.value = MyClaimsState.Success(claimRepository.loadMyClaims(userId))
        } catch (e: Exception) { 
            myClaimsState.value = MyClaimsState.Error(e.message ?: "Failed to load my claims")
        }
    }

    fun loadClaimsForMyPosts() = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: run {
            claimsForMyPostsState.value = MyClaimsState.Error("Not logged in")
            return@launch
        }
        claimsForMyPostsState.value = MyClaimsState.Loading
        try {
            claimsForMyPostsState.value = MyClaimsState.Success(claimRepository.loadClaimsForMyPosts(userId))
        } catch (e: Exception) {
            claimsForMyPostsState.value = MyClaimsState.Error(e.message ?: "Failed to load claims for my posts")
        }
    }

    fun approveClaim(claimId: String) = viewModelScope.launch {
        try {
            claimRepository.approveClaim(claimId)
            loadMyClaims()
            loadClaimsForMyPosts()
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error("Approval failed"))
        }
    }

    fun rejectClaim(claimId: String) = viewModelScope.launch {
        try {
            claimRepository.rejectClaim(claimId)
            loadMyClaims()
            loadClaimsForMyPosts()
        } catch (e: Exception) {
            _claimEventChannel.send(ClaimEvent.Error("Rejection failed"))
        }
    }
}
