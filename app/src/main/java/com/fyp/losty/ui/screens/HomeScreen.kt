package com.fyp.losty.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.fyp.losty.ui.theme.*
import android.widget.Toast
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyp.losty.AppViewModel
import com.fyp.losty.ui.components.PullToRefreshBox
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import com.fyp.losty.R
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun HomeScreen(navController: NavController, appNavController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val appViewModel: AppViewModel = viewModel()
    val postFeedState by appViewModel.postFeedState.collectAsState()
    val samplePosts: List<HomePost> = remember {
        listOf(
            samplePost(title = "Lost Backpack", username = "alex_j", location = "Downtown Park", postId = "post1", authorId = "user_alex"),
            samplePost(title = "Found Keys", username = "maria_d", location = "Central Library", isLost = false, postId = "post2", authorId = "user_maria")
        )
    }

    // Observe internal nav backstack to decide which bottom item should appear selected
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route
    val selectedRoute = when {
        currentDestination == "home" -> "home"
        currentDestination == "conversations" -> "chat"
        currentDestination?.startsWith("chat") == true -> "chat"
        currentDestination == "profile" -> "profile"
        currentDestination == "manage_active_claims" -> "notifications"
        else -> "home"
    }

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text(text = "Explore", color = TextBlack) },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = OffWhite,
                    scrolledContainerColor = OffWhite,
                    titleContentColor = TextBlack
                ),
                actions = {
                    IconButton(onClick = { navController.navigate("manage_active_claims") }) {
                        Icon(imageVector = Icons.Filled.Notifications, contentDescription = "Notifications", tint = ElectricPink)
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(selectedRoute = selectedRoute, onItemSelected = { route ->
                when (route) {
                    "home" -> navController.navigate("home")
                    "chat" -> navController.navigate("conversations")
                    "add" -> appNavController.navigate("create_post")
                    "notifications" -> navController.navigate("manage_active_claims")
                    "profile" -> navController.navigate("profile")
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
            // Wrap feed in PullToRefreshBox
            val isRefreshing = postFeedState is com.fyp.losty.PostFeedState.Loading
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    val selectedType = if (selectedTab == 0) "LOST" else "FOUND"
                    val itemsToShow: List<HomePost> = if (postFeedState is com.fyp.losty.PostFeedState.Success) {
                        val filtered = (postFeedState as com.fyp.losty.PostFeedState.Success).posts.filter { it.type.equals(selectedType, ignoreCase = false) }
                        filtered.map { p ->
                            HomePost(
                                title = p.title,
                                username = p.authorName,
                                location = p.location,
                                likes = 0,
                                caption = p.description,
                                timestamp = "",
                                isLost = p.type == "LOST",
                                postId = p.id,
                                authorId = p.authorId,
                                imageUrl = p.imageUrls.firstOrNull() ?: ""
                            )
                        }
                    } else samplePosts

                    items(itemsToShow) { post: HomePost ->
                        PostCard(post = post, navController = navController)
                    }
                }
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
        trailingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = "Filter", tint = ElectricPink) },
        placeholder = { Text("Search items, locations...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun TabsSection(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val lostColor = UrgentRed
    val foundColor = SafetyTeal

    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = OffWhite
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            selectedContentColor = lostColor,
            unselectedContentColor = TextBlack,
            text = { Text("Lost Items", fontWeight = FontWeight.Bold) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            selectedContentColor = foundColor,
            unselectedContentColor = TextBlack,
            text = { Text("Found Items", fontWeight = FontWeight.Bold) }
        )
    }
    // Simple colored underline to mimic indicator color per selected tab
    Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selectedTab == 0) lostColor else Color.Transparent)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selectedTab == 1) foundColor else Color.Transparent)
        )
    }
}

private data class HomePost(
    val title: String,
    val username: String,
    val location: String,
    val likes: Int,
    val caption: String,
    val timestamp: String,
    val isLost: Boolean,
    val postId: String,
    val authorId: String,
    val imageUrl: String
)

private fun samplePost(
    title: String,
    username: String,
    location: String,
    likes: Int = 124,
    caption: String = "Need this back ASAP",
    timestamp: String = "2h ago",
    isLost: Boolean = true,
    postId: String = "",
    authorId: String = "",
    imageUrl: String = "https://picsum.photos/400"
) = HomePost(title, username, location, likes, caption, timestamp, isLost, postId, authorId, imageUrl)

@Composable
private fun PostCard(post: HomePost, navController: NavController) {
    val appViewModel: AppViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    var creatingConversation by remember { mutableStateOf(false) }
    var liked by remember(post.postId) { mutableStateOf(false) }
    var likeCount by remember(post.postId) { mutableStateOf(post.likes) }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = post.username, color = TextBlack, fontWeight = FontWeight.Bold)
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
                    model = post.imageUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UrgentRed)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(text = if (post.isLost) "LOST" else "FOUND", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    liked = !liked
                    likeCount = if (liked) likeCount + 1 else (likeCount - 1).coerceAtLeast(0)
                }) {
                    Icon(Icons.Filled.Favorite, contentDescription = if (liked) "Unlike" else "Like", tint = if (liked) ElectricPink else TextGrey)
                }
                IconButton(onClick = {
                    if (creatingConversation) return@IconButton
                    if (post.postId.isNotBlank() && post.authorId.isNotBlank()) {
                        creatingConversation = true
                        appViewModel.getOrCreateConversation(
                            postId = post.postId,
                            postTitle = post.title,
                            postImageUrl = post.imageUrl,
                            postOwnerId = post.authorId,
                            postOwnerName = post.username
                        ) { result ->
                            creatingConversation = false
                            if (result.isSuccess) {
                                val conversationId = result.getOrNull() ?: run {
                                    Toast.makeText(context, "Failed to open conversation", Toast.LENGTH_SHORT).show(); return@getOrCreateConversation
                                }
                                navController.navigate("chat/$conversationId")
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Could not open conversation", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // fallback to conversations list
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
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmark", tint = TextGrey)
                }
            }
            Text(text = "$likeCount likes", color = TextBlack, fontWeight = FontWeight.Bold)
            BuildCaption(post.username, post.caption)
            Text(text = post.timestamp, color = TextGrey, style = MaterialTheme.typography.bodySmall)
        }
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

@Composable
private fun BottomNavigationBar(selectedRoute: String, onItemSelected: (String) -> Unit) {
    val items = listOf(
        BottomNavItem("home", Icons.Filled.Home, "Home"),
        BottomNavItem("chat", Icons.AutoMirrored.Filled.Chat, "Chat"),
        BottomNavItem("add", Icons.Outlined.Add, "Add"),
        BottomNavItem("notifications", Icons.Filled.Notifications, "Notifications"),
        BottomNavItem("profile", Icons.Filled.Person, "Profile")
    )

    NavigationBar(containerColor = Color.White) {
        items.forEach { item ->
            val isSelected = item.route == selectedRoute
            val iconScale by animateFloatAsState(targetValue = if (isSelected) 1.18f else 1f, animationSpec = tween(durationMillis = 200))
            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = ElectricPink,
                        modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (isSelected) ElectricPink else TextBlack
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

private data class BottomNavItem(val route: String, val icon: ImageVector, val label: String)
