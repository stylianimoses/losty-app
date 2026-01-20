package com.fyp.losty.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.fyp.losty.AppViewModel
import com.fyp.losty.Post
import com.fyp.losty.PostFeedState
import com.fyp.losty.R
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.components.TrustScoreCard
import com.fyp.losty.ui.theme.UrgentRed
import com.fyp.losty.ui.theme.SafetyTeal
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val userProfile by appViewModel.userProfile.collectAsState()
    
    val myPostsState by appViewModel.myPostsState.collectAsState()
    val bookmarkedPostsState by appViewModel.bookmarkedPostsState.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    // Image Picker & Camera Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) appViewModel.updateProfilePicture(uri)
    }

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            appViewModel.updateProfilePicture(tempImageUri!!)
        }
    }

    fun createTempUri(): Uri {
        val tempFile = File.createTempFile("profile_tmp_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

    LaunchedEffect(Unit) {
        appViewModel.loadUserProfile()
        appViewModel.loadMyPosts()
        appViewModel.loadBookmarkedPosts()
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(navController = navController) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Profile Picture
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { showImageSourceDialog = true },
                contentAlignment = Alignment.BottomEnd
            ) {
                val painter = if (userProfile.photoUrl.isNotEmpty()) {
                    rememberAsyncImagePainter(userProfile.photoUrl)
                } else {
                    painterResource(id = R.drawable.outline_account_circle_24)
                }
                Image(
                    painter = painter,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                
                // Camera Overlay Icon
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp),
                    shadowElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Picture",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Name & Edit Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = userProfile.displayName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { newName = userProfile.displayName; showEditNameDialog = true }) {
                    Icon(Icons.Default.Create, contentDescription = "Edit Name", tint = Color(0xFFE91E63))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Email
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = userProfile.email,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Trust Score Card
            TrustScoreCard(score = userProfile.trustScore)

            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            val postCount = when (val state = myPostsState) {
                is PostFeedState.Success -> state.posts.size
                else -> 0
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$postCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Posts", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 0.5.dp)

            // Tabs for Posts and Bookmarks
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "My Posts") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmarks") }
                )
            }

            // Post Grid Content
            Box(modifier = Modifier.heightIn(min = 200.dp, max = 1000.dp)) {
                val posts = if (selectedTab == 0) {
                    (myPostsState as? PostFeedState.Success)?.posts ?: emptyList()
                } else {
                    (bookmarkedPostsState as? PostFeedState.Success)?.posts ?: emptyList()
                }

                if (posts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedTab == 0) "No posts yet" else "No bookmarked posts",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val chunkedPosts = posts.chunked(3)
                        chunkedPosts.forEach { rowPosts ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowPosts.forEach { post ->
                                    PostGridItem(post = post, modifier = Modifier.weight(1f)) {
                                        navController.navigate("post_detail/${post.id}")
                                    }
                                }
                                repeat(3 - rowPosts.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New display name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appViewModel.updateDisplayName(newName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Change Profile Picture") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Take Photo") },
                        leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        modifier = Modifier.clickable {
                            tempImageUri = createTempUri()
                            cameraLauncher.launch(tempImageUri!!)
                            showImageSourceDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Choose from Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        modifier = Modifier.clickable {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            showImageSourceDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PostGridItem(post: Post, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clickable { onClick() }
    ) {
        Image(
            painter = rememberAsyncImagePainter(post.imageUrls.firstOrNull()),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Surface(
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.TopStart),
            color = if (post.type == "FOUND") SafetyTeal else UrgentRed,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = post.type,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
