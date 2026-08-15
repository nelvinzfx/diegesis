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
    val timestamp: Long = System.currentTimeMillis()
)
