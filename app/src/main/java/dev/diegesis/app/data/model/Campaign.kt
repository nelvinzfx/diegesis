package dev.diegesis.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Campaign(
    val id: String,
    val title: String,
    val premise: String,
    val sessionPlan: String,
    val playerPersona: String = "",
    val sceneState: SceneState = SceneState(),
    val thinkModel: StageModelSelection? = null,
    val writeModel: StageModelSelection? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SceneState(
    val location: String = "",
    val presentNpcIds: List<String> = emptyList()
)
