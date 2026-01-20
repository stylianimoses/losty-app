package com.fyp.losty.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.fyp.losty.AppViewModel
import com.fyp.losty.ConversationsState
import com.fyp.losty.Message
import com.fyp.losty.MessagesState
import com.fyp.losty.R
import com.fyp.losty.ui.components.BackButton
import com.fyp.losty.ui.theme.ElectricPink
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    navController: NavController,
    appViewModel: AppViewModel = viewModel(),
    otherUserNameArg: String? = null
) {
    val messagesState by appViewModel.messagesState.collectAsState()
    val conversationsState by appViewModel.conversationsState.collectAsState()
    val userProfile by appViewModel.userProfile.collectAsState()
    val currentUserId = userProfile.uid

    var isRefreshing by remember { mutableStateOf(false) }

    val conversation = (conversationsState as? ConversationsState.Success)?.conversations?.find { it.id == conversationId }
    val resolvedOtherName = otherUserNameArg ?: when {
        conversation == null -> "User"
        conversation.participant1Id == currentUserId -> conversation.participant2Name
        else -> conversation.participant1Name
    }

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Fullscreen image state
    var showFullscreenImage by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                appViewModel.sendMessage(conversationId, "", uri)
            }
        }
    )

    fun refresh() = coroutineScope.launch {
        isRefreshing = true
        appViewModel.loadMessages(conversationId, isRefresh = true)
        isRefreshing = false
    }

    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = ::refresh)

    LaunchedEffect(conversationId) {
        appViewModel.loadConversations() 
        appViewModel.loadMessages(conversationId)
    }

    LaunchedEffect(messagesState) {
        if (messagesState is MessagesState.Success) {
            val messages = (messagesState as MessagesState.Success).messages
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    var optionsExpanded by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(navController = navController) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            if (conversation?.postImageUrl?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = conversation.postImageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(resolvedOtherName.take(1).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = resolvedOtherName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (conversation != null) {
                                Text(
                                    text = "Re: ${conversation.postTitle}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = optionsExpanded,
                            onDismissRequest = { optionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.outline_person_alert_24),
                                            contentDescription = "Report",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Report User")
                                    }
                                },
                                onClick = {
                                    optionsExpanded = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        appViewModel.sendMessage(conversationId, messageText.trim())
                        messageText = ""
                    }
                },
                onAttachClick = {
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // --- SAFETY HEADER ---
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tip: Ask for a photo of the item or describe a unique scratch.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f) 
                    .pullRefresh(pullRefreshState)
            ) {
                when (val state = messagesState) {
                    is MessagesState.Loading -> {
                        if (!isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ElectricPink)
                        }
                    }
                    is MessagesState.Success -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.messages) { message ->
                                MessageBubble(
                                    message = message,
                                    isCurrentUser = message.senderId == currentUserId,
                                    onImageClick = {
                                        fullscreenImageUrl = message.imageUrl
                                        showFullscreenImage = true
                                    },
                                    onLongClick = {
                                        if (message.senderId == currentUserId) {
                                            messageToDelete = message
                                        }
                                    }
                                )
                            }
                        }
                    }
                    is MessagesState.Error -> Text("Error: ${state.message}", modifier = Modifier.align(Alignment.Center), color = Color.Red)
                }
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = ElectricPink
                )
            }
        }
    }

    if (showFullscreenImage && fullscreenImageUrl != null) {
        ChatFullscreenImage(url = fullscreenImageUrl!!, onDismiss = { showFullscreenImage = false })
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message?") },
            text = { Text("Are you sure you want to delete this message? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        messageToDelete?.let { appViewModel.deleteMessage(it.id) }
                        messageToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatFullscreenImage(url: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Fullscreen Image",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Close", tint = Color.White) // Using MoreVert as dummy close or just relying on click
        }
    }
}

@Composable
fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach Photo", tint = ElectricPink)
            }

            BasicTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(ElectricPink),
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Type a message...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            val isSendEnabled = messageText.isNotBlank()
            IconButton(
                onClick = onSendClick,
                enabled = isSendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSendEnabled) ElectricPink else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message, 
    isCurrentUser: Boolean,
    onImageClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val bubbleColor = if (isCurrentUser) ElectricPink else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val bubbleShape = if (isCurrentUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Image message",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Crop
                    )
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
