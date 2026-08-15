package dev.diegesis.app.ui.settings

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
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // BYOK Section
                Text(
                    text = "API Configuration",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiegesisColors.Text
                )

                OutlinedTextField(
                    value = uiState.openaiBaseUrl,
                    onValueChange = { viewModel.updateOpenaiBaseUrl(it) },
                    label = { Text("OpenAI Base URL", color = DiegesisColors.TextDim) },
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

                OutlinedTextField(
                    value = uiState.openaiApiKey,
                    onValueChange = { viewModel.updateOpenaiApiKey(it) },
                    label = { Text("OpenAI API Key", color = DiegesisColors.TextDim) },
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

                OutlinedTextField(
                    value = uiState.anthropicApiKey,
                    onValueChange = { viewModel.updateAnthropicApiKey(it) },
                    label = { Text("Anthropic API Key", color = DiegesisColors.TextDim) },
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

                Divider(color = DiegesisColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // Think Model Section
                Text(
                    text = "Think Model (Router, Plot, Agency, Extraction)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiegesisColors.Text
                )

                ProviderSelector(
                    label = "Think Provider",
                    selectedProvider = uiState.thinkProvider,
                    onProviderChange = { viewModel.updateThinkProvider(it) }
                )

                OutlinedTextField(
                    value = uiState.thinkModel,
                    onValueChange = { viewModel.updateThinkModel(it) },
                    label = { Text("Think Model ID", color = DiegesisColors.TextDim) },
                    placeholder = { Text("e.g., gpt-4o-mini", color = DiegesisColors.TextFaint) },
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

                Divider(color = DiegesisColors.Border, modifier = Modifier.padding(vertical = 8.dp))

                // Write Model Section
                Text(
                    text = "Write Model (Scene Prose)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DiegesisColors.Text
                )

                ProviderSelector(
                    label = "Write Provider",
                    selectedProvider = uiState.writeProvider,
                    onProviderChange = { viewModel.updateWriteProvider(it) }
                )

                OutlinedTextField(
                    value = uiState.writeModel,
                    onValueChange = { viewModel.updateWriteModel(it) },
                    label = { Text("Write Model ID", color = DiegesisColors.TextDim) },
                    placeholder = { Text("e.g., claude-3-5-sonnet-20241022", color = DiegesisColors.TextFaint) },
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

                // Save Button
                Button(
                    onClick = { viewModel.saveSettings() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiegesisColors.Amber,
                        contentColor = DiegesisColors.Bg
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Success/Error messages
            uiState.successMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = DiegesisColors.Green,
                    contentColor = DiegesisColors.Bg,
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
                            Text("Dismiss", color = DiegesisColors.Bg)
                        }
                    }
                ) {
                    Text(message)
                }
            }

            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
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
fun ProviderSelector(
    label: String,
    selectedProvider: String,
    onProviderChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = DiegesisColors.TextDim
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedProvider == "openai-compat",
                onClick = { onProviderChange("openai-compat") },
                label = { Text("OpenAI Compatible") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DiegesisColors.Amber,
                    selectedLabelColor = DiegesisColors.Bg,
                    containerColor = DiegesisColors.Surface2,
                    labelColor = DiegesisColors.Text
                )
            )
            FilterChip(
                selected = selectedProvider == "anthropic",
                onClick = { onProviderChange("anthropic") },
                label = { Text("Anthropic") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DiegesisColors.Amber,
                    selectedLabelColor = DiegesisColors.Bg,
                    containerColor = DiegesisColors.Surface2,
                    labelColor = DiegesisColors.Text
                )
            )
        }
    }
}
