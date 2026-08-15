package dev.diegesis.app.engine.stages

import dev.diegesis.app.data.model.RouterDecision
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.serialization.json.Json

/**
 * Router stage: decides if mechanics checks are needed.
 */
class RouterStage(private val aiCaller: AiCaller) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun execute(
        playerInput: String,
        sceneState: SceneState
    ): RouterDecision {
        val systemPrompt = """
You are the router for a tabletop RPG turn. Decide if the player's action requires a mechanics check.

Reply with JSON only:
{
  "needs_check": false,
  "checks": [{"skill": "string", "dc": 5, "modifier": 0, "advantage": 0}],
  "run_agency_update": false,
  "lore_query": null
}

- needs_check: true if any mechanics roll is needed
- checks: array of checks (dc 3-18, advantage -1/0/1 for disadvantage/normal/advantage)
- run_agency_update: true if NPCs should update their goals after this turn
- lore_query: reserved for future memory search
        """.trimIndent()
        
        val userPrompt = """
Scene state:
- Location: ${sceneState.location.ifBlank { "unspecified" }}
- Present NPCs: ${sceneState.presentNpcIds.joinToString(", ").ifBlank { "none" }}

Player action: $playerInput

Does this require a check? Reply with JSON only.
        """.trimIndent()
        
        val fallback = RouterDecision(
            needs_check = false,
            checks = emptyList(),
            run_agency_update = false,
            lore_query = null
        )
        
        return aiCaller.generateStructured(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            decoder = { responseText ->
                json.decodeFromString<RouterDecision>(responseText)
            },
            fallback = fallback
        )
    }
}
