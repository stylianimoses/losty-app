package com.fyp.losty.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.PostFeedState
import com.fyp.losty.data.AuthRepository
import com.fyp.losty.data.PostRepository
import com.fyp.losty.data.BookmarkRepository // Changed from AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val postRepository = PostRepository()
    private val bookmarkRepository = BookmarkRepository() // Changed from AppRepository
    val postFeedState = MutableStateFlow<PostFeedState>(PostFeedState.Loading)
    val bookmarks = MutableStateFlow<Set<String>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        loadAllPosts()
        loadBookmarks()
    }

    fun loadAllPosts(isRefresh: Boolean = false) = viewModelScope.launch {
        if (isRefresh) _isRefreshing.value = true
        else postFeedState.value = PostFeedState.Loading
        try {
            val posts = postRepository.getAllPosts().filter { it.status == "active" }
            postFeedState.value = PostFeedState.Success(posts)
        } catch (e: Exception) {
            postFeedState.value = PostFeedState.Error(e.message ?: "Failed to load feed")
        } finally {
            if (isRefresh) _isRefreshing.value = false
        }
    }

    private fun loadBookmarks() = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: return@launch
        try {
            bookmarks.value = bookmarkRepository.loadBookmarks(userId) // Using BookmarkRepository
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun toggleBookmark(post: com.fyp.losty.Post) = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: return@launch
        try {
            bookmarkRepository.toggleBookmark(post, userId) // Using BookmarkRepository
            loadBookmarks() // Refresh bookmarks after toggling
        } catch (e: Exception) {
            // Handle error
        }
    }
}