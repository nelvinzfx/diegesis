package dev.diegesis.app.engine

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.MechanicResult
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.ai.AiCaller
import dev.diegesis.app.engine.assembler.VisibilityContextAssembler
import dev.diegesis.app.engine.mechanics.DeckMechanics
import dev.diegesis.app.engine.memory.MemoryRetriever
import dev.diegesis.app.engine.stages.AgencyStage
import dev.diegesis.app.engine.stages.MemoryExtractionStage
import dev.diegesis.app.engine.stages.PlotStage
import dev.diegesis.app.engine.stages.RouterStage
import dev.diegesis.app.engine.stages.SceneStage
import java.util.UUID
import kotlin.random.Random

/**
 * Orchestrates the full turn execution pipeline.
 * 
 * Resilience contract: no stage failure crashes the turn. Every structured
 * stage has fallback handling; the scene stage failing yields an interrupted
 * variant rather than an exception.
 */
class PipelineOrchestrator(
    private val aiCaller: AiCaller,
    private val campaignStorage: CampaignStorage,
    private val npcStorage: NpcStorage,
    private val turnStorage: TurnStorage,
    private val memoryStorage: MemoryStorage,
    private val random: Random = Random.Default
) {
    private val routerStage = RouterStage(aiCaller)
    private val plotStage = PlotStage(aiCaller)
    private val agencyStage = AgencyStage(aiCaller)
    private val sceneStage = SceneStage(aiCaller)
    private val memoryExtractionStage = MemoryExtractionStage(aiCaller)

    /**
     * Execute a full turn.
     *
     * @param campaignId Campaign to run the turn in
     * @param playerInput What the player typed
     * @param onChunk Called with each streamed prose chunk
     * @return The completed TurnVariant
     */
    suspend fun executeTurn(
        campaignId: String,
        playerInput: String,
        onChunk: (String) -> Unit
    ): TurnVariant {
        val campaign = campaignStorage.load(campaignId)
            ?: error("Campaign $campaignId not found")

        val existingIndices = turnStorage.listTurnIndices(campaignId)
        val turnIndex = (existingIndices.maxOrNull() ?: -1) + 1
        val allTurns = existingIndices.mapNotNull { turnStorage.loadTurn(campaignId, it) }

        // ---- 1. Retrieve memories -------------------------------------------
        val allMemories = runCatching { memoryStorage.loadMemories(campaignId) }
            .getOrDefault(emptyList())
        val preRetrieval = MemoryRetriever.retrieve(playerInput, allMemories)

        // ---- 2. Router ------------------------------------------------------
        val routerDecision = runCatching {
            routerStage.execute(playerInput, campaign.sceneState)
        }.getOrNull()

        // ---- 3. Mechanics (pure code, cannot fail on the model) -------------
        val mechanicResults: List<MechanicResult> =
            if (routerDecision?.needs_check == true) {
                routerDecision.checks.map { DeckMechanics.executeCheck(it, random) }
            } else {
                emptyList()
            }

        // ---- 4. Plot --------------------------------------------------------
        val recentSummary = buildRecentSummary(allTurns)
        val plotOutput = runCatching {
            plotStage.execute(
                sessionPlan = campaign.sessionPlan,
                recentSummary = recentSummary,
                playerInput = playerInput,
                routerDecision = routerDecision,
                mechanicResults = mechanicResults,
                retrievedMemories = preRetrieval
            )
        }.getOrElse {
            dev.diegesis.app.data.model.PlotOutput(
                synopsis = "The moment stretches; the situation stays tense.",
                present_npcs = campaign.sceneState.presentNpcIds
            )
        }

        // present_npcs is authoritative for the new scene; an empty list from a
        // fallback means "keep the previous scene" rather than "everyone leaves".
        val presentNpcIds = plotOutput.present_npcs.ifEmpty { campaign.sceneState.presentNpcIds }

        // ---- 5. Agency (optional) -------------------------------------------
        val agencyShouldRun = routerDecision?.run_agency_update == true ||
            plotOutput.scene_change ||
            plotOutput.tracker_updates.isNotEmpty()

        if (agencyShouldRun) {
            presentNpcIds.forEach { npcId ->
                runCatching {
                    val npc = npcStorage.load(campaignId, npcId) ?: return@runCatching
                    val witnessed = witnessedTurnsFor(npcId, allTurns)
                    val updated = agencyStage.updateNpcAgency(npc, witnessed)
                    npcStorage.save(campaignId, npc.copy(agency = updated))
                }
            }
        }

        // ---- 6. Visibility-filtered assembly --------------------------------
        val presentNpcs: List<Npc> = presentNpcIds.mapNotNull { npcStorage.load(campaignId, it) }
        val sceneRetrieval = MemoryRetriever.retrieve(
            query = playerInput + " " + plotOutput.synopsis,
            allMemories = allMemories
        )

        val context = VisibilityContextAssembler.assemble(
            synopsis = plotOutput.synopsis,
            mechanicResults = mechanicResults,
            presentNpcIds = presentNpcIds,
            presentNpcs = presentNpcs,
            allTurns = allTurns,
            retrievedMemories = sceneRetrieval,
            playerInput = playerInput
        )

        // ---- 7. Scene (streaming) -------------------------------------------
        val prose = StringBuilder()
        var interrupted = false
        try {
            sceneStage.execute(context).collect { chunk ->
                prose.append(chunk)
                onChunk(chunk)
            }
        } catch (t: Throwable) {
            interrupted = true
        }
        if (prose.isBlank()) interrupted = true

        val sceneOutput = prose.toString()

        // ---- 8. Memory extraction -------------------------------------------
        val extracted = runCatching {
            memoryExtractionStage.execute(
                playerInput = playerInput,
                synopsis = plotOutput.synopsis,
                sceneOutput = sceneOutput,
                turnIndex = turnIndex
            )
        }.getOrDefault(emptyList())

        extracted.forEach { entry ->
            runCatching { memoryStorage.appendMemory(campaignId, entry) }
        }

        // ---- 9. Tracker updates + scene state -------------------------------
        plotOutput.tracker_updates.forEach { update ->
            runCatching {
                val npc = npcStorage.load(campaignId, update.npc) ?: return@runCatching
                val current = npc.trackers[update.key] ?: 0
                val merged = npc.trackers.toMutableMap()
                merged[update.key] = current + update.delta
                npcStorage.save(campaignId, npc.copy(trackers = merged))
            }
        }

        val newSceneState = SceneState(
            location = plotOutput.location?.takeIf { it.isNotBlank() }
                ?: campaign.sceneState.location,
            presentNpcIds = presentNpcIds
        )
        runCatching {
            campaignStorage.save(
                campaign.copy(
                    sceneState = newSceneState,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // ---- 10. Save the turn ----------------------------------------------
        val variant = TurnVariant(
            id = UUID.randomUUID().toString(),
            synopsis = plotOutput.synopsis,
            sceneOutput = sceneOutput,
            routerDecision = routerDecision,
            presentNpcIds = presentNpcIds,
            mechanicResults = mechanicResults,
            interrupted = interrupted
        )

        runCatching {
            val existing = turnStorage.loadTurn(campaignId, turnIndex)
            if (existing == null) {
                turnStorage.saveTurn(
                    campaignId,
                    Turn(
                        index = turnIndex,
                        playerInput = playerInput,
                        variants = listOf(variant)
                    )
                )
            } else {
                turnStorage.appendVariant(campaignId, turnIndex, variant)
            }
        }

        // ---- 11. Return ------------------------------------------------------
        return variant
    }

    /**
     * Turns a given NPC witnessed — i.e. turns where that NPC was present.
     * Mirrors the visibility invariant at single-NPC granularity.
     */
    private fun witnessedTurnsFor(npcId: String, allTurns: List<Turn>): List<Turn> =
        allTurns.filter { turn ->
            turn.variants.lastOrNull()?.presentNpcIds?.contains(npcId) == true
        }

    /**
     * Compressed story-so-far for the plot stage. Unfiltered on purpose: the
     * plot engine is omniscient, only the scene stage is visibility-bound.
     */
    private fun buildRecentSummary(allTurns: List<Turn>, limit: Int = 6): String {
        if (allTurns.isEmpty()) return ""
        return allTurns.takeLast(limit).joinToString("\n") { turn ->
            val synopsis = turn.variants.lastOrNull()?.synopsis ?: ""
            "- ${turn.playerInput} -> $synopsis"
        }
    }
}
