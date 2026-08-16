package dev.diegesis.app.ui.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.ui.theme.DiegesisColors

@Composable
private fun PlanThinkingBlock(
    reasoning: String,
    isGenerating: Boolean,
    planStarted: Boolean
) {
    // Auto-expanded while the model is thinking so the wait is visible;
    // auto-collapsed once plan text starts flowing. Tappable either way.
    var expanded by remember { mutableStateOf(isGenerating && !planStarted) }

    LaunchedEffect(planStarted) {
        if (isGenerating && planStarted) expanded = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiegesisColors.Surface2, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGenerating && !planStarted) "Thinking…" else "Thinking",
                color = DiegesisColors.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (expanded) "›" else "‹",
                color = DiegesisColors.TextFaint,
                fontSize = 14.sp
            )
        }

        if (expanded) {
            Text(
                text = reasoning,
                color = DiegesisColors.TextFaint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignCreateScreen(
    viewModel: CampaignCreateViewModel,
    onBack: () -> Unit,
    onCampaignCreated: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Campaign",
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
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Title", color = DiegesisColors.TextDim) },
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

                // Premise
                OutlinedTextField(
                    value = uiState.premise,
                    onValueChange = { viewModel.updatePremise(it) },
                    label = { Text("Premise", color = DiegesisColors.TextDim) },
                    minLines = 4,
                    maxLines = 8,
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

                // Initial Location
                OutlinedTextField(
                    value = uiState.initialLocation,
                    onValueChange = { viewModel.updateInitialLocation(it) },
                    label = { Text("Initial Location", color = DiegesisColors.TextDim) },
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

                // Player Persona
                OutlinedTextField(
                    value = uiState.playerPersona,
                    onValueChange = { viewModel.updatePlayerPersona(it) },
                    label = { Text("Player Persona (optional)", color = DiegesisColors.TextDim) },
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

                // Generate Session Plan Button
                Button(
                    onClick = { viewModel.generateSessionPlan() },
                    enabled = !uiState.isGeneratingPlan && uiState.premise.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiegesisColors.Cyan,
                        contentColor = DiegesisColors.Bg,
                        disabledContainerColor = DiegesisColors.Surface2,
                        disabledContentColor = DiegesisColors.TextFaint
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isGeneratingPlan) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DiegesisColors.Bg,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.isGeneratingPlan) "Generating..." else "Generate Session Plan",
                        fontSize = 16.sp
                    )
                }

                // Live model thinking during plan generation (collapsible;
                // shown only while reasoning text exists).
                if (!uiState.planReasoning.isNullOrBlank()) {
                    PlanThinkingBlock(
                        reasoning = uiState.planReasoning!!,
                        isGenerating = uiState.isGeneratingPlan,
                        planStarted = uiState.sessionPlan.isNotBlank()
                    )
                }

                // Session Plan
                OutlinedTextField(
                    value = uiState.sessionPlan,
                    onValueChange = { viewModel.updateSessionPlan(it) },
                    label = { Text("Session Plan (editable)", color = DiegesisColors.TextDim) },
                    minLines = 8,
                    maxLines = 20,
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

                // Create Campaign Button
                Button(
                    onClick = { viewModel.createCampaign(onCampaignCreated) },
                    enabled = uiState.title.isNotBlank() && uiState.premise.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiegesisColors.Amber,
                        contentColor = DiegesisColors.Bg,
                        disabledContainerColor = DiegesisColors.Surface2,
                        disabledContentColor = DiegesisColors.TextFaint
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Campaign", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(32.dp))
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
}
