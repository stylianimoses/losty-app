package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fyp.losty.AppViewModel
import com.fyp.losty.SinglePostState

@Composable
fun EditPostScreen(postId: String?, navController: NavController, appViewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current

    if (postId == null) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Error: Could not load post.", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
        return
    }

    val postState by appViewModel.postToEditState.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    LaunchedEffect(postId) {
        appViewModel.getPost(postId)
    }

    LaunchedEffect(postState) {
        when (val state = postState) {
            is SinglePostState.Success -> {
                title = state.post.title
                description = state.post.description
                category = state.post.category
                location = state.post.location
            }
            is SinglePostState.Updated -> {
                Toast.makeText(context, "Post updated successfully!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            is SinglePostState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (postState is SinglePostState.Loading || postState is SinglePostState.Idle) {
            CircularProgressIndicator()
        }
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { appViewModel.updatePost(postId, title, description, category, location) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = postState !is SinglePostState.Loading
                ) {
                    Text("Update Post")
                }
            }
        }
    }
}
