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
import dev.diegesis.app.engine.assembler.ContextWindowTrimmer
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
import kotlinx.coroutines.CancellationException

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
    private val random: Random = Random.Default,
    // Context-window enforcement (see docs/pipeline.md + AppSettings).
    // Budget for history+payload = (contextWindowTokens - writeMaxTokens) * 0.8.
    private val contextWindowTokens: Int = 32768,
    private val writeMaxTokens: Int = 8192,
    // Live progress hook for the UI. Receives every stageEvents line plus
    // transient stage-boundary lines, so the live log and the persisted
    // Stage Details log read identically.
    private val onPipelineEvent: ((String) -> Unit)? = null
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
        targetTurnIndex: Int? = null,
        // Per-call override of the constructor-level progress hook, so a
        // caller holding a shared orchestrator can still observe one turn.
        // Declared before onChunk so existing trailing-lambda call sites
        // keep binding to onChunk unchanged.
        onPipelineEvent: ((String) -> Unit)? = this.onPipelineEvent,
        // Live tap for scene-stage reasoning deltas (same style as
        // onPipelineEvent; default null keeps existing call sites compiling).
        onReasoningChunk: ((String) -> Unit)? = null,
        onChunk: (String) -> Unit
    ): TurnVariant {
        val campaign = campaignStorage.load(campaignId)
            ?: error("Campaign $campaignId not found")

        // Terse one-line transparency log, stored on the saved variant so the
        // stage-details sheet can explain fallbacks instead of staying silent.
        val stageEvents = mutableListOf<String>()
        fun emitProgress(line: String) {
            onPipelineEvent?.invoke(line)
        }
        fun recordEvent(line: String) {
            stageEvents += line
            emitProgress(line)
        }

        val existingIndices = turnStorage.listTurnIndices(campaignId)
        val turnIndex = targetTurnIndex ?: ((existingIndices.maxOrNull() ?: -1) + 1)
        val allTurns = existingIndices
            .filter { it < turnIndex }
            .mapNotNull { turnStorage.loadTurn(campaignId, it) }

        // ---- 1. Retrieve memories -------------------------------------------
        val allMemories = runCatching { memoryStorage.loadMemories(campaignId) }
            .getOrDefault(emptyList())
        val preRetrieval = MemoryRetriever.retrieve(playerInput, allMemories)

        // ---- 2. Router ------------------------------------------------------
        emitProgress("router: deciding checks…")
        val routerDecision = runCatching {
            routerStage.execute(playerInput, campaign.sceneState)
        }.getOrElse { t ->
            recordEvent("router: fallback used (${t.message ?: t.javaClass.simpleName})")
            null
        }
        if (routerDecision != null) emitProgress("router: done")

        // ---- 3. Mechanics (pure code, cannot fail on the model) -------------
        val mechanicResults: List<MechanicResult> =
            if (routerDecision?.needs_check == true) {
                routerDecision.checks.map { DeckMechanics.executeCheck(it, random) }
            } else {
                emptyList()
            }

        // ---- 4. Plot --------------------------------------------------------
        val recentSummary = buildRecentSummary(allTurns)
        emitProgress("plot: generating turn plan…")
        val plotOutput = runCatching {
            plotStage.execute(
                sessionPlan = campaign.sessionPlan,
                recentSummary = recentSummary,
                playerInput = playerInput,
                routerDecision = routerDecision,
                mechanicResults = mechanicResults,
                retrievedMemories = preRetrieval
            )
        }.getOrElse { t ->
            recordEvent("plot: fallback used (${t.message ?: t.javaClass.simpleName})")
            dev.diegesis.app.data.model.PlotOutput(
                synopsis = PlotStage.FALLBACK_SYNOPSIS,
                present_npcs = campaign.sceneState.presentNpcIds
            )
        }

        // The stage falls back internally on parse failure (per pipeline.md);
        // detect that via the documented sentinel synopsis so it is visible too.
        if (plotOutput.synopsis == PlotStage.FALLBACK_SYNOPSIS &&
            stageEvents.none { it.startsWith("plot:") }
        ) {
            recordEvent("plot: fallback used (json parse failed)")
        }
        if (stageEvents.none { it.startsWith("plot:") }) emitProgress("plot: done")

        // present_npcs is authoritative for the new scene; an empty list from a
        // fallback means "keep the previous scene" rather than "everyone leaves".
        val presentNpcIds = plotOutput.present_npcs.ifEmpty { campaign.sceneState.presentNpcIds }

        // ---- 5. Agency (optional) -------------------------------------------
        val agencyShouldRun = routerDecision?.run_agency_update == true ||
            plotOutput.scene_change ||
            plotOutput.tracker_updates.isNotEmpty()

        if (agencyShouldRun) {
            recordEvent("agency: run for ${presentNpcIds.size} npc(s)")
            presentNpcIds.forEach { npcId ->
                runCatching {
                    val npc = npcStorage.load(campaignId, npcId) ?: return@runCatching
                    val witnessed = witnessedTurnsFor(npcId, allTurns)
                    val updated = agencyStage.updateNpcAgency(npc, witnessed)
                    npcStorage.save(campaignId, npc.copy(agency = updated))
                }.onFailure { t ->
                    recordEvent("agency: update failed for $npcId (${t.message ?: t.javaClass.simpleName})")
                }
            }
            emitProgress("agency: done")
        }

        // ---- 6. Visibility-filtered assembly --------------------------------
        val presentNpcs: List<Npc> = presentNpcIds.mapNotNull { npcStorage.load(campaignId, it) }
        val sceneRetrieval = MemoryRetriever.retrieve(
            query = playerInput + " " + plotOutput.synopsis,
            allMemories = allMemories
        )

        // Context-window enforcement: trim the visibility-filtered history so
        // the estimated payload fits the configured window, dropping oldest
        // turns first. chars/4 ≈ tokens; 80% of (window - write budget).
        val visibleTurns = VisibilityContextAssembler.filterVisibleTurns(allTurns, presentNpcIds)
        val historyBudgetTokens = ((contextWindowTokens - writeMaxTokens) * 0.8).toInt()
        val trimmedTurns = ContextWindowTrimmer.trimToFit(visibleTurns, historyBudgetTokens)
        if (trimmedTurns.size < visibleTurns.size) {
            recordEvent(
                "context: history trimmed to last ${trimmedTurns.size} turns " +
                    "(budget $historyBudgetTokens tokens)"
            )
        }

        val context = VisibilityContextAssembler.assemble(
            synopsis = plotOutput.synopsis,
            mechanicResults = mechanicResults,
            presentNpcIds = presentNpcIds,
            presentNpcs = presentNpcs,
            allTurns = trimmedTurns,
            retrievedMemories = sceneRetrieval,
            playerInput = playerInput
        )

        // ---- 7. Scene (streaming) -------------------------------------------
        val prose = StringBuilder()
        val reasoning = StringBuilder()
        var interrupted = false
        emitProgress("scene: streaming…")
        try {
            sceneStage.execute(
                context,
                onReasoningChunk = { delta ->
                    reasoning.append(delta)
                    onReasoningChunk?.invoke(delta)
                }
            ).collect { chunk ->
                prose.append(chunk)
                onChunk(chunk)
            }
        } catch (t: Throwable) {
            // Cancellation must abort the turn, not masquerade as a completed
            // one: rethrow so no state is written by a cancelled pipeline.
            // The caller (StoryViewModel) owns the decision of whether the
            // partial prose is worth persisting.
            if (t is CancellationException) throw t
            interrupted = true
            recordEvent("scene: interrupted (${t.message ?: t.javaClass.simpleName})")
        }
        if (prose.isBlank() && !interrupted) {
            interrupted = true
            recordEvent("scene: interrupted (empty output)")
        }

        val sceneOutput = prose.toString()

        // ---- 8. Memory extraction -------------------------------------------
        emitProgress("memory: extracting…")
        var memoryFailed = false
        val extracted = runCatching {
            memoryExtractionStage.execute(
                playerInput = playerInput,
                synopsis = plotOutput.synopsis,
                sceneOutput = sceneOutput,
                turnIndex = turnIndex
            )
        }.getOrElse { t ->
            recordEvent("memory: extraction failed (${t.message ?: t.javaClass.simpleName})")
            memoryFailed = true
            emptyList()
        }

        extracted.forEach { entry ->
            runCatching { memoryStorage.appendMemory(campaignId, entry) }
                .onFailure { t ->
                    recordEvent("memory: append failed (${t.message ?: t.javaClass.simpleName})")
                }
        }
        if (!memoryFailed) emitProgress("memory: done")

        // ---- 9. Tracker updates + scene state -------------------------------
        plotOutput.tracker_updates.forEach { update ->
            runCatching {
                val npc = npcStorage.load(campaignId, update.npc)
                if (npc == null) {
                    recordEvent("tracker: update skipped, unknown npc ${update.npc}")
                    return@runCatching
                }
                val current = npc.trackers[update.key] ?: 0
                val merged = npc.trackers.toMutableMap()
                merged[update.key] = current + update.delta
                npcStorage.save(campaignId, npc.copy(trackers = merged))
                val sign = if (update.delta >= 0) "+" else ""
                recordEvent("tracker: ${update.key} $sign${update.delta} applied to ${update.npc}")
            }.onFailure { t ->
                recordEvent("tracker: update failed for ${update.npc} (${t.message ?: t.javaClass.simpleName})")
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
            interrupted = interrupted,
            stageEvents = stageEvents.toList(),
            reasoning = reasoning.toString().takeIf { it.isNotBlank() }
        )

        runCatching {
            val existing = turnStorage.loadTurn(campaignId, turnIndex)
            when {
                existing == null -> turnStorage.saveTurn(
                    campaignId,
                    Turn(
                        index = turnIndex,
                        playerInput = playerInput,
                        variants = listOf(variant)
                    )
                )

                // Explicit regenerate: appending a variant is the point.
                targetTurnIndex != null ->
                    turnStorage.appendVariant(campaignId, turnIndex, variant)

                // New-turn send that lost the index race: a concurrent
                // pipeline (e.g. an orphaned ViewModel's in-flight job)
                // claimed this index while we were streaming. Never stack a
                // send's output as a variant on someone else's turn — claim
                // the next free index instead.
                else -> {
                    val nextFree = (turnStorage.listTurnIndices(campaignId).maxOrNull() ?: -1) + 1
                    turnStorage.saveTurn(
                        campaignId,
                        Turn(
                            index = nextFree,
                            playerInput = playerInput,
                            variants = listOf(variant)
                        )
                    )
                }
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
