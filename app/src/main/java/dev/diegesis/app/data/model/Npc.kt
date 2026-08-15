package dev.diegesis.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Npc(
    val id: String,
    val name: String,
    val description: String,
    val personality: String,
    val voiceExamples: List<String> = emptyList(),
    val agency: NpcAgency = NpcAgency(),
    val trackers: Map<String, Int> = emptyMap(),
    val sourceCard: String? = null
)

@Serializable
data class NpcAgency(
    val goal: String = "",
    val stance: String = "",
    val willActOn: String = ""
)
