package com.fyp.losty.claims

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.ClaimEvent
import com.fyp.losty.MyClaimsState
import com.fyp.losty.Post
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.ClaimRepository
import com.fyp.losty.data.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ClaimsViewModel : ViewModel() {
    private val claimRepository = ClaimRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    val myClaimsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    val claimsForMyPostsState = MutableStateFlow<MyClaimsState>(MyClaimsState.Loading)
    private val _claimEventChannel = Channel<ClaimEvent>()
    val claimEvents = _claimEventChannel.receiveAsFlow()

    init {
        loadMyClaims()
        loadClaimsForMyPosts()
    }

    fun createClaim(post: Post) = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: run { _claimEventChannel.send(ClaimEvent.Error("You must be logged in.")); return@launch }
        if (userId == post.authorId) { _claimEventChannel.send(ClaimEvent.Error("You cannot claim your own item.")); return@launch }

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
                "status" to "PENDING",
                "claimedAt" to System.currentTimeMillis()
            )
            claimRepository.createClaim(claimData)
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
            // Handle error
        }
    }

    fun rejectClaim(claimId: String) = viewModelScope.launch {
        try {
            claimRepository.rejectClaim(claimId)
            loadMyClaims()
            loadClaimsForMyPosts()
        } catch (e: Exception) {
            // Handle error
        }
    }
}