package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.Post
import com.fyp.losty.SinglePostState
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.ElectricPink
import com.fyp.losty.ui.theme.SafetyTeal
import com.fyp.losty.ui.theme.UrgentRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val postState by appViewModel.postToEditState.collectAsState()
    val bookmarks by appViewModel.bookmarks.collectAsState()
    val context = LocalContext.current
    var creatingConversation by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        appViewModel.getPost(postId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = postState) {
                is SinglePostState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SinglePostState.Success -> {
                    val post = state.post
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Image Carousel/Single Image
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            AsyncImage(
                                model = post.imageUrls.firstOrNull(),
                                contentDescription = post.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.TopEnd),
                                color = if (post.type == "FOUND") SafetyTeal else UrgentRed,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = post.type,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            // Author Info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = post.authorImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = post.authorName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(text = "Posted on ${formatDate(post.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(text = post.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = ElectricPink, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = post.location, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(text = "Category: ${post.category}", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(text = post.description, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)

                            Spacer(modifier = Modifier.height(100.dp)) // Space for bottom buttons
                        }
                    }

                    // Bottom Action Bar
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(onClick = { appViewModel.toggleBookmark(post) }) {
                                val isBookmarked = bookmarks.contains(post.id)
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = ElectricPink
                                )
                            }
                            
                            Button(
                                onClick = {
                                    if (creatingConversation) return@Button
                                    creatingConversation = true
                                    appViewModel.getOrCreateConversation(
                                        postId = post.id,
                                        postTitle = post.title,
                                        postImageUrl = post.imageUrls.firstOrNull() ?: "",
                                        postOwnerId = post.authorId,
                                        postOwnerName = post.authorName
                                    ) { result ->
                                        creatingConversation = false
                                        result.onSuccess { convId ->
                                            navController.navigate("chat/$convId")
                                        }.onFailure {
                                            Toast.makeText(context, "Failed to start chat", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPink),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Message", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is SinglePostState.Error -> {
                    Text(text = state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
