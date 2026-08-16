package dev.diegesis.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Turn(
    val index: Int,
    val playerInput: String,
    val variants: List<TurnVariant> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class TurnVariant(
    val id: String,
    val synopsis: String,
    val sceneOutput: String,
    val routerDecision: RouterDecision? = null,
    val presentNpcIds: List<String> = emptyList(),
    val mechanicResults: List<MechanicResult> = emptyList(),
    val interrupted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Terse one-line pipeline events recorded by the orchestrator, e.g.
     * "plot: fallback used (json parse failed)". Default keeps pre-phase-6
     * turn files loadable.
     */
    val stageEvents: List<String> = emptyList(),
    /**
     * Model reasoning/thinking streamed during the scene stage (OpenAI-compat
     * `delta.reasoning`/`delta.reasoning_content`, Anthropic thinking blocks).
     * Null when the model emitted none. Default keeps old turn files loadable.
     */
    val reasoning: String? = null
)
