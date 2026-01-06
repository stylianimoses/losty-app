package com.fyp.losty.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyp.losty.AppViewModel
import com.fyp.losty.MyClaimsState


@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun ManagePostClaimsScreen(appViewModel: AppViewModel = viewModel()) {
    val claimsState by appViewModel.claimsForMyPostsState.collectAsState()
    val context = LocalContext.current

    // Load claims for user's posts
    LaunchedEffect(Unit) {
        appViewModel.loadClaimsForMyPosts()
    }

    val isRefreshing = claimsState is MyClaimsState.Loading
    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = { appViewModel.loadClaimsForMyPosts() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState),
        contentAlignment = Alignment.Center
    ) {
        when (val state = claimsState) {
            is MyClaimsState.Loading -> {
                CircularProgressIndicator()
            }
            is MyClaimsState.Success -> {
                val pendingClaims = state.claims.filter { it.status == "pending" }
                val otherClaims = state.claims.filter { it.status != "pending" }
                
                if (state.claims.isEmpty()) {
                    Text(text = "No claims on your posts yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (pendingClaims.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Pending Claims (${pendingClaims.size})",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(pendingClaims) { claim ->
                                ClaimApprovalCard(
                                    claim = claim,
                                    onApprove = {
                                        appViewModel.approveClaim(claim.id)
                                        Toast.makeText(context, "Claim approved! The item has been marked as claimed.", Toast.LENGTH_SHORT).show()
                                        // Refresh the claims list
                                        appViewModel.loadClaimsForMyPosts()
                                    },
                                    onReject = {
                                        appViewModel.rejectClaim(claim.id)
                                        Toast.makeText(context, "Claim rejected.", Toast.LENGTH_SHORT).show()
                                        // Refresh the claims list
                                        appViewModel.loadClaimsForMyPosts()
                                    }
                                )
                            }
                        }
                        
                        if (otherClaims.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Other Claims",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(otherClaims) { claim ->
                                OwnerClaimCard(claim = claim)
                            }
                        }
                    }
                }
            }
            is MyClaimsState.Error -> {
                Text(text = "Error: ${state.message}")
            }
        }

        PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}
