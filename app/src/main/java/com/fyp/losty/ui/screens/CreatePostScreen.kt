package com.fyp.losty.ui.screens

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.SinglePostState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import com.fyp.losty.R
import com.fyp.losty.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.core.content.FileProvider
import com.fyp.losty.ui.components.LocationPickerMap
import java.io.File
import java.util.*

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
    
    // Security fields
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }

    // UI state
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val createPostState by appViewModel.createPostState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Camera setup
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            selectedImageUris = selectedImageUris + tempImageUri!!
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> selectedImageUris = selectedImageUris + uris }
    )

    fun createTempPictureUri(context: Context): Uri {
        val tempFile = File.createTempFile("losty_img_${System.currentTimeMillis()}", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

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

    if (showLocationPicker) {
        Box(modifier = Modifier.fillMaxSize()) {
            LocationPickerMap(
                onLocationConfirmed = { address ->
                    location = address
                    showLocationPicker = false
                }
            )
            // Add a close button for the map
            IconButton(
                onClick = { showLocationPicker = false },
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .background(Color.White, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
        }
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("New Post", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val errors = mutableListOf<String>()
                                if (title.isBlank()) errors.add("Title")
                                if (selectedCategory == null) errors.add("Category")
                                if (location.isBlank()) errors.add("Location")
                                if (caption.isBlank()) errors.add("Caption")
                                if (selectedImageUris.isEmpty()) errors.add("Photo")
                                
                                if (postType == "FOUND" && (securityQuestion.isBlank() || securityAnswer.isBlank())) {
                                    errors.add("Security Q&A")
                                }

                                if (errors.isNotEmpty()) {
                                    Toast.makeText(context, "Please fill all required fields: ${errors.joinToString()}", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }

                                appViewModel.createPost(
                                    title = title,
                                    description = caption,
                                    category = selectedCategory!!,
                                    location = location,
                                    imageUris = selectedImageUris,
                                    type = postType,
                                    requiresSecurityCheck = (postType == "FOUND"),
                                    securityQuestion = securityQuestion,
                                    securityAnswer = securityAnswer
                                )
                            },
                            enabled = createPostState !is SinglePostState.Loading
                        ) { Text("Share", color = BlueAction) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
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
                Text(text = "Title", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Short descriptive title", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status Toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val lostSelected = postType == "LOST"
                    val foundSelected = postType == "FOUND"
                    Button(
                        onClick = { postType = "LOST" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (lostSelected) UrgentRed else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (lostSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("LOST", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { postType = "FOUND" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (foundSelected) SafetyTeal else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (foundSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("FOUND", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // SECURITY SECTION FOR FOUND ITEMS
                if (postType == "FOUND") {
                    Text("Verification Gatekeeper", fontWeight = FontWeight.Bold, color = SafetyTeal)
                    Text("Add a question only the true owner can answer.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = securityQuestion,
                        onValueChange = { securityQuestion = it },
                        label = { Text("Security Question (e.g. What color is the keychain?)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = { securityAnswer = it },
                        label = { Text("Correct Answer") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Photo Upload Section
                Text(text = "Photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                val strokePx = with(density) { 3.dp.toPx() }
                val cornerPx = with(density) { 12.dp.toPx() }
                val outlineColor = MaterialTheme.colorScheme.outline

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent)
                        .clickable { showImageSourceDialog = true }
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRoundRect(
                                color = outlineColor,
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
                            Icon(painter = painterResource(id = R.drawable.outline_photo_camera_24), contentDescription = "Add Photo", tint = BlueAction, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Add Photo", color = BlueAction)
                        }
                    }
                }

                // Thumbnails row
                if (selectedImageUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedImageUris) { uri ->
                            Box(modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(Color(0xAA000000), shape = RoundedCornerShape(10.dp))
                                    .clickable { selectedImageUris = selectedImageUris.filterNot { it == uri } }, 
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("×", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Category Section
                Text(text = "Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                val categories = listOf("Electronics", "Wallets & Purses", "Bags", "ID/Cards", "Keys", "Clothing", "Books & Stationery", "Jewelry & Watches", "Personal Accessories", "Sports & Equipment", "Cameras & Photography Gear", "Food & Drink Containers", "Miscellaneous")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val selected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { selectedCategory = if (selected) null else cat },
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                        ) {
                            Text(
                                text = cat,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Location Section
                Text(text = "Location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Where was this?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(painter = painterResource(id = R.drawable.outline_add_location_24), contentDescription = "Location", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        IconButton(onClick = { showLocationPicker = true }) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = "Pick on map", tint = ElectricPink)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Caption Section
                Text(text = "Caption", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Describe the item...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    maxLines = 6,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface)
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (createPostState is SinglePostState.Loading) {
                    CircularProgressIndicator()
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Image Source Selection Dialog
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
                            tempImageUri = createTempPictureUri(context)
                            cameraLauncher.launch(tempImageUri!!)
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
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
