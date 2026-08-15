package dev.diegesis.app.engine.stages

import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Agency stage: updates NPC goals and stances based on what they witnessed.
 */
class AgencyStage(private val aiCaller: AiCaller) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Serializable
    private data class AgencyUpdate(
        val goal: String,
        val stance: String,
        val will_act_on: String
    )
    
    /**
     * Update agency for a single NPC based on their witnessed turns.
     * 
     * @param npc The NPC to update
     * @param witnessedTurns Turns where this NPC was present
     * @return Updated NpcAgency, or unchanged if generation fails
     */
    suspend fun updateNpcAgency(
        npc: Npc,
        witnessedTurns: List<Turn>
    ): NpcAgency {
        // Build context of what this NPC witnessed
        val witnessedContext = buildWitnessedContext(witnessedTurns)
        
        val systemPrompt = """
You maintain the inner life of an NPC. Given what THIS NPC has witnessed (below) and their current goal, produce their updated immediate goal and emotional stance.

Reply with JSON only:
{
  "goal": "immediate goal, 1-2 sentences",
  "stance": "emotional stance toward the player, 1 sentence",
  "will_act_on": "what they plan to do next, 1 sentence"
}
        """.trimIndent()
        
        val userPrompt = """
## NPC: ${npc.name}
${npc.description}

**Personality:** ${npc.personality}

**Current agency:**
- Goal: ${npc.agency.goal.ifBlank { "none set" }}
- Stance: ${npc.agency.stance.ifBlank { "neutral" }}
- Will act on: ${npc.agency.willActOn.ifBlank { "nothing planned" }}

## What ${npc.name} witnessed:
$witnessedContext

Based on these events, update ${npc.name}'s agency. Reply with JSON only.
        """.trimIndent()
        
        val fallback = npc.agency // Keep current agency if update fails
        
        val update = aiCaller.generateStructured(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            decoder = { responseText ->
                json.decodeFromString<AgencyUpdate>(responseText)
            },
            fallback = AgencyUpdate(
                goal = fallback.goal,
                stance = fallback.stance,
                will_act_on = fallback.willActOn
            )
        )
        
        return NpcAgency(
            goal = update.goal,
            stance = update.stance,
            willActOn = update.will_act_on
        )
    }
    
    /**
     * Build a summary of witnessed turns for the prompt.
     */
    private fun buildWitnessedContext(witnessedTurns: List<Turn>): String {
        if (witnessedTurns.isEmpty()) {
            return "Nothing yet."
        }
        
        return witnessedTurns.joinToString("\n\n") { turn ->
            val variant = turn.variants.lastOrNull()
            if (variant != null) {
                "**Player:** ${turn.playerInput}\n${variant.synopsis}"
            } else {
                "**Player:** ${turn.playerInput}"
            }
        }
    }
}
