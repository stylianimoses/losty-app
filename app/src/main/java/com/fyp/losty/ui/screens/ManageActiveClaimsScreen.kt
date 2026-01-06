package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.fyp.losty.MyClaimsState
import com.fyp.losty.ui.components.BackToHomeButton
import com.fyp.losty.ui.components.PullToRefreshBox
import com.fyp.losty.ui.theme.TextBlack

@Composable
fun ManageActiveClaimsScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val myClaimsState by appViewModel.myClaimsState.collectAsState()
    val claimsForMyPostsState by appViewModel.claimsForMyPostsState.collectAsState()
    val context = LocalContext.current

    // Load both types of claims
    LaunchedEffect(Unit) {
        appViewModel.loadMyClaims()
        appViewModel.loadClaimsForMyPosts()
    }

    val isRefreshing = myClaimsState is MyClaimsState.Loading || claimsForMyPostsState is MyClaimsState.Loading

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Notifications", color = TextBlack) },
                navigationIcon = { BackToHomeButton(navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        // Get claims on user's posts (for approval)
        val claimsOnMyPosts = when (val postsState = claimsForMyPostsState) {
            is MyClaimsState.Success -> postsState.claims
            else -> emptyList()
        }
        val pendingClaimsOnMyPosts = claimsOnMyPosts.filter { it.status == "pending" }
        val otherClaimsOnMyPosts = claimsOnMyPosts.filter { it.status != "pending" }

        PullToRefreshBox(isRefreshing = isRefreshing, modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = myClaimsState) {
                is MyClaimsState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is MyClaimsState.Success -> {
                    if (state.claims.isEmpty() && claimsOnMyPosts.isEmpty()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No claims to manage",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Claims on your posts will appear here",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Show pending claims on user's posts FIRST (with approve/reject buttons)
                            if (pendingClaimsOnMyPosts.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Claims on My Posts - Action Required",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color(0xFFFF9800) // Orange
                                    )
                                }
                                items(pendingClaimsOnMyPosts) { claim ->
                                    ClaimApprovalCard(
                                        claim = claim,
                                        onApprove = {
                                            appViewModel.approveClaim(claim.id)
                                            Toast.makeText(context, "Claim approved! The item has been marked as claimed.", Toast.LENGTH_SHORT).show()
                                            appViewModel.loadClaimsForMyPosts()
                                        },
                                        onReject = {
                                            appViewModel.rejectClaim(claim.id)
                                            Toast.makeText(context, "Claim rejected.", Toast.LENGTH_SHORT).show()
                                            appViewModel.loadClaimsForMyPosts()
                                        }
                                    )
                                }
                            }

                            // Show other claims on user's posts (approved/denied)
                            if (otherClaimsOnMyPosts.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Other Claims on My Posts",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(otherClaimsOnMyPosts) { claim ->
                                    OwnerClaimCard(claim = claim)
                                }
                            }

                            // Show claims made by the user (on other people's posts)
                            if (state.claims.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "My Claims (on others' posts)",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(state.claims) { claim ->
                                    ClaimCard(claim = claim)
                                }
                            }
                        }
                    }
                }
                is MyClaimsState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${state.message}")
                    }
                }
            }
        }
    }
}
