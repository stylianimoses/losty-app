package com.fyp.losty.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fyp.losty.AppViewModel
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostyComposeFeed(appViewModel: AppViewModel = viewModel(), navController: NavController) {
    val postFeedState by appViewModel.postFeedState.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val searchQuery = remember { mutableStateOf("") }
    val feedContext = remember { mutableStateOf("LOST") }
    val showFilter = remember { mutableStateOf(false) }

    val ElectricPink = Color(0xFFFF4081)
    val UrgentRed = Color(0xFFD32F2F)
    val SafetyTeal = Color(0xFF009688)
    val TextBlack = Color(0xFF1C1B1F)
    val TextGrey = Color(0xFF9E9E9E)
    val OffWhite = Color(0xFFF5F5F5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Gradient text logo Losty
                        Text(
                            text = "Losty",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextBlack,
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(listOf(Color(0xFF7C4DFF), ElectricPink))
                                )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("notifications") }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = ElectricPink)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("create_post") }) {
                Icon(Icons.Default.Add, contentDescription = "Create Post", tint = ElectricPink)
            }
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { /* home */ }) { Icon(Icons.Default.Home, contentDescription = "Home", tint = ElectricPink) }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { navController.navigate("conversations") }) { Icon(Icons.Default.Email, contentDescription = "Messages", tint = ElectricPink) }
                IconButton(onClick = { navController.navigate("profile") }) { Icon(Icons.Default.Person, contentDescription = "Profile", tint = ElectricPink) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(OffWhite)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { searchQuery.value = it },
                placeholder = { Text("Search items...", color = TextGrey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp)),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = ElectricPink) },
                trailingIcon = {
                    IconButton(onClick = { showFilter.value = !showFilter.value }) { Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = ElectricPink) }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    cursorColor = ElectricPink,
                    focusedBorderColor = ElectricPink,
                    unfocusedBorderColor = TextGrey
                )
            )

            if (showFilter.value) {
                // simple filter row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val categories = listOf("All", "Electronics", "Wallet", "Bags", "Keys", "Pets", "Others")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.take(4).forEach { cat ->
                            FilterChip(label = { Text(cat) }, selected = false, onClick = { /* select */ })
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Tabs with colored borders (no underline), bold fonts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { feedContext.value = "LOST" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (feedContext.value == "LOST") UrgentRed else TextGrey
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(if (feedContext.value == "LOST") UrgentRed else TextGrey, if (feedContext.value == "LOST") UrgentRed else TextGrey))
                    )
                ) {
                    Text("Lost Items", fontWeight = FontWeight.Bold, color = if (feedContext.value == "LOST") UrgentRed else TextGrey)
                }
                OutlinedButton(
                    onClick = { feedContext.value = "FOUND" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (feedContext.value == "FOUND") SafetyTeal else TextGrey
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(if (feedContext.value == "FOUND") SafetyTeal else TextGrey, if (feedContext.value == "FOUND") SafetyTeal else TextGrey))
                    )
                ) {
                    Text("Found Items", fontWeight = FontWeight.Bold, color = if (feedContext.value == "FOUND") SafetyTeal else TextGrey)
                }
            }

            when (val state = postFeedState) {
                is PostFeedState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is PostFeedState.Success -> {
                    // Filter by type if available; fallback to all
                    val all = state.posts
                    val posts = all.filter { it.type.equals(feedContext.value, ignoreCase = true) }.ifEmpty { all }
                    if (posts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No items found.", color = TextBlack)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(posts) { post ->
                                LostyPostCard(
                                    post = post,
                                    currentUserId = userProfile.uid,
                                    onMessage = {
                                        // Link to chat list (bottom menu behavior)
                                        navController.navigate("conversations")
                                    },
                                    onSave = { appViewModel.toggleBookmark(post) },
                                    onClaim = { appViewModel.claimItem(post) }
                                )
                            }
                        }
                    }
                }
                is PostFeedState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: ${state.message}") }
                }
            }
        }
    }
}

@Composable
fun LostyPostCard(post: Post, currentUserId: String, onMessage: () -> Unit, onSave: () -> Unit, onClaim: () -> Unit) {
    // Local color tokens to avoid unresolved references
    val TextBlack = Color(0xFF1C1B1F)
    val TextGrey = Color(0xFF9E9E9E)
    val ElectricPink = Color(0xFFFF4081)
    var bookmarked by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            if (post.imageUrls.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(post.imageUrls.first()).crossfade(true).build(),
                    contentDescription = "Post image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)) {
                        // placeholder avatar
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = post.authorName, fontWeight = FontWeight.Bold, color = TextBlack)
                        Text(text = post.location, fontSize = 12.sp, color = TextGrey)
                    }
                    IconButton(onClick = { /* more */ }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = post.title, fontWeight = FontWeight.Bold, color = TextBlack)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = post.description, maxLines = 3, color = TextBlack)

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (currentUserId != post.authorId) {
                        OutlinedButton(onClick = onMessage) { Text("Message") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { bookmarked = !bookmarked; onSave() }) {
                        if (bookmarked) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Bookmarked", tint = ElectricPink)
                        } else {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark", tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onClaim) { Text("Claim") }
                }
            }
        }
    }
}
