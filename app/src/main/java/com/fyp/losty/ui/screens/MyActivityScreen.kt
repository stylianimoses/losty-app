package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fyp.losty.AppViewModel
import com.fyp.losty.Claim
import com.fyp.losty.MyClaimsState
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.ElectricPink
import com.fyp.losty.ui.theme.TextBlack
import com.fyp.losty.ui.theme.TextGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyActivityScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel(),
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "My Activity", color = TextBlack, fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val postItemsSelected = selectedTab == 0
                val claimItemsSelected = selectedTab == 1
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (postItemsSelected) ElectricPink else Color(0xFFF0F0F0),
                        contentColor = if (postItemsSelected) Color.White else TextGrey
                    )
                ) {
                    Text("Post Items", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (claimItemsSelected) ElectricPink else Color(0xFFF0F0F0),
                        contentColor = if (claimItemsSelected) Color.White else TextGrey
                    )
                ) {
                    Text("Claim Items", fontWeight = FontWeight.Bold)
                }
            }

            when (selectedTab) {
                0 -> MyPostsTab(appViewModel = appViewModel, navController = navController) { selectedTab = 1 }
                1 -> ClaimsTab(appViewModel = appViewModel)
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

    LaunchedEffect(Unit) { appViewModel.loadMyPosts() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = myPostsState) {
            is PostFeedState.Loading -> CircularProgressIndicator()
            is PostFeedState.Success -> {
                if (state.posts.isEmpty()) {
                    Text(text = "You haven\'t created any posts yet.")
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
                                onViewClaimsClick = onViewClaims
                            )
                        }
                    }
                }
            }
            is PostFeedState.Error -> Text(text = "Error: ${state.message}")
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
}

@Composable
private fun MyPostItem(post: Post, onDeleteClick: () -> Unit, onEditClick: () -> Unit, onViewClaimsClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = post.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = post.description, maxLines = 3)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onViewClaimsClick) { Icon(Icons.Filled.Notifications, contentDescription = "View Claims") }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEditClick) { Icon(Icons.Filled.Edit, contentDescription = "Edit Post") }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) { Icon(Icons.Filled.Delete, contentDescription = "Delete Post") }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ClaimsTab(
    appViewModel: AppViewModel
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
                        Text(text = "No claims to manage", fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
                                        appViewModel.approveClaim(claim.id, notification?.id ?: "")
                                        Toast.makeText(context, "Claim approved! The item has been marked as claimed.", Toast.LENGTH_SHORT).show()
                                        appViewModel.loadClaimsForMyPosts()
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
                            item { Text(text = "Other Claims on My Posts", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(otherClaimsOnMyPosts) { claim -> OwnerClaimCard(claim = claim) }
                        }

                        if (state.claims.isNotEmpty()) {
                            item { Text(text = "My Claims (on others\' posts)", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(state.claims) { claim -> ClaimCard(claim = claim) }
                        }
                    }
                }
            }
            is MyClaimsState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "Error: ${state.message}") }
        }

        PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun ClaimApprovalCard(claim: Claim, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Claim for: ${claim.postTitle}", fontWeight = FontWeight.Bold)
            // You might want to fetch and display the claimer's name
            // For now, we\'ll just show the ID
            Text(text = "Claim by user: ${claim.claimerId}")
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                    Text("Approve")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onReject, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
fun OwnerClaimCard(claim: Claim) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = claim.postTitle, fontWeight = FontWeight.Bold)
            Text("Claim by: ${claim.claimerId}")
            Text("Status: ${claim.status.uppercase()}", color = if (claim.status == "approved") Color(0xFF4CAF50) else if (claim.status == "denied") Color.Red else Color.Black)
        }
    }
}

@Composable
fun ClaimCard(claim: Claim) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = claim.postTitle, fontWeight = FontWeight.Bold)
            Text("Status: ${claim.status.uppercase()}", color = if (claim.status == "approved") Color(0xFF4CAF50) else if (claim.status == "pending") Color(0xFFFF9800) else Color.Red)
        }
    }
}
