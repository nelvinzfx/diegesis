package dev.diegesis.app.engine.assembler

import dev.diegesis.app.data.model.MechanicResult
import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.Turn

/**
 * Assembles the scene context with strict visibility filtering.
 * 
 * THE CORE INVARIANT: A past turn is visible IF AND ONLY IF:
 * - presentNpcIds is empty (solo player scene), OR
 * - at least one currently present NPC was also present in that past turn.
 * 
 * This prevents the scene stage from accessing information NPCs couldn't have witnessed.
 */
object VisibilityContextAssembler {
    
    data class SceneContext(
        val synopsis: String,
        val mechanicOutcomes: List<MechanicResult>,
        val presentNpcs: List<NpcPayload>,
        val filteredHistory: List<HistoryEntry>,
        val retrievedMemories: List<MemoryEntry>,
        val playerInput: String
    )
    
    data class NpcPayload(
        val id: String,
        val name: String,
        val description: String,
        val personality: String,
        val voiceExamples: List<String>,
        val agency: String, // formatted goal + stance
        val trackers: Map<String, Int>
    )
    
    data class HistoryEntry(
        val playerInput: String,
        val sceneOutput: String
    )
    
    /**
     * Assemble the full scene context with visibility filtering.
     * 
     * @param synopsis Fresh synopsis from the plot stage
     * @param mechanicResults Results from mechanics checks (if any)
     * @param presentNpcIds IDs of NPCs present in the current scene
     * @param presentNpcs Full NPC data for present NPCs
     * @param allTurns All previous turns (will be filtered)
     * @param retrievedMemories Memory entries retrieved for this turn
     * @param playerInput Current player input
     */
    fun assemble(
        synopsis: String,
        mechanicResults: List<MechanicResult>,
        presentNpcIds: List<String>,
        presentNpcs: List<Npc>,
        allTurns: List<Turn>,
        retrievedMemories: List<MemoryEntry>,
        playerInput: String
    ): SceneContext {
        // Filter turn history based on NPC presence
        val visibleTurns = filterVisibleTurns(allTurns, presentNpcIds)
        
        // Convert to history entries (player input + scene output pairs)
        val history = visibleTurns.mapNotNull { turn ->
            val variant = turn.variants.lastOrNull() ?: return@mapNotNull null
            HistoryEntry(
                playerInput = turn.playerInput,
                sceneOutput = variant.sceneOutput
            )
        }
        
        // Build NPC payloads
        val npcPayloads = presentNpcs.map { npc ->
            NpcPayload(
                id = npc.id,
                name = npc.name,
                description = npc.description,
                personality = npc.personality,
                voiceExamples = npc.voiceExamples,
                agency = formatAgency(npc),
                trackers = npc.trackers
            )
        }
        
        return SceneContext(
            synopsis = synopsis,
            mechanicOutcomes = mechanicResults,
            presentNpcs = npcPayloads,
            filteredHistory = history,
            retrievedMemories = retrievedMemories,
            playerInput = playerInput
        )
    }
    
    /**
     * Filter turns to only those visible to the currently present NPCs.
     * 
     * A turn is visible if:
     * - presentNpcIds is empty (solo player scene), OR
     * - at least one currently present NPC was also present in that past turn
     */
    private fun filterVisibleTurns(allTurns: List<Turn>, presentNpcIds: List<String>): List<Turn> {
        // Solo player scene: all turns are visible
        if (presentNpcIds.isEmpty()) {
            return allTurns
        }
        
        // Filter based on NPC presence overlap
        return allTurns.filter { turn ->
            val variant = turn.variants.lastOrNull() ?: return@filter false
            val pastPresentNpcs = variant.presentNpcIds
            
            // At least one currently present NPC was present in this past turn
            pastPresentNpcs.any { it in presentNpcIds }
        }
    }
    
    /**
     * Format NPC agency as a readable string for the prompt.
     */
    private fun formatAgency(npc: Npc): String {
        val agency = npc.agency
        val parts = mutableListOf<String>()
        
        if (agency.goal.isNotBlank()) {
            parts.add("Goal: ${agency.goal}")
        }
        if (agency.stance.isNotBlank()) {
            parts.add("Stance: ${agency.stance}")
        }
        if (agency.willActOn.isNotBlank()) {
            parts.add("Will act on: ${agency.willActOn}")
        }
        
        return if (parts.isEmpty()) {
            "No current agency state."
        } else {
            parts.joinToString(" | ")
        }
    }
    
    /**
     * Format the scene context into a prompt string.
     * Order: synopsis, mechanics, NPCs, history, memories, player input.
     */
    fun formatPrompt(context: SceneContext): String {
        val sections = mutableListOf<String>()
        
        // 1. Synopsis
        sections.add("## Synopsis\n${context.synopsis}")
        
        // 2. Mechanic outcomes
        if (context.mechanicOutcomes.isNotEmpty()) {
            val mechanicsText = context.mechanicOutcomes.joinToString("\n") { result ->
                "- ${result.skill} (DC ${result.dc}): ${result.tier.replace("_", " ")} " +
                "(drew ${result.drawn.joinToString(", ") { it.name }}, total ${result.value})"
            }
            sections.add("## Mechanic Outcomes\n$mechanicsText\n\nNarrate these outcomes accordingly.")
        }
        
        // 3. Present NPCs
        if (context.presentNpcs.isNotEmpty()) {
            val npcsText = context.presentNpcs.joinToString("\n\n") { npc ->
                buildString {
                    append("### ${npc.name}\n")
                    append("${npc.description}\n\n")
                    append("**Personality:** ${npc.personality}\n\n")
                    if (npc.voiceExamples.isNotEmpty()) {
                        append("**Voice examples:**\n")
                        npc.voiceExamples.forEach { append("- \"$it\"\n") }
                        append("\n")
                    }
                    append("**Agency:** ${npc.agency}\n\n")
                    if (npc.trackers.isNotEmpty()) {
                        append("**Trackers:** ${npc.trackers.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
                    }
                }
            }
            sections.add("## Present NPCs\n$npcsText")
        }
        
        // 4. Filtered history
        if (context.filteredHistory.isNotEmpty()) {
            val historyText = context.filteredHistory.joinToString("\n\n") { entry ->
                "**Player:** ${entry.playerInput}\n\n${entry.sceneOutput}"
            }
            sections.add("## Previous Events\n$historyText")
        }
        
        // 5. Retrieved memories
        if (context.retrievedMemories.isNotEmpty()) {
            val memoriesText = context.retrievedMemories.joinToString("\n") { mem ->
                "- ${mem.fact}"
            }
            sections.add("## Recalled Facts\n$memoriesText")
        }
        
        // 6. Player input
        sections.add("## Player Action\n${context.playerInput}")
        
        return sections.joinToString("\n\n")
    }
}
