package dev.diegesis.app.engine.stages

import dev.diegesis.app.engine.ai.AiCaller
import dev.diegesis.app.engine.assembler.VisibilityContextAssembler
import kotlinx.coroutines.flow.Flow

/**
 * Scene stage: streams the narrative prose using the write model.
 * This is the only stage whose output reaches the story screen.
 */
class SceneStage(private val aiCaller: AiCaller) {
    
    companion object {
        const val DEFAULT_NARRATOR_VOICE = """
You are the narrator of a tabletop campaign. Write in second person, present tense.
Literary but direct. Dialog in quotes. Never decide the player's actions or thoughts.

Render the beat described in the synopsis. Honor mechanic outcomes exactly.
Voice each present NPC according to their sheet and voice examples.
Output markdown prose only — no headers, no meta commentary.
        """
    }
    
    /**
     * Stream the scene prose.
     * 
     * @param context Visibility-assembled scene context
     * @param narratorVoice Campaign-configurable narrator instructions
     * @param onReasoningChunk Live tap for model reasoning deltas; null = ignore
     * @return Flow of prose chunks
     */
    suspend fun execute(
        context: VisibilityContextAssembler.SceneContext,
        narratorVoice: String = DEFAULT_NARRATOR_VOICE,
        onReasoningChunk: ((String) -> Unit)? = null
    ): Flow<String> {
        val userPrompt = VisibilityContextAssembler.formatPrompt(context)
        
        return aiCaller.streamProse(
            systemPrompt = narratorVoice.trimIndent(),
            userPrompt = userPrompt,
            onReasoningChunk = onReasoningChunk
        )
    }
}
