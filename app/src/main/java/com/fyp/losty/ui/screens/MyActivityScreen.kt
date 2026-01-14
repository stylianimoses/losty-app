package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.fyp.losty.Claim
import com.fyp.losty.MyClaimsState
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.components.TrustScoreCard
import com.fyp.losty.ui.theme.ElectricPink
import com.fyp.losty.ui.theme.SafetyTeal
import com.fyp.losty.ui.theme.UrgentRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyActivityScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel(),
) {
    var selectedTab by remember { mutableStateOf(0) }
    val userProfile by appViewModel.userProfile.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "My Activity", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            
            // Trust Score Section
            TrustScoreCard(score = userProfile.trustScore)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val postItemsSelected = selectedTab == 0
                val claimItemsSelected = selectedTab == 1
                val savedItemsSelected = selectedTab == 2
                
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (postItemsSelected) ElectricPink else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (postItemsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Posts", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (claimItemsSelected) ElectricPink else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (claimItemsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Claims", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (savedItemsSelected) ElectricPink else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (savedItemsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Saved", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            when (selectedTab) {
                0 -> MyPostsTab(appViewModel = appViewModel, navController = navController) { selectedTab = 1 }
                1 -> ClaimsTab(appViewModel = appViewModel, navController = navController)
                2 -> SavedItemsTab(appViewModel = appViewModel, navController = navController)
            }
        }
    }
}


