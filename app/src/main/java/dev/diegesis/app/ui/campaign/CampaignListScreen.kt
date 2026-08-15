package dev.diegesis.app.ui.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.ui.theme.DiegesisColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignListScreen(
    viewModel: CampaignListViewModel,
    onOpenCampaign: (String) -> Unit,
    onCreateCampaign: () -> Unit,
    onEditCampaign: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Diegesis",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙", fontSize = 20.sp, color = DiegesisColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiegesisColors.Surface,
                    titleContentColor = DiegesisColors.Text
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCampaign,
                containerColor = DiegesisColors.Amber,
                contentColor = DiegesisColors.Bg
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.campaigns.isEmpty() && !uiState.isLoading) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.campaigns) { campaign ->
                        CampaignCard(
                            campaign = campaign,
                            onClick = { onOpenCampaign(campaign.id) },
                            onEdit = { onEditCampaign(campaign.id) },
                            onDelete = { viewModel.deleteCampaign(campaign.id) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DiegesisColors.Amber
                )
            }

            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
fun CampaignCard(
    campaign: Campaign,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DiegesisColors.Surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = campaign.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiegesisColors.Text,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("✎", fontSize = 16.sp, color = DiegesisColors.TextDim)
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🗑", fontSize = 16.sp)
                }
            }

            Text(
                text = campaign.premise.take(120) + if (campaign.premise.length > 120) "..." else "",
                fontSize = 14.sp,
                color = DiegesisColors.TextDim,
                lineHeight = 20.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (campaign.sceneState.location.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(DiegesisColors.Surface2, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = campaign.sceneState.location,
                            fontSize = 12.sp,
                            color = DiegesisColors.Cyan
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(campaign.updatedAt),
                    fontSize = 12.sp,
                    color = DiegesisColors.TextFaint
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Campaign") },
            text = { Text("Delete \"${campaign.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DiegesisColors.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No campaigns yet",
                fontSize = 18.sp,
                color = DiegesisColors.TextDim
            )
            Text(
                text = "Tap + to create your first story",
                fontSize = 14.sp,
                color = DiegesisColors.TextFaint
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayInMs = 24 * 60 * 60 * 1000L

    return when {
        diff < dayInMs -> "Today"
        diff < 2 * dayInMs -> "Yesterday"
        diff < 7 * dayInMs -> "${diff / dayInMs} days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
