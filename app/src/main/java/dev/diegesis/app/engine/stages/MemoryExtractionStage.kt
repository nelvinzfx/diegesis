package dev.diegesis.app.engine.stages

import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Memory extraction stage: pulls durable facts from a finished turn.
 */
class MemoryExtractionStage(private val aiCaller: AiCaller) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Serializable
    private data class ExtractedFact(
        val scope: String,
        val npc_id: String? = null,
        val fact: String
    )
    
    /**
     * Extract durable memories from the finished turn.
     * 
     * @param playerInput What the player did
     * @param synopsis What the plot stage decided happened
     * @param sceneOutput The narrated prose
     * @param turnIndex Index of this turn (for the memory reference)
     * @return List of memory entries, empty on failure
     */
    suspend fun execute(
        playerInput: String,
        synopsis: String,
        sceneOutput: String,
        turnIndex: Int
    ): List<MemoryEntry> {
        val systemPrompt = """
Extract durable facts from this turn worth remembering across sessions: revelations, decisions, relationships changes, promises, names, places. Ignore transient detail.

Reply with a JSON array only:
[{"scope": "campaign", "npc_id": null, "fact": "..."}]

- scope: "campaign" for world facts, "npc" for facts about a specific NPC
- npc_id: the NPC's id when scope is "npc", otherwise null
- fact: one durable fact, stated plainly
- Return an empty array [] if nothing durable happened.
        """.trimIndent()
        
        val userPrompt = """
## Player action
$playerInput

## What happened
$synopsis

## How it was narrated
$sceneOutput

Extract durable facts. JSON array only.
        """.trimIndent()
        
        val extracted = aiCaller.generateStructured(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            decoder = { responseText ->
                json.decodeFromString<List<ExtractedFact>>(responseText)
            },
            fallback = emptyList()
        )
        
        return extracted.map { fact ->
            MemoryEntry(
                scope = fact.scope,
                npc_id = fact.npc_id,
                fact = fact.fact,
                turn = turnIndex
            )
        }
    }
}
