package dev.diegesis.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RouterDecision(
    val needs_check: Boolean = false,
    val checks: List<MechanicCheck> = emptyList(),
    val run_agency_update: Boolean = false,
    val lore_query: String? = null
)

@Serializable
data class MechanicCheck(
    val skill: String,
    val dc: Int = 5,
    val modifier: Int = 0,
    val advantage: Int = 0
)

@Serializable
data class DrawnCard(
    val rank: Int,
    val suit: String,
    val name: String = ""
)

@Serializable
data class MechanicResult(
    val skill: String,
    val dc: Int,
    val modifier: Int,
    val drawn: List<DrawnCard>,
    val value: Int,
    val tier: String  // "critical_success", "success", "partial", "failure"
)

@Serializable
data class PlotOutput(
    val synopsis: String,
    val present_npcs: List<String> = emptyList(),
    val scene_change: Boolean = false,
    val location: String? = null,
    val tracker_updates: List<TrackerUpdate> = emptyList()
)

@Serializable
data class TrackerUpdate(
    val npc: String,
    val key: String,
    val delta: Int
)

@Serializable
data class MemoryEntry(
    val scope: String,
    val npc_id: String? = null,
    val fact: String,
    val turn: Int,
    val ts: Long = System.currentTimeMillis()
)
