package com.fyp.losty.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fyp.losty.AppViewModel
import com.fyp.losty.PostFeedState
import com.fyp.losty.Post

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Suppress("unused", "UNUSED_VARIABLE", "UNUSED_VALUE")
@Composable
fun PostsFeedScreen(
    paddingValues: PaddingValues,
    navController: NavController,
    viewModel: AppViewModel
) {
    val postState by viewModel.postFeedState.collectAsState()

    // 1. Add state for the selected Tab ("LOST" or "FOUND")
    var selectedType by remember { mutableStateOf("LOST") }

    // 2. Refresh logic (gesture-based using Material pullRefresh)
    val isRefreshing = postState is PostFeedState.Loading
    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { viewModel.loadAllPosts() })

    Box(modifier = Modifier.padding(paddingValues).fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 3. Add the Tab Row at the top
            TabRow(
                selectedTabIndex = if (selectedType == "LOST") 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedType == "LOST",
                    onClick = { selectedType = "LOST" },
                    text = { Text("Lost Items") }
                )
                Tab(
                    selected = selectedType == "FOUND",
                    onClick = { selectedType = "FOUND" },
                    text = { Text("Found Items") }
                )
            }

            // 4. The Content List
            when (val state = postState) {
                is PostFeedState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PostFeedState.Success -> {
                    // 5. FILTER: Only show posts that match the selected tab
                    val filteredPosts = state.posts.filter { it.type == selectedType }

                    if (filteredPosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No ${selectedType.lowercase()} items found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredPosts) { post ->
                                PostItem(post = post, onClick = {
                                    navController.navigate("post_detail/${post.id}")
                                })
                            }
                        }
                    }
                }
                is PostFeedState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}")
                    }
                }
            }
        }

        PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun PostItem(post: Post, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // Optional: Show a small badge for the type
                AssistChip(
                    onClick = { },
                    label = { Text(post.type) },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = post.description, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClick) { Text("View Details") }
            }
        }
    }
}