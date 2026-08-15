package dev.diegesis.app.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.ui.theme.DiegesisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageDetailsBottomSheet(
    variant: TurnVariant,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DiegesisColors.Surface2,
        contentColor = DiegesisColors.Text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Stage Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = DiegesisColors.Text
            )

            // Synopsis
            DetailSection(
                title = "Plot Synopsis",
                content = variant.synopsis
            )

            // Router decision
            if (variant.routerDecision != null) {
                DetailSection(
                    title = "Router Decision",
                    content = buildString {
                        appendLine("Needs check: ${variant.routerDecision.needs_check}")
                        if (variant.routerDecision.checks.isNotEmpty()) {
                            appendLine("\nChecks requested:")
                            variant.routerDecision.checks.forEach { check ->
                                appendLine("  • ${check.skill} (DC ${check.dc}, modifier ${check.modifier}, advantage ${check.advantage})")
                            }
                        }
                        appendLine("\nRun agency update: ${variant.routerDecision.run_agency_update}")
                        if (!variant.routerDecision.lore_query.isNullOrBlank()) {
                            appendLine("\nLore query: ${variant.routerDecision.lore_query}")
                        }
                    }
                )
            }

            // Mechanic results
            if (variant.mechanicResults.isNotEmpty()) {
                DetailSection(
                    title = "Mechanic Results",
                    content = buildString {
                        variant.mechanicResults.forEach { result ->
                            appendLine("${result.skill}:")
                            appendLine("  Tier: ${result.tier}")
                            appendLine("  Value: ${result.value} (vs DC ${result.dc})")
                            appendLine("  Cards drawn:")
                            result.drawn.forEach { card ->
                                appendLine("    ${card.name} (${card.rank} of ${card.suit})")
                            }
                            appendLine()
                        }
                    }
                )
            }

            // Present NPCs
            if (variant.presentNpcIds.isNotEmpty()) {
                DetailSection(
                    title = "Present NPCs",
                    content = variant.presentNpcIds.joinToString(", ")
                )
            }

            // Pipeline events — transparency into fallbacks and failures.
            DetailSection(
                title = "Pipeline Events",
                content = if (variant.stageEvents.isEmpty()) {
                    "All stages completed cleanly."
                } else {
                    variant.stageEvents.joinToString("\n")
                }
            )

            // Interrupted flag
            if (variant.interrupted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiegesisColors.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠ This variant was interrupted during generation",
                        color = DiegesisColors.Red,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiegesisColors.Amber
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DiegesisColors.Surface, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = DiegesisColors.Text,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
