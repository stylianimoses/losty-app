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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel // Import the unified ViewModel
import com.fyp.losty.SinglePostState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import com.fyp.losty.R
import com.fyp.losty.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(navController: NavController, appViewModel: AppViewModel = viewModel()) {
    // Form state
    var postType by remember { mutableStateOf("LOST") } // LOST or FOUND
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // Validation / UI state
    var titleError by remember { mutableStateOf("") }
    var imageError by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf("") }

    val createPostState by appViewModel.createPostState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> selectedImageUris = uris }
    )

    // Handle create post result
    LaunchedEffect(createPostState) {
        when (val state = createPostState) {
            is SinglePostState.Updated -> {
                Toast.makeText(context, "Post created successfully!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            is SinglePostState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Post", fontWeight = FontWeight.Bold, color = TextGrey) },
                // Use an IconButton (arrow) as the back/cancel control and navigate safely
                navigationIcon = {
                    IconButton(onClick = {
                        try {
                            val poppedToMain = navController.popBackStack("main", false)
                            if (!poppedToMain) {
                                navController.navigate("main") {
                                    popUpTo("main") { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        } catch (_: Exception) { /* ignore to prevent crashes */ }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = TextGrey)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            // Reset errors
                            titleError = ""
                            imageError = ""
                            categoryError = ""

                            // Basic validation: title required and at least one image
                            var valid = true
                            if (title.isBlank()) { titleError = "Title is required"; valid = false }
                            if (selectedImageUris.isEmpty()) { imageError = "Upload at least one photo"; valid = false }
                            if (selectedCategory.isNullOrBlank()) { categoryError = "Please select a category"; /*not mandatory, but helpful*/ }

                            if (!valid) {
                                Toast.makeText(context, "Please fix errors before sharing", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            // Call createPost
                            appViewModel.createPost(
                                title = title,
                                description = caption,
                                category = selectedCategory ?: "",
                                location = location,
                                imageUris = selectedImageUris,
                                type = postType
                            )
                        },
                        enabled = createPostState !is SinglePostState.Loading
                    ) { Text("Share", color = BlueAction) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Title Field
            Text(text = "Title", style = MaterialTheme.typography.labelSmall, color = TextGrey)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; if (it.isNotBlank()) titleError = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Short descriptive title", color = TextGrey) },
                singleLine = true
            )
            if (titleError.isNotBlank()) {
                Text(text = titleError, color = UrgentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            // Status Toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lostSelected = postType == "LOST"
                val foundSelected = postType == "FOUND"
                Button(
                    onClick = { postType = "LOST" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lostSelected) UrgentRed else Color(0xFFF0F0F0),
                        contentColor = if (lostSelected) Color.White else TextGrey
                    )
                ) {
                    Text("LOST", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { postType = "FOUND" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        // FOUND should use SafetyTeal when selected
                        containerColor = if (foundSelected) SafetyTeal else Color(0xFFF8F8F8),
                        contentColor = if (foundSelected) Color.White else TextGrey
                    )
                ) {
                    Text("FOUND", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Upload Section
            Text(text = "Photo", style = MaterialTheme.typography.labelSmall, color = TextGrey)
            Spacer(modifier = Modifier.height(8.dp))
            // precompute px values for stroke and corner
            val strokePx = with(density) { 3.dp.toPx() }
            val cornerPx = with(density) { 12.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(0.dp)
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                // Draw dashed border using drawBehind
                Box(modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            color = TextGrey,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), 0f))
                        )
                    }
                )

                if (selectedImageUris.isNotEmpty()) {
                    AsyncImage(
                        model = selectedImageUris[0],
                        contentDescription = "Selected photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painter = painterResource(id = R.drawable.outline_photo_camera_24), contentDescription = "Camera", tint = BlueAction, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Upload Photo", color = BlueAction)
                    }
                }
            }

            // Thumbnails row for selected images
            if (selectedImageUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedImageUris) { uri ->
                        Box(modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            // remove button
                            Box(modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(Color(0xAA000000), shape = RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedImageUris = selectedImageUris.filterNot { it == uri }
                                }, contentAlignment = Alignment.Center
                            ) {
                                Text("×", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (imageError.isNotBlank()) {
                    Text(text = imageError, color = UrgentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                if (imageError.isNotBlank()) {
                    Text(text = imageError, color = UrgentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Section
            Text(text = "Category", style = MaterialTheme.typography.labelSmall, color = TextGrey)
            Spacer(modifier = Modifier.height(8.dp))
            val categories = listOf("Electronics", "Wallets & Purses", "Bags", "ID/Cards", "Keys", "Clothing", "Books & Stationery", "Jewelry & Watches", "Personal Accessories", "Sports & Equipment", "Cameras & Photography Gear", "Food & Drink Containers", "Miscellaneous")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val selected = selectedCategory == cat
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedCategory = if (selected) null else cat },
                        color = if (selected) Color.Black else Color.White,
                        shadowElevation = 0.dp,
                        border = if (!selected) BorderStroke(1.dp, TextGrey) else null
                    ) {
                        // padding controls the chip width to fit text
                        Text(
                            text = cat,
                            color = if (selected) Color.White else TextBlack,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            if (categoryError.isNotBlank()) {
                Text(text = categoryError, color = UrgentRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Section
            Text(text = "Location", style = MaterialTheme.typography.labelSmall, color = TextGrey)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Where was this?", color = TextGrey) },
                leadingIcon = { Icon(painter = painterResource(id = R.drawable.outline_add_location_24), contentDescription = "Location", tint = TextGrey) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Caption Section
            Text(text = "Caption", style = MaterialTheme.typography.labelSmall, color = TextGrey)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Describe the item...", color = TextGrey) },
                singleLine = false,
                maxLines = 6,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Optional: show currently selected values for debugging
            // Text("Type: $postType, Category: ${selectedCategory ?: "None"}")

            if (createPostState is SinglePostState.Loading) {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}