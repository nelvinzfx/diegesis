package dev.diegesis.app.ui.npc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.ui.theme.DiegesisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpcManagementScreen(
    viewModel: NpcViewModel,
    campaignId: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(campaignId) {
        viewModel.loadNpcs(campaignId)
    }

    if (uiState.editingNpc != null) {
        NpcEditSheet(
            npc = uiState.editingNpc!!,
            onUpdate = { viewModel.updateEditingNpc(it) },
            onSave = { viewModel.saveNpc() },
            onCancel = { viewModel.cancelEdit() },
            onImportCard = { viewModel.showImportDialog() }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "NPCs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("←", fontSize = 20.sp, color = DiegesisColors.Text)
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
                    onClick = { viewModel.createNewNpc() },
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
                if (uiState.npcs.isEmpty() && !uiState.isLoading) {
                    EmptyNpcState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.npcs) { npc ->
                            NpcCard(
                                npc = npc,
                                onClick = { viewModel.editNpc(npc) },
                                onDelete = { viewModel.deleteNpc(npc.id) }
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

    // Import dialog
    if (uiState.showImportDialog) {
        CharacterCardImportDialog(
            onImport = { jsonString -> viewModel.importCharacterCard(jsonString) },
            onDismiss = { viewModel.hideImportDialog() }
        )
    }
}

@Composable
fun NpcCard(
    npc: Npc,
    onClick: () -> Unit,
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
                    text = npc.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiegesisColors.Cyan,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🗑", fontSize = 16.sp)
                }
            }

            if (npc.description.isNotEmpty()) {
                Text(
                    text = npc.description.take(100) + if (npc.description.length > 100) "..." else "",
                    fontSize = 14.sp,
                    color = DiegesisColors.TextDim,
                    lineHeight = 20.sp
                )
            }

            if (npc.trackers.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    npc.trackers.entries.take(3).forEach { (key, value) ->
                        Box(
                            modifier = Modifier
                                .background(DiegesisColors.Surface2, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$key: $value",
                                fontSize = 12.sp,
                                color = DiegesisColors.TextDim
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete NPC") },
            text = { Text("Delete \"${npc.name}\"? This cannot be undone.") },
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
fun EmptyNpcState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No NPCs yet",
                fontSize = 18.sp,
                color = DiegesisColors.TextDim
            )
            Text(
                text = "Tap + to create or import",
                fontSize = 14.sp,
                color = DiegesisColors.TextFaint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpcEditSheet(
    npc: Npc,
    onUpdate: (Npc) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onImportCard: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (npc.name.isEmpty()) "New NPC" else npc.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Text("×", fontSize = 24.sp, color = DiegesisColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiegesisColors.Surface,
                    titleContentColor = DiegesisColors.Text
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Import button
            Button(
                onClick = onImportCard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiegesisColors.Surface2,
                    contentColor = DiegesisColors.Text
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Character Card")
            }

            // Name
            OutlinedTextField(
                value = npc.name,
                onValueChange = { onUpdate(npc.copy(name = it)) },
                label = { Text("Name", color = DiegesisColors.TextDim) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = npc.description,
                onValueChange = { onUpdate(npc.copy(description = it)) },
                label = { Text("Description", color = DiegesisColors.TextDim) },
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Personality
            OutlinedTextField(
                value = npc.personality,
                onValueChange = { onUpdate(npc.copy(personality = it)) },
                label = { Text("Personality", color = DiegesisColors.TextDim) },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Voice Examples
            Text(
                text = "Voice Examples",
                fontSize = 14.sp,
                color = DiegesisColors.TextDim,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = npc.voiceExamples.joinToString("\n\n"),
                onValueChange = { text ->
                    val examples = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                    onUpdate(npc.copy(voiceExamples = examples))
                },
                placeholder = { Text("One example per paragraph", color = DiegesisColors.TextFaint) },
                minLines = 4,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Agency
            Text(
                text = "Agency",
                fontSize = 14.sp,
                color = DiegesisColors.TextDim,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = npc.agency.goal,
                onValueChange = { onUpdate(npc.copy(agency = npc.agency.copy(goal = it))) },
                label = { Text("Goal", color = DiegesisColors.TextDim) },
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = npc.agency.stance,
                onValueChange = { onUpdate(npc.copy(agency = npc.agency.copy(stance = it))) },
                label = { Text("Stance", color = DiegesisColors.TextDim) },
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = npc.agency.willActOn,
                onValueChange = { onUpdate(npc.copy(agency = npc.agency.copy(willActOn = it))) },
                label = { Text("Will Act On", color = DiegesisColors.TextDim) },
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Trackers
            Text(
                text = "Trackers",
                fontSize = 14.sp,
                color = DiegesisColors.TextDim,
                fontWeight = FontWeight.Medium
            )

            TrackerEditor(
                trackers = npc.trackers,
                onUpdate = { onUpdate(npc.copy(trackers = it)) }
            )

            // Save button
            Button(
                onClick = onSave,
                enabled = npc.name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiegesisColors.Amber,
                    contentColor = DiegesisColors.Bg,
                    disabledContainerColor = DiegesisColors.Surface2,
                    disabledContentColor = DiegesisColors.TextFaint
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save NPC", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TrackerEditor(
    trackers: Map<String, Int>,
    onUpdate: (Map<String, Int>) -> Unit
) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("0") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        trackers.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key,
                    color = DiegesisColors.Text,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        onUpdate(trackers + (key to value - 1))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("−", fontSize = 20.sp, color = DiegesisColors.Text)
                }

                Text(
                    text = value.toString(),
                    color = DiegesisColors.Text,
                    modifier = Modifier.widthIn(min = 32.dp)
                )

                IconButton(
                    onClick = {
                        onUpdate(trackers + (key to value + 1))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+", fontSize = 20.sp, color = DiegesisColors.Text)
                }

                IconButton(
                    onClick = {
                        onUpdate(trackers - key)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("×", fontSize = 20.sp, color = DiegesisColors.Red)
                }
            }
        }

        // Add new tracker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                placeholder = { Text("Tracker name", color = DiegesisColors.TextFaint) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                placeholder = { Text("0", color = DiegesisColors.TextFaint) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    focusedContainerColor = DiegesisColors.Surface,
                    unfocusedContainerColor = DiegesisColors.Surface,
                    focusedBorderColor = DiegesisColors.TextDim,
                    unfocusedBorderColor = DiegesisColors.Border
                ),
                modifier = Modifier.width(80.dp)
            )

            IconButton(
                onClick = {
                    if (newKey.isNotBlank()) {
                        val value = newValue.toIntOrNull() ?: 0
                        onUpdate(trackers + (newKey to value))
                        newKey = ""
                        newValue = "0"
                    }
                },
                enabled = newKey.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                Text("+", fontSize = 20.sp, color = if (newKey.isNotBlank()) DiegesisColors.Amber else DiegesisColors.TextFaint)
            }
        }
    }
}

@Composable
fun CharacterCardImportDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Character Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste Character Card V2 JSON:",
                    fontSize = 14.sp,
                    color = DiegesisColors.TextDim
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    minLines = 6,
                    maxLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DiegesisColors.Text,
                        unfocusedTextColor = DiegesisColors.Text,
                        focusedContainerColor = DiegesisColors.Surface,
                        unfocusedContainerColor = DiegesisColors.Surface,
                        focusedBorderColor = DiegesisColors.TextDim,
                        unfocusedBorderColor = DiegesisColors.Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (jsonInput.isNotBlank()) {
                        onImport(jsonInput)
                    }
                },
                enabled = jsonInput.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