@Composable
private fun MyPostsTab(
    appViewModel: AppViewModel,
    navController: NavController,
    onViewClaims: () -> Unit
) {
    val myPostsState by appViewModel.myPostsState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Post?>(null) }
    var showResolveDialog by remember { mutableStateOf<Post?>(null) }

    LaunchedEffect(Unit) { appViewModel.loadMyPosts() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = myPostsState) {
            is PostFeedState.Loading -> CircularProgressIndicator()
            is PostFeedState.Success -> {
                if (state.posts.isEmpty()) {
                    Text(text = "You haven\'t created any posts yet.", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.posts) { post ->
                            MyPostItem(
                                post = post,
                                onDeleteClick = { showDeleteDialog = post },
                                onEditClick = { navController.navigate("edit_post/${post.id}") },
                                onViewClaimsClick = onViewClaims,
                                onResolveClick = { showResolveDialog = post }
                            )
                        }
                    }
                }
            }
            is PostFeedState.Error -> Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }

    showDeleteDialog?.let { postToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to permanently delete this post?") },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.deletePost(postToDelete)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }

    showResolveDialog?.let { postToResolve ->
        AlertDialog(
            onDismissRequest = { showResolveDialog = null },
            title = { Text("Mark as Solved") },
            text = { Text("Is this item found? This will close the post and remove it from the public feed.") },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.resolvePost(postToResolve.id)
                        showResolveDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal)
                ) { Text("Yes, Solved") }
            },
            dismissButton = { TextButton(onClick = { showResolveDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MyPostItem(post: Post, onDeleteClick: () -> Unit, onEditClick: () -> Unit, onViewClaimsClick: () -> Unit, onResolveClick: () -> Unit) {
    val isResolved = post.status != "active"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isResolved) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = post.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (isResolved) {
                    Surface(color = SafetyTeal, shape = RoundedCornerShape(12.dp)) {
                        Text("SOLVED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = post.description, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isResolved) {
                    TextButton(onClick = onResolveClick) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Solved", color = SafetyTeal, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onViewClaimsClick) { Icon(Icons.Filled.Notifications, contentDescription = "View Claims", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onEditClick) { Icon(Icons.Filled.Edit, contentDescription = "Edit Post", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = onDeleteClick) { Icon(Icons.Filled.Delete, contentDescription = "Delete Post", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ClaimsTab(
    appViewModel: AppViewModel,
    navController: NavController
) {
    val myClaimsState by appViewModel.myClaimsState.collectAsState()
    val claimsForMyPostsState by appViewModel.claimsForMyPostsState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isRefreshing by remember(myClaimsState, claimsForMyPostsState) {
        derivedStateOf { myClaimsState is MyClaimsState.Loading || claimsForMyPostsState is MyClaimsState.Loading }
    }

    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = {
        coroutineScope.launch {
            appViewModel.loadMyClaims()
            appViewModel.loadClaimsForMyPosts()
        }
    })

    LaunchedEffect(Unit) {
        appViewModel.loadMyClaims()
        appViewModel.loadClaimsForMyPosts()
    }

    val claimsOnMyPosts = when (val postsState = claimsForMyPostsState) {
        is MyClaimsState.Success -> postsState.claims
        else -> emptyList()
    }
    val pendingClaimsOnMyPosts = claimsOnMyPosts.filter { it.status == "pending" }
    val otherClaimsOnMyPosts = claimsOnMyPosts.filter { it.status != "pending" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        when (val state = myClaimsState) {
            is MyClaimsState.Loading -> {
                if (!isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            is MyClaimsState.Success -> {
                if (state.claims.isEmpty() && claimsOnMyPosts.isEmpty()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "No claims to manage", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Claims on your posts will appear here", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (pendingClaimsOnMyPosts.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Claims on My Posts - Action Required",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = Color(0xFFFF9800)
                                )
                            }
                            items(pendingClaimsOnMyPosts) { claim ->
                                val notification = (appViewModel.notificationState.collectAsState().value as? com.fyp.losty.NotificationState.Success)?.notifications?.find { it.claimId == claim.id }
                                ClaimApprovalCard(
                                    claim = claim,
                                    onApprove = {
                                        coroutineScope.launch {
                                            appViewModel.approveClaim(claim.id, claim.postId, notification?.id ?: "")
                                            
                                            // Get post details for conversation
                                            val postDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("posts").document(claim.postId).get().await()
                                            
                                            val postTitle = postDoc.getString("title") ?: ""
                                            val postImageUrl = (postDoc.get("imageUrls") as? List<*>)?.firstOrNull() as? String ?: ""
                                            val claimerName = claim.claimerName.ifBlank { "Claimer" }

                                            appViewModel.getOrCreateConversation(
                                                postId = claim.postId,
                                                postTitle = postTitle,
                                                postImageUrl = postImageUrl,
                                                postOwnerId = claim.claimerId, // Send to claimer
                                                postOwnerName = claimerName
                                            ) { result ->
                                                result.onSuccess { convId ->
                                                    navController.navigate("chat/$convId")
                                                }.onFailure {
                                                    Toast.makeText(context, "Claim approved, but failed to start chat", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            
                                            appViewModel.loadClaimsForMyPosts()
                                        }
                                    },
                                    onReject = {
                                        appViewModel.rejectClaim(claim.id, notification?.id ?: "")
                                        Toast.makeText(context, "Claim rejected.", Toast.LENGTH_SHORT).show()
                                        appViewModel.loadClaimsForMyPosts()
                                    }
                                )
                            }
                        }

                        if (otherClaimsOnMyPosts.isNotEmpty()) {
                            item { Text(text = "Other Claims on My Posts", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface) }
                            items(otherClaimsOnMyPosts) { claim -> OwnerClaimCard(claim = claim) }
                        }

                        if (state.claims.isNotEmpty()) {
                            item { Text(text = "My Claims (on others\' posts)", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface) }
                            items(state.claims) { claim -> ClaimCard(claim = claim) }
                        }
                    }
                }
            }
            is MyClaimsState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error) }
        }

        PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun SavedItemsTab(
    appViewModel: AppViewModel,
    navController: NavController
) {
    val bookmarkedPostsState by appViewModel.bookmarkedPostsState.collectAsState()
    val bookmarks by appViewModel.bookmarks.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.loadBookmarkedPosts()
    }
    
    // Auto-refresh when bookmarks change (e.g. unbookmarking an item)
    LaunchedEffect(bookmarks) {
        appViewModel.loadBookmarkedPosts()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = bookmarkedPostsState) {
            is PostFeedState.Loading -> CircularProgressIndicator()
            is PostFeedState.Success -> {
                if (state.posts.isEmpty()) {
                    Text(text = "You haven\'t saved any items yet.", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.posts) { post ->
                            SavedPostItem(
                                post = post,
                                onClick = { navController.navigate("post_detail/${post.id}") },
                                onRemoveBookmark = { appViewModel.toggleBookmark(post) }
                            )
                        }
                    }
                }
            }
            is PostFeedState.Error -> Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SavedPostItem(post: Post, onClick: () -> Unit, onRemoveBookmark: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Image Thumbnail
            AsyncImage(
                model = post.imageUrls.firstOrNull(),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge
                    Surface(
                        color = if (post.type == "FOUND") SafetyTeal else UrgentRed,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = post.type,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = post.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.description,
                    maxLines = 2,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onRemoveBookmark) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Remove Bookmark",
                    tint = ElectricPink
                )
            }
        }
    }
}


@Composable
fun ClaimApprovalCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit) {
    val score = claim.verificationScore

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Claim Request", color = ElectricPink, fontWeight = FontWeight.Bold)
                
                // Scoring Badge
                Surface(
                    color = when {
                        score >= 70 -> Color(0xFF4CAF50)
                        score >= 40 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Match: $score%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = claim.postTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "From: ${claim.claimerName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            if (claim.securityQuestion.isNotBlank()) {
                Text(text = "SECURITY QUESTION:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = claim.securityQuestion, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(text = "PROOFS PROVIDED:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            
            if (claim.answer.isNotBlank()) {
                Box(
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    ).padding(8.dp).fillMaxWidth()
                ) {
                    Text(text = claim.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (claim.proofImageUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = claim.proofImageUrl,
                    contentDescription = "Proof Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Deny Access")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Verify & Chat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OwnerClaimCard(claim: Claim) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = claim.postTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Claim by: ${claim.claimerName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (claim.securityQuestion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Question: ${claim.securityQuestion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
            if (claim.answer.isNotBlank()) {
                Text("Answer: ${claim.answer}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (claim.proofImageUrl != null) {
                AsyncImage(
                    model = claim.proofImageUrl,
                    contentDescription = "Proof Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text("Status: ${claim.status.uppercase()}", color = if (claim.status == "approved") Color(0xFF4CAF50) else if (claim.status == "denied") Color.Red else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ClaimCard(claim: Claim) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = claim.postTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (claim.securityQuestion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Question: ${claim.securityQuestion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Text("Status: ${claim.status.uppercase()}", color = if (claim.status == "approved") Color(0xFF4CAF50) else if (claim.status == "pending") Color(0xFFFF9800) else Color.Red)
        }
    }
}
