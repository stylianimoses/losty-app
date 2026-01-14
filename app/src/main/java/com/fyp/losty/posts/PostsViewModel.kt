package com.fyp.losty.posts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.SinglePostState
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.PostRepository
import com.fyp.losty.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PostsViewModel : ViewModel() {
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    val myPostsState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val postToEditState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)
    val createPostState = MutableStateFlow<SinglePostState>(SinglePostState.Idle)

    init {
        loadMyPosts()
    }

    fun loadMyPosts() = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: return@launch
        myPostsState.value = PostFeedState.Loading
        try {
            myPostsState.value = PostFeedState.Success(postRepository.getMyPosts(userId))
        } catch (e: Exception) {
            myPostsState.value = PostFeedState.Error(e.message ?: "Failed to load my posts")
        }
    }

    fun getPost(postId: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            val post = postRepository.getPost(postId)
            if (post != null) {
                postToEditState.value = SinglePostState.Success(post)
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
        type: String,
        requiresSecurityCheck: Boolean,
        securityQuestion: String,
        securityAnswer: String
    ) = viewModelScope.launch {
        createPostState.value = SinglePostState.Loading
        val userId = authRepository.getCurrentUser()?.uid ?: run { createPostState.value = SinglePostState.Error("Not logged in"); return@launch }
        val user = userRepository.getUserProfile(userId)

        try {
            val imageUrls = postRepository.uploadPostImages(userId, imageUris)
            val postData = hashMapOf<String, Any>(
                "title" to title,
                "description" to description,
                "category" to category,
                "location" to location,
                "imageUrls" to imageUrls,
                "authorId" to userId,
                "authorName" to (user?.displayName ?: ""),
                "authorImageUrl" to (user?.photoUrl ?: ""),
                "type" to type,
                "createdAt" to System.currentTimeMillis(),
                "status" to "active",
                "requiresSecurityCheck" to requiresSecurityCheck,
                "securityQuestion" to securityQuestion,
                "securityAnswer" to securityAnswer
            )
            postRepository.createPost(postData)
            createPostState.value = SinglePostState.Updated
        } catch (e: Exception) {
            createPostState.value = SinglePostState.Error(e.message ?: "An unknown error occurred.")
        }
    }

    fun updatePost(postId: String, newTitle: String, newDescription: String, newCategory: String, newLocation: String) = viewModelScope.launch {
        postToEditState.value = SinglePostState.Loading
        try {
            postRepository.updatePost(postId, newTitle, newDescription, newCategory, newLocation)
            postToEditState.value = SinglePostState.Updated
        } catch (e: Exception) { postToEditState.value = SinglePostState.Error(e.message ?: "") }
    }

    fun deletePost(post: Post) = viewModelScope.launch {
        try {
            postRepository.deletePost(post)
            loadMyPosts()
        } catch (e: Exception) { /* Handle error */ }
    }
}