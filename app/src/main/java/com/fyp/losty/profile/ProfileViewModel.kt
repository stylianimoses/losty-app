package com.fyp.losty.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.losty.UserProfile
import com.fyp.losty.data.AuthRepository // Added AuthRepository import
import com.fyp.losty.data.UserRepository // Changed from AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val authRepository = AuthRepository() // Added AuthRepository instance
    private val userRepository = UserRepository() // Changed from AppRepository
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: return@launch // Using AuthRepository
        try {
            _userProfile.value = userRepository.getUserProfile(userId) // Using UserRepository
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun updateDisplayName(newName: String) = viewModelScope.launch {
        val userId = authRepository.getCurrentUser()?.uid ?: return@launch // Using AuthRepository
        try {
            userRepository.updateUserDocument(userId, newName) // Using UserRepository
            userRepository.updateUserProfile(newName, _userProfile.value?.photoUrl ?: "") // Using UserRepository
            loadUserProfile() // Refresh profile
        } catch (e: Exception) {
            // Handle error
        }
    }
}