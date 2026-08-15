package dev.diegesis.app.ui.campaign

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.ui.theme.DiegesisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignEditScreen(
    viewModel: CampaignEditViewModel,
    onBack: () -> Unit,
    onCampaignSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Campaign",
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = DiegesisColors.Amber
                    )
                }

                uiState.notFound -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Campaign not found",
                            fontSize = 18.sp,
                            color = DiegesisColors.TextDim
                        )
                        TextButton(onClick = onBack) {
                            Text("Back", color = DiegesisColors.Amber)
                        }
                    }
                }

                else -> {
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

                        // Current Location
                        OutlinedTextField(
                            value = uiState.location,
                            onValueChange = { viewModel.updateLocation(it) },
                            label = { Text("Current Location", color = DiegesisColors.TextDim) },
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

                        // Session plan is editable text but there is no
                        // regeneration in edit mode.
                        Text(
                            text = "Session plan is kept from campaign creation.",
                            fontSize = 12.sp,
                            color = DiegesisColors.TextFaint
                        )

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

                        // Save Changes Button
                        Button(
                            onClick = { viewModel.saveCampaign(onCampaignSaved) },
                            enabled = uiState.title.isNotBlank() && uiState.premise.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DiegesisColors.Amber,
                                contentColor = DiegesisColors.Bg,
                                disabledContainerColor = DiegesisColors.Surface2,
                                disabledContentColor = DiegesisColors.TextFaint
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
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
}
