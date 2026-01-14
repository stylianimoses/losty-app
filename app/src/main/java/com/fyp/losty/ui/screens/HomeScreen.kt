package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.ClaimEvent
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.R
import com.fyp.losty.ui.components.BottomNavigationBar
import com.fyp.losty.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(appNavController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val appViewModel: AppViewModel = viewModel()
    val postFeedState by appViewModel.postFeedState.collectAsState()
    val isRefreshing by appViewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, { appViewModel.loadAllPosts(true) })
    val context = LocalContext.current
    val unreadNotificationCount by appViewModel.unreadNotificationCount.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.loadUserProfile()
        appViewModel.claimEvents.collect { event ->
            when (event) {
                is ClaimEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ClaimEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Observe internal nav backstack to decide which bottom item should appear selected
    val navBackStackEntry by appNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route
    val selectedRoute = when {
        currentDestination == "home" -> "home"
        currentDestination == "conversations" -> "chat"
        currentDestination?.startsWith("chat") == true -> "chat"
        currentDestination == "profile" -> "profile"
        currentDestination == "my_activity" -> "my_activity"
        // When on notifications screen, keep Home selected since bottom tab was removed
        currentDestination == "manage_active_claims" -> "home"
        else -> "home"
    }

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Explore", color = TextBlack) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = OffWhite,
                    scrolledContainerColor = OffWhite,
                    titleContentColor = TextBlack
                ),
                actions = {
                    BadgedBox(badge = { if (unreadNotificationCount > 0) Badge { Text("$unreadNotificationCount") } }) {
                        IconButton(onClick = { appNavController.navigate("my_activity") }) {
                            Icon(imageVector = Icons.Filled.Notifications, contentDescription = "Notifications", tint = ElectricPink)
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(selectedRoute = selectedRoute, onItemSelected = { route ->
                when (route) {
                    "home" -> appNavController.navigate("home")
                    "chat" -> appNavController.navigate("conversations")
                    "add" -> appNavController.navigate("create_post")
                    "my_activity" -> appNavController.navigate("my_activity")
                    "profile" -> appNavController.navigate("profile")
                }
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            HeaderSection()
            Spacer(modifier = Modifier.height(16.dp))
            SearchSection()
            Spacer(modifier = Modifier.height(16.dp))
            TabsSection(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            Spacer(modifier = Modifier.height(16.dp))

            Box(Modifier.pullRefresh(pullRefreshState)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    val selectedType = if (selectedTab == 0) "LOST" else "FOUND"
                    if (postFeedState is PostFeedState.Success) {
                        val filtered = (postFeedState as PostFeedState.Success).posts.filter { it.type.equals(selectedType, ignoreCase = false) }
                        items(filtered) { post ->
                            PostCard(post = post, navController = appNavController, appViewModel = appViewModel)
                        }
                    }
                }
                PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Losty",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                // Apply gradient to text via brush in the TextStyle
                brush = Brush.linearGradient(listOf(DeepPurple, ElectricPink))
            ),
            // remove explicit color so brush is visible
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = "Lost things don’t stay lost with Losty!", color = TextGrey)
    }
}

@Composable
private fun SearchSection() {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ElectricPink) },
        trailingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.outline_filter_alt_24),
                contentDescription = "Filter",
                tint = ElectricPink
            )
        },
        placeholder = { Text("Search items, locations...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun TabsSection(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val lostSelected = selectedTab == 0
        val foundSelected = selectedTab == 1
        Button(
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (lostSelected) UrgentRed else Color(0xFFF0F0F0),
                contentColor = if (lostSelected) Color.White else TextGrey
            )
        ) {
            Text("Lost Items", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (foundSelected) SafetyTeal else Color(0xFFF0F0F0),
                contentColor = if (foundSelected) Color.White else TextGrey
            )
        ) {
            Text("Found Items", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PostCard(post: Post, navController: NavController, appViewModel: AppViewModel) {
    val context = LocalContext.current
    var creatingConversation by remember { mutableStateOf(false) }
    val userProfile by appViewModel.userProfile.collectAsState()
    val bookmarks by appViewModel.bookmarks.collectAsState()
    
    // --- GATEKEEPER STATE ---
    var showSecurityDialog by remember { mutableStateOf(false) }
    var userAnswer by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorImageUrl,
                    contentDescription = "Author Image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = post.authorName, color = TextBlack, fontWeight = FontWeight.Bold)
                    Text(text = post.location, color = TextGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = TextGrey)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = post.imageUrls.firstOrNull(),
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (post.type == "LOST") UrgentRed else SafetyTeal)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = post.type, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* TODO: Implement likes */ }) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = TextGrey)
                }
                IconButton(onClick = {
                    if (creatingConversation) return@IconButton
                    if (post.id.isNotBlank() && post.authorId.isNotBlank()) {
                        creatingConversation = true
                        appViewModel.getOrCreateConversation(
                            postId = post.id,
                            postTitle = post.title,
                            postImageUrl = post.imageUrls.firstOrNull() ?: "",
                            postOwnerId = post.authorId,
                            postOwnerName = post.authorName
                        ) { result ->
                            creatingConversation = false
                            if (result.isSuccess) {
                                val conversationId = result.getOrNull() ?: run {
                                    Toast.makeText(context, "Failed to open conversation", Toast.LENGTH_SHORT).show(); return@getOrCreateConversation
                                }
                                val encodedName = java.net.URLEncoder.encode(post.authorName, "utf-8")
                                navController.navigate("chat/$conversationId?otherUserName=$encodedName")
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Could not open conversation", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        navController.navigate("conversations")
                    }
                }) {
                    if (creatingConversation) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = TextGrey)
                    }
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = TextGrey)
                }
                Spacer(modifier = Modifier.weight(1f))

                if (post.authorId != userProfile.uid) {
                    Button(
                        onClick = {
                            if (post.type == "FOUND" && post.requiresSecurityCheck) {
                                showSecurityDialog = true // INTERCEPT: Show the Quiz
                            } else {
                                appViewModel.createClaim(post)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal)
                    ) {
                        Text("Claim", color = Color.White)
                    }
                } else {
                    val isBookmarked = bookmarks.contains(post.id)
                    IconButton(onClick = { appViewModel.toggleBookmark(post) }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) ElectricPink else TextGrey
                        )
                    }
                }
            }
            BuildCaption(post.authorName, post.description)
            Text(text = "", color = TextGrey, style = MaterialTheme.typography.bodySmall) // Timestamp removed for now
        }
    }
    
    // --- THE SECURITY DIALOG ---
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("Prove Ownership") },
            text = {
                Column {
                    Text("The finder asks: ", fontWeight = FontWeight.Bold)
                    Text(post.securityQuestion)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userAnswer,
                        onValueChange = { userAnswer = it },
                        placeholder = { Text("Your answer...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Check logic (case-insensitive and trimmed)
                        if (userAnswer.trim().equals(post.securityAnswer.trim(), ignoreCase = true)) {
                            showSecurityDialog = false
                            appViewModel.createClaim(post)
                            Toast.makeText(context, "Verification Success!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect answer. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Verify") }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BuildCaption(username: String, caption: String) {
    Row {
        Text(text = username, fontWeight = FontWeight.Bold, color = TextBlack)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = caption, color = TextBlack)
    }
}
