package dev.diegesis.app.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.ui.markdown.MarkdownText
import dev.diegesis.app.ui.theme.DiegesisColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    viewModel: StoryViewModel,
    onBack: () -> Unit,
    onOpenNpcs: () -> Unit,
    onOpenMemories: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new turns appear
    LaunchedEffect(uiState.turns.size, uiState.streamingText) {
        if (uiState.turns.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    Scaffold(
        topBar = {
            StoryTopBar(
                campaign = uiState.campaign,
                onBack = onBack,
                onOpenNpcs = onOpenNpcs,
                onOpenMemories = onOpenMemories
            )
        },
        bottomBar = {
            StoryInputBar(
                isStreaming = uiState.isStreaming,
                onSend = { input -> viewModel.sendPlayerInput(input) },
                onStop = { viewModel.stopGeneration() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.turns) { turn ->
                    TurnItem(
                        turn = turn,
                        selectedVariantIndex = uiState.selectedVariantIndices[turn.index] ?: 0,
                        onSwitchVariant = { variantIndex ->
                            viewModel.switchVariant(turn.index, variantIndex)
                        },
                        onRegenerate = { viewModel.regenerateTurn(turn.index) },
                        onEditAndResend = { newInput ->
                            viewModel.editAndResend(turn.index, newInput)
                        },
                        onDelete = { viewModel.deleteTurn(turn.index) },
                        onViewStageDetails = { viewModel.showStageDetails(turn.index) }
                    )
                }

                // Just-sent player input, echoed instantly while the pipeline runs.
                uiState.pendingPlayerInput?.let { pending ->
                    item { PlayerTurnItem(pending) }
                }

                // Assistant placeholder: appears empty the moment generation
                // starts, then fills token by token from the SSE stream.
                if (uiState.isStreaming) {
                    item {
                        AssistantTurnItem(
                            variant = TurnVariant(
                                id = "streaming",
                                synopsis = "",
                                sceneOutput = uiState.streamingText,
                                interrupted = false
                            ),
                            isStreaming = true
                        )
                    }
                }
            }

            // Error snackbar
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

    // Stage details bottom sheet
    if (uiState.activeStageDetailsTurn != null) {
        val turn = uiState.turns.find { it.index == uiState.activeStageDetailsTurn }
        val variantIndex = uiState.selectedVariantIndices[uiState.activeStageDetailsTurn!!] ?: 0
        val variant = turn?.variants?.getOrNull(variantIndex)
        
        if (variant != null) {
            StageDetailsBottomSheet(
                variant = variant,
                onDismiss = { viewModel.showStageDetails(null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryTopBar(
    campaign: dev.diegesis.app.data.model.Campaign?,
    onBack: () -> Unit,
    onOpenNpcs: () -> Unit,
    onOpenMemories: () -> Unit
) {
    TopAppBar(
        title = { 
            Column {
                Text(
                    text = campaign?.title ?: "Story",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (campaign?.sceneState?.location?.isNotEmpty() == true) {
                    Text(
                        text = campaign.sceneState.location,
                        fontSize = 12.sp,
                        color = DiegesisColors.TextDim
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 20.sp, color = DiegesisColors.Text)
            }
        },
        actions = {
            TextButton(onClick = onOpenNpcs) {
                Text(
                    text = "NPCs",
                    color = DiegesisColors.Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = onOpenMemories) {
                Text(
                    text = "Memories",
                    color = DiegesisColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DiegesisColors.Surface,
            titleContentColor = DiegesisColors.Text
        )
    )
}

@Composable
fun TurnItem(
    turn: Turn,
    selectedVariantIndex: Int,
    onSwitchVariant: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onEditAndResend: (String) -> Unit,
    onDelete: () -> Unit,
    onViewStageDetails: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Player input
        PlayerTurnItem(turn.playerInput)

        // Assistant response
        if (turn.variants.isNotEmpty()) {
            val variant = turn.variants.getOrNull(selectedVariantIndex) ?: turn.variants.first()
            AssistantTurnItem(
                variant = variant,
                isStreaming = false,
                variantIndex = selectedVariantIndex,
                totalVariants = turn.variants.size,
                onSwitchVariant = onSwitchVariant,
                onRegenerate = onRegenerate,
                onEditAndResend = { onEditAndResend(turn.playerInput) },
                onDelete = onDelete,
                onViewStageDetails = onViewStageDetails
            )
        }
    }
}

@Composable
fun PlayerTurnItem(input: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        SelectionContainer {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(DiegesisColors.Surface2, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = input,
                    color = DiegesisColors.Text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
fun AssistantTurnItem(
    variant: TurnVariant,
    isStreaming: Boolean,
    variantIndex: Int = 0,
    totalVariants: Int = 1,
    onSwitchVariant: (Int) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onEditAndResend: () -> Unit = {},
    onDelete: () -> Unit = {},
    onViewStageDetails: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiegesisColors.Surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .then(
                if (!isStreaming) {
                    Modifier.clickable { showContextMenu = true }
                } else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mechanics badge
        if (variant.mechanicResults.isNotEmpty()) {
            variant.mechanicResults.forEach { result ->
                MechanicsBadge(result)
            }
        }

        // Prose content (empty placeholder while awaiting first chunk)
        if (isStreaming && variant.sceneOutput.isBlank()) {
            Text(
                text = "…",
                color = DiegesisColors.TextFaint,
                fontSize = 17.sp
            )
        } else {
            MarkdownText(
                markdown = variant.sceneOutput,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Variant pager
        if (totalVariants > 1) {
            VariantPager(
                currentIndex = variantIndex,
                totalVariants = totalVariants,
                onSwitchVariant = onSwitchVariant
            )
        }
    }

    // Context menu
    if (showContextMenu) {
        TurnContextMenu(
            onDismiss = { showContextMenu = false },
            onCopy = {
                showContextMenu = false
                clipboard.setText(AnnotatedString(variant.sceneOutput))
            },
            onRegenerate = {
                showContextMenu = false
                onRegenerate()
            },
            onEditAndResend = {
                showContextMenu = false
                onEditAndResend()
            },
            onDelete = {
                showContextMenu = false
                showDeleteConfirm = true
            },
            onViewStageDetails = {
                showContextMenu = false
                onViewStageDetails()
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Turn") },
            text = { Text("This will delete this turn and all subsequent turns. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DiegesisColors.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MechanicsBadge(result: dev.diegesis.app.data.model.MechanicResult) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .background(DiegesisColors.Amber.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✦",
                color = DiegesisColors.Amber,
                fontSize = 12.sp
            )
            Text(
                text = "${result.tier} · ${result.skill} (${result.value} vs ${result.dc})",
                color = DiegesisColors.Amber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .background(DiegesisColors.Surface2, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Cards drawn:",
                    fontSize = 11.sp,
                    color = DiegesisColors.TextDim
                )
                result.drawn.forEach { card ->
                    Text(
                        text = "${card.name} (${card.rank} of ${card.suit})",
                        fontSize = 11.sp,
                        color = DiegesisColors.Text,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Value: ${result.value} (${result.drawn.maxOfOrNull { it.rank } ?: 0} + ${result.modifier} modifier)",
                    fontSize = 11.sp,
                    color = DiegesisColors.TextDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun VariantPager(
    currentIndex: Int,
    totalVariants: Int,
    onSwitchVariant: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (currentIndex > 0) onSwitchVariant(currentIndex - 1) },
            enabled = currentIndex > 0
        ) {
            Text(
                text = "‹",
                fontSize = 20.sp,
                color = if (currentIndex > 0) DiegesisColors.Text else DiegesisColors.TextFaint
            )
        }

        Text(
            text = "${currentIndex + 1}/$totalVariants",
            color = DiegesisColors.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        IconButton(
            onClick = { if (currentIndex < totalVariants - 1) onSwitchVariant(currentIndex + 1) },
            enabled = currentIndex < totalVariants - 1
        ) {
            Text(
                text = "›",
                fontSize = 20.sp,
                color = if (currentIndex < totalVariants - 1) DiegesisColors.Text else DiegesisColors.TextFaint
            )
        }
    }
}

@Composable
fun TurnContextMenu(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEditAndResend: () -> Unit,
    onDelete: () -> Unit,
    onViewStageDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn Actions") },
        text = {
            Column {
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Text", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Regenerate", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onEditAndResend,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit & Resend", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onViewStageDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Stage Details", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = DiegesisColors.Red)
                ) {
                    Text("Delete Turn", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StoryInputBar(
    isStreaming: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        color = DiegesisColors.Surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you do?", color = DiegesisColors.TextFaint) },
                minLines = 1,
                maxLines = 4,
                enabled = !isStreaming,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DiegesisColors.Text,
                    unfocusedTextColor = DiegesisColors.Text,
                    disabledTextColor = DiegesisColors.TextDim,
                    focusedBorderColor = DiegesisColors.Border,
                    unfocusedBorderColor = DiegesisColors.Border
                )
            )

            if (isStreaming) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .background(DiegesisColors.Red, RoundedCornerShape(8.dp))
                        .size(48.dp)
                ) {
                    Text("■", fontSize = 16.sp, color = DiegesisColors.Text)
                }
            } else {
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSend(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .background(
                            if (inputText.isNotBlank()) DiegesisColors.Amber else DiegesisColors.Surface2,
                            RoundedCornerShape(8.dp)
                        )
                        .size(48.dp)
                ) {
                    Text(
                        "▲",
                        fontSize = 14.sp,
                        color = if (inputText.isNotBlank()) DiegesisColors.Bg else DiegesisColors.TextFaint
                    )
                }
            }
        }
    }
}
