package dev.diegesis.app.engine

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.ai.AiCaller
import dev.diegesis.app.ui.story.StoryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression tests for the "three swipeable variants after one send" report.
 *
 * Mechanism: StoryViewModel is remembered with (campaignId, aiCaller) in
 * MainActivity; a settings reload rebuilds aiCaller and forgets the ViewModel
 * WITHOUT cancelling its scope. The orphaned pipeline keeps streaming with a
 * turn index computed at launch (maxOrNull + 1). A fresh ViewModel then sends
 * again, computes the SAME index, and the orchestrator's save step used to
 * appendVariant whenever the file already existed — stacking unrelated sends
 * as variants on one turn.
 */
class TurnIndexRaceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var campaigns: CampaignStorage
    private lateinit var npcs: NpcStorage
    private lateinit var turns: TurnStorage
    private lateinit var memories: MemoryStorage

    private val campaignId = "race-camp"

    /**
     * Scripted AiCaller whose prose stream can be parked on a gate so a
     * pipeline can be frozen mid-stream while another one runs to completion.
     */
    private class GatedAiCaller(
        var proseChunks: List<String> = listOf("The ", "story ", "unfolds."),
        var gate: CompletableDeferred<Unit>? = null,
    ) : AiCaller {

        override suspend fun <T> generateStructured(
            systemPrompt: String,
            userPrompt: String,
            decoder: (String) -> T,
            fallback: T,
        ): T {
            val json = when {
                systemPrompt.contains("router", ignoreCase = true) ->
                    """{"needs_check":false,"checks":[],"run_agency_update":false,"lore_query":null}"""
                systemPrompt.contains("plot engine", ignoreCase = true) ->
                    """{"synopsis":"A tense encounter.","present_npcs":[],"scene_change":false,"location":null,"tracker_updates":[]}"""
                systemPrompt.contains("inner life", ignoreCase = true) ->
                    """{"goal":"g","stance":"s","will_act_on":"w"}"""
                systemPrompt.contains("Extract durable facts", ignoreCase = true) ->
                    """[]"""
                else -> return fallback
            }
            return runCatching { decoder(json) }.getOrDefault(fallback)
        }

        override suspend fun streamProse(systemPrompt: String, userPrompt: String): Flow<String> {
            val g = gate
            return if (g != null) {
                flow {
                    emit("partial ")
                    g.await()
                    emit("rest")
                }
            } else {
                proseChunks.asFlow()
            }
        }
    }

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        campaigns = CampaignStorage(filesDir)
        npcs = NpcStorage(filesDir)
        turns = TurnStorage(filesDir)
        memories = MemoryStorage(filesDir)

        campaigns.save(
            Campaign(
                id = campaignId,
                title = "Race Campaign",
                premise = "premise",
                sessionPlan = "Act 1",
                sceneState = SceneState(location = "Tavern")
            )
        )
    }

    private fun orchestrator(ai: AiCaller) = PipelineOrchestrator(
        aiCaller = ai,
        campaignStorage = campaigns,
        npcStorage = npcs,
        turnStorage = turns,
        memoryStorage = memories
    )

    // ---- mechanism 3 + save-step guard --------------------------------------

    @Test
    fun `concurrent new-turn sends never stack variants on one turn`() = runBlocking {
        // Pipeline A: parked mid-stream, holding turn index 0 in a local.
        val gate = CompletableDeferred<Unit>()
        val gatedAi = GatedAiCaller(gate = gate)
        val orchA = orchestrator(gatedAi)

        val jobA = launch(Dispatchers.Unconfined) {
            orchA.executeTurn(campaignId, "first send") {}
        }
        // A is frozen at the gate; nothing persisted yet.
        assertTrue(turns.listTurnIndices(campaignId).isEmpty())

        // Pipeline B: a fresh send (new ViewModel after remember-key churn).
        // It also computes index 0 and completes first.
        orchestrator(GatedAiCaller()).executeTurn(campaignId, "second send") {}
        assertEquals(listOf(0), turns.listTurnIndices(campaignId))

        // Release A: its save step finds index 0 already claimed by an
        // unrelated send. It must NOT append a variant to B's turn.
        gate.complete(Unit)
        jobA.join()

        val indices = turns.listTurnIndices(campaignId)
        assertEquals("each send must own its own turn", listOf(0, 1), indices)
        indices.forEach { idx ->
            val turn = turns.loadTurn(campaignId, idx)!!
            assertEquals(
                "no turn may accumulate variants from a plain send",
                1, turn.variants.size
            )
        }
        assertEquals("second send", turns.loadTurn(campaignId, 0)!!.playerInput)
        assertEquals("first send", turns.loadTurn(campaignId, 1)!!.playerInput)
    }

    @Test
    fun `regenerate still appends a variant to the target turn`() = runBlocking {
        val ai = GatedAiCaller()
        val orch = orchestrator(ai)
        orch.executeTurn(campaignId, "original") {}

        ai.proseChunks = listOf("Alternate.")
        orch.executeTurn(campaignId, "original", targetTurnIndex = 0) {}

        val turn = turns.loadTurn(campaignId, 0)!!
        assertEquals(2, turn.variants.size)
        assertEquals("Alternate.", turn.variants[1].sceneOutput)
        assertEquals(listOf(0), turns.listTurnIndices(campaignId))
    }

    // ---- mechanism 1: cancellation must not write ---------------------------

    @Test
    fun `cancelled pipeline writes nothing - persistence is the caller's job`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val orch = orchestrator(GatedAiCaller(gate = gate))

        val job = launch(Dispatchers.Unconfined) {
            orch.executeTurn(campaignId, "doomed send") {}
        }
        job.cancel()
        job.join()

        // Pre-fix the scene-stage catch swallowed CancellationException and the
        // pipeline continued to the save step, persisting an interrupted turn
        // in ADDITION to StoryViewModel.persistInterruptedTurn -> duplicates.
        assertTrue(
            "a cancelled orchestrator run must not persist a turn",
            turns.listTurnIndices(campaignId).isEmpty()
        )
        assertEquals(
            "a cancelled orchestrator run must not mutate scene state",
            "Tavern", campaigns.load(campaignId)!!.sceneState.location
        )
    }

    @Test
    fun `stop via ViewModel persists exactly one interrupted turn`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val gatedAi = GatedAiCaller(gate = gate)
        val viewModel = StoryViewModel(
            campaignId = campaignId,
            orchestrator = orchestrator(gatedAi),
            campaignStorage = campaigns,
            turnStorage = turns,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.sendPlayerInput("risky move")
        assertEquals("partial ", viewModel.uiState.value.streamingText)

        viewModel.stopGeneration()

        // Exactly one turn on disk: the ViewModel's interrupted save. The
        // orchestrator itself must have written nothing (see test above).
        val indices = turns.listTurnIndices(campaignId)
        assertEquals(listOf(0), indices)
        val saved = turns.loadTurn(campaignId, 0)!!
        assertEquals(1, saved.variants.size)
        assertTrue(saved.variants.single().interrupted)
        assertEquals("partial ", saved.variants.single().sceneOutput)
    }

    // ---- the user-visible symptom, end to end -------------------------------

    @Test
    fun `orphaned ViewModel racing a fresh one cannot produce a variant pager`() = runBlocking {
        // vm1 = the ViewModel forgotten by remember-key churn, generation in flight.
        val gate = CompletableDeferred<Unit>()
        val vm1 = StoryViewModel(
            campaignId = campaignId,
            orchestrator = orchestrator(GatedAiCaller(gate = gate)),
            campaignStorage = campaigns,
            turnStorage = turns,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        vm1.sendPlayerInput("orphaned send")

        // vm2 = the freshly remembered ViewModel; isStreaming=false there, so
        // the user can immediately send again.
        val vm2 = StoryViewModel(
            campaignId = campaignId,
            orchestrator = orchestrator(GatedAiCaller()),
            campaignStorage = campaigns,
            turnStorage = turns,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        vm2.sendPlayerInput("fresh send")

        // Orphan finishes streaming afterwards.
        gate.complete(Unit)

        vm2.loadTurns()
        val allTurns = vm2.uiState.value.turns
        assertEquals(2, allTurns.size)
        allTurns.forEach { turn ->
            assertEquals(
                "one send must never surface as an extra swipeable variant",
                1, turn.variants.size
            )
        }
    }
}
