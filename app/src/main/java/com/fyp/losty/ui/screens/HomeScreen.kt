package com.fyp.losty.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.ClaimEvent
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.R
import com.fyp.losty.claims.ClaimsViewModel
import com.fyp.losty.ui.components.BottomNavigationBar
import com.fyp.losty.ui.components.VerificationTipsDialog
import com.fyp.losty.ui.theme.*
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(appNavController: NavController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter states
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPeriod by remember { mutableStateOf("Any time") }
    val sheetState = rememberModalBottomSheetState()

    val appViewModel: AppViewModel = viewModel()
    val claimsViewModel: ClaimsViewModel = viewModel {
        ClaimsViewModel(appViewModel)
    }
    val postFeedState by appViewModel.postFeedState.collectAsState()
    val isRefreshing by appViewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, { appViewModel.loadAllPosts(true) })
    val context = LocalContext.current
    val unreadNotificationCount by appViewModel.unreadNotificationCount.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.loadUserProfile()
    }
    
    LaunchedEffect(claimsViewModel, appNavController) {
        claimsViewModel.claimEvents.collect { event ->
            when (event) {
                is ClaimEvent.Success -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ClaimEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                is ClaimEvent.ReportSuccess -> {
                    Toast.makeText(context, "Report sent! Navigating to chat...", Toast.LENGTH_SHORT).show()
                    appNavController.navigate("chat/${event.conversationId}")
                }
            }
        }
    }

    val navBackStackEntry by appNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route
    val selectedRoute = when {
        currentDestination == "main" -> "home"
        currentDestination == "conversations" -> "chat"
        currentDestination?.startsWith("chat") == true -> "chat"
        currentDestination == "profile" -> "profile"
        currentDestination == "my_activity" -> "my_activity"
        else -> "home"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Explore", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    BadgedBox(badge = { if (unreadNotificationCount > 0) Badge { Text("$unreadNotificationCount") } }) {
                        IconButton(onClick = { appNavController.navigate("notifications") }) {
                            Icon(imageVector = Icons.Filled.Notifications, contentDescription = "Notifications", tint = ElectricPink)
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(selectedRoute = selectedRoute, onItemSelected = { route ->
                val destination = when(route) {
                    "home" -> "main"
                    "chat" -> "conversations"
                    "add" -> "create_post"
                    "my_activity" -> "my_activity"
                    "profile" -> "profile"
                    else -> route
                }

                if (currentDestination != destination) {
                    appNavController.navigate(destination) {
                        popUpTo(appNavController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
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
            SearchSection(
                query = searchQuery, 
                onQueryChange = { searchQuery = it },
                onFilterClick = { showFilterSheet = true }
            )
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
                        val filtered = (postFeedState as PostFeedState.Success).posts.filter { 
                            val matchesType = it.type.equals(selectedType, ignoreCase = false)
                            val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) || 
                                              it.location.contains(searchQuery, ignoreCase = true) ||
                                              it.description.contains(searchQuery, ignoreCase = true)
                            val matchesCategory = if (selectedCategory == "All") true else it.category.equals(selectedCategory, ignoreCase = true)
                            
                            val matchesPeriod = when (selectedPeriod) {
                                "Today" -> isWithinToday(it.createdAt)
                                "Yesterday" -> isWithinYesterday(it.createdAt)
                                "Last 7 days" -> isWithinDays(it.createdAt, 7)
                                "Last 30 days" -> isWithinDays(it.createdAt, 30)
                                else -> true 
                            }
                            
                            matchesType && matchesSearch && matchesCategory && matchesPeriod
                        }
                        items(filtered) { post ->
                            PostCard(post = post, navController = appNavController, appViewModel = appViewModel, claimsViewModel = claimsViewModel)
                        }
                    }
                }
                PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterSheetContent(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it },
                onDone = { showFilterSheet = false }
            )
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
                brush = Brush.linearGradient(listOf(DeepPurple, ElectricPink))
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = "Lost things don’t stay lost with Losty!", color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SearchSection(query: String, onQueryChange: (String) -> Unit, onFilterClick: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ElectricPink) },
        trailingIcon = {
            IconButton(onClick = onFilterClick) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_filter_alt_24),
                    contentDescription = "Filter",
                    tint = ElectricPink
                )
            }
        },
        placeholder = { Text("Search items, locations...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        )
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
                containerColor = if (lostSelected) UrgentRed else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (lostSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("Lost Items", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (foundSelected) SafetyTeal else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (foundSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("Found Items", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    onDone: () -> Unit
) {
    val categories = listOf("All", "Electronics", "Wallet", "Bags", "ID/Cards", "Keys", "Pets")
    val periods = listOf("Any time", "Today", "Yesterday", "Last 7 days", "Last 30 days")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("CATEGORY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChipItem(
                    text = category,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("PERIOD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            periods.forEach { period ->
                FilterChipItem(
                    text = period,
                    isSelected = selectedPeriod == period,
                    onClick = { onPeriodSelected(period) },
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", modifier = Modifier.padding(vertical = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterChipItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun FullscreenImage(uri: Uri, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Fullscreen Image",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun PostCard(post: Post, navController: NavController, appViewModel: AppViewModel, claimsViewModel: ClaimsViewModel) {
    val context = LocalContext.current
    var creatingConversation by remember { mutableStateOf(false) }
    val bookmarks by appViewModel.bookmarks.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val isOwner = userProfile.uid == post.authorId
    
    // Dialog states
    var showClaimDialog by remember { mutableStateOf(false) } 
    var showVerificationTips by remember { mutableStateOf(false) }
    var showFoundReportDialog by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }
    
    var userAnswer by remember { mutableStateOf("") }
    var proofImageUri by remember { mutableStateOf<Uri?>(null) }
    var fullscreenImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) proofImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) proofImageUri = null
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }

    fun getTempUri(): Uri {
        val tempFile = File.createTempFile("proof_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorImageUrl,
                    contentDescription = "Author Image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = post.authorName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(text = post.location, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            
            Text(text = post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isLiked = userProfile.uid in post.likes
                IconButton(onClick = { appViewModel.toggleLike(post.id) }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (post.likes.isNotEmpty()) {
                    Text(
                        text = "${post.likes.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                            result.onSuccess { convId ->
                                navController.navigate("chat/$convId")
                            }.onFailure {
                                Toast.makeText(context, "Failed to start chat", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Message", tint = ElectricPink)
                }
                IconButton(onClick = { appViewModel.toggleBookmark(post) }) {
                    val isBookmarked = bookmarks.contains(post.id)
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) ElectricPink else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                
                if (!isOwner && post.status == "active") {
                    if (post.type == "LOST") {
                        Button(
                            onClick = { showVerificationTips = true }, 
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPink),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("I Found This!")
                        }
                    } else {
                        Button(
                            onClick = { showClaimDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("This is Mine!")
                        }
                    }
                } else if (post.status != "active") {
                    Text(
                        text = "Resolved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
    
    if (showFullscreenImage && fullscreenImageUri != null) {
        FullscreenImage(uri = fullscreenImageUri!!, onDismiss = { showFullscreenImage = false })
    }

    if (showVerificationTips) {
        VerificationTipsDialog(
            onDismiss = { showVerificationTips = false },
            onConfirm = {
                showVerificationTips = false
                showFoundReportDialog = true 
            }
        )
    }

    if (showFoundReportDialog) {
        AlertDialog(
            onDismissRequest = { showFoundReportDialog = false },
            title = { Text("Report Found Item") },
            text = {
                Column {
                    Text("Provide a brief description to help the owner, and add a photo if possible.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userAnswer, 
                        onValueChange = { userAnswer = it },
                        placeholder = { Text("e.g., Found near the library") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Attach photo proof (optional):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable {
                                if (proofImageUri != null) {
                                    fullscreenImageUri = proofImageUri
                                    showFullscreenImage = true
                                } else {
                                    showImageSourceDialog = true
                                }
                             },
                        contentAlignment = Alignment.Center
                    ) {
                        if (proofImageUri != null) {
                            AsyncImage(model = proofImageUri, contentDescription = "Proof", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Add Photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (userAnswer.isNotBlank()) {
                            claimsViewModel.reportFoundItem(post, userAnswer, proofImageUri)
                            showFoundReportDialog = false
                            userAnswer = ""
                            proofImageUri = null
                        } else {
                            Toast.makeText(context, "Please provide a description.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Send Report", color = ElectricPink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showFoundReportDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClaimDialog) {
        AlertDialog(
            onDismissRequest = { showClaimDialog = false },
            title = { Text("Claim Item") },
            text = {
                Column {
                    Text("The finder has set a security question to verify ownership:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = post.securityQuestion.ifBlank { "Describe this item in detail (e.g., specific marks, contents)." },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userAnswer,
                        onValueChange = { userAnswer = it },
                        placeholder = { Text("Your answer...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Attach photo proof (optional):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { 
                                if (proofImageUri != null) {
                                    fullscreenImageUri = proofImageUri
                                    showFullscreenImage = true
                                } else {
                                    showImageSourceDialog = true
                                }
                             },
                        contentAlignment = Alignment.Center
                    ) {
                        if (proofImageUri != null) {
                            AsyncImage(model = proofImageUri, contentDescription = "Proof", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Take/Add Photo Proof", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (userAnswer.isNotBlank()) {
                            appViewModel.submitClaim(post = post, answer = userAnswer, proofUri = proofImageUri)
                            showClaimDialog = false
                            userAnswer = ""
                            proofImageUri = null
                        } else {
                            Toast.makeText(context, "Please provide some detail/answer", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Submit Claim", color = SafetyTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showClaimDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Choose image source") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Camera") },
                        leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val uri = getTempUri()
                            proofImageUri = uri
                            cameraLauncher.launch(uri)
                            showImageSourceDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            showImageSourceDialog = false
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancel") } }
        )
    }
}

fun isWithinToday(timestamp: Long): Boolean {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar[Calendar.HOUR_OF_DAY] = 0
    calendar[Calendar.MINUTE] = 0
    calendar[Calendar.SECOND] = 0
    calendar[Calendar.MILLISECOND] = 0
    val startOfToday = calendar.timeInMillis
    return timestamp >= startOfToday
}

fun isWithinYesterday(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfToday = calendar.timeInMillis
    val startOfYesterday = startOfToday - (24 * 60 * 60 * 1000)
    return timestamp in startOfYesterday until startOfToday
}

fun isWithinDays(timestamp: Long, days: Int): Boolean {
    val now = System.currentTimeMillis()
    val startTime = now - (days.toLong() * 24 * 60 * 60 * 1000)
    return timestamp >= startTime
}
