package dev.diegesis.app.engine.stages

import dev.diegesis.app.data.model.MechanicResult
import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.model.PlotOutput
import dev.diegesis.app.data.model.RouterDecision
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.serialization.json.Json

/**
 * Plot stage: decides what happens in this beat.
 */
class PlotStage(private val aiCaller: AiCaller) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun execute(
        sessionPlan: String,
        recentSummary: String,
        playerInput: String,
        routerDecision: RouterDecision?,
        mechanicResults: List<MechanicResult>,
        retrievedMemories: List<MemoryEntry>
    ): PlotOutput {
        val systemPrompt = """
You are the plot engine of a tabletop campaign. You decide WHAT happens, never how it is told.

Session plan (the arc to follow):
$sessionPlan

Story so far (compressed):
${recentSummary.ifBlank { "Campaign just started." }}

Rules:
- Advance the arc. Do not stall, do not repeat beats.
- End every beat ON MAXIMUM CONFLICT. Whatever the situation, add pressure. Slice of life: add friction. Conversation: escalate.
- If mechanic results are provided, the synopsis MUST honor their tiers exactly.
- Nominate which NPCs are physically present. NPCs not listed leave the scene.
- Reply with JSON only.
        """.trimIndent()
        
        val userPayload = buildString {
            append("Player action: $playerInput\n\n")
            
            if (mechanicResults.isNotEmpty()) {
                append("## Mechanic Results (MUST honor these):\n")
                mechanicResults.forEach { result ->
                    append("- ${result.skill}: ${result.tier} (DC ${result.dc}, rolled ${result.value})\n")
                }
                append("\n")
            }
            
            if (retrievedMemories.isNotEmpty()) {
                append("## Recalled Facts:\n")
                retrievedMemories.forEach { mem ->
                    append("- ${mem.fact}\n")
                }
                append("\n")
            }
            
            append("Reply with JSON:\n")
            append("""
{
  "synopsis": "2-6 sentences, what happens in this beat",
  "present_npcs": ["npcId"],
  "scene_change": false,
  "location": null,
  "tracker_updates": [{"npc": "npcId", "key": "trust", "delta": -1}]
}
            """.trimIndent())
        }
        
        val fallback = PlotOutput(
            synopsis = "The moment stretches; the situation stays tense.",
            present_npcs = emptyList(),
            scene_change = false,
            location = null,
            tracker_updates = emptyList()
        )
        
        return aiCaller.generateStructured(
            systemPrompt = systemPrompt,
            userPrompt = userPayload,
            decoder = { responseText ->
                json.decodeFromString<PlotOutput>(responseText)
            },
            fallback = fallback
        )
    }
}
