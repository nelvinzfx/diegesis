package dev.diegesis.app.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.ui.theme.DiegesisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    viewModel: MemoriesViewModel,
    campaignId: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(campaignId) {
        viewModel.loadMemories(campaignId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Memories",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp, color = DiegesisColors.Text)
                    }
                },
                actions = {
                    if (uiState.memories.isNotEmpty()) {
                        TextButton(onClick = { viewModel.requestClearAll() }) {
                            Text(
                                text = "Clear all",
                                color = DiegesisColors.Red,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiegesisColors.Surface,
                    titleContentColor = DiegesisColors.Text
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.memories.isEmpty() && !uiState.isLoading) {
                EmptyMemoriesState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.memories) { entry ->
                        MemoryCard(
                            entry = entry,
                            npcName = viewModel.npcNameFor(entry),
                            onDelete = { viewModel.deleteMemory(entry) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DiegesisColors.TextDim
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

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelClearAll() },
            containerColor = DiegesisColors.Surface2,
            title = {
                Text(
                    text = "Clear all memories?",
                    color = DiegesisColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Every extracted fact for this campaign will be deleted. This cannot be undone.",
                    color = DiegesisColors.TextDim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClearAll() }) {
                    Text("Clear all", color = DiegesisColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelClearAll() }) {
                    Text("Cancel", color = DiegesisColors.TextDim)
                }
            }
        )
    }
}

@Composable
private fun MemoryCard(
    entry: MemoryEntry,
    npcName: String?,
    onDelete: () -> Unit
) {
    var confirmingDelete by remember(entry) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiegesisColors.Surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScopeBadge(entry = entry, npcName = npcName)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "turn ${entry.turn}",
                fontSize = 12.sp,
                color = DiegesisColors.TextFaint,
                fontFamily = FontFamily.Monospace
            )
        }

        Text(
            text = entry.fact,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = DiegesisColors.Text
        )

        if (confirmingDelete) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delete this memory?",
                    fontSize = 13.sp,
                    color = DiegesisColors.TextDim
                )
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel", color = DiegesisColors.TextDim, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = DiegesisColors.Red, fontSize = 13.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text("Delete", color = DiegesisColors.TextDim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ScopeBadge(entry: MemoryEntry, npcName: String?) {
    val isNpc = entry.scope == "npc"
    val label = if (isNpc) (npcName ?: entry.npc_id ?: "npc") else "campaign"
    val color = if (isNpc) DiegesisColors.Cyan else DiegesisColors.TextDim

    Box(
        modifier = Modifier
            .background(DiegesisColors.Surface2, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyMemoriesState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No memories yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = DiegesisColors.TextDim
            )
            Text(
                text = "Durable facts extracted after each turn will collect here.",
                fontSize = 14.sp,
                color = DiegesisColors.TextFaint
            )
        }
    }
}
