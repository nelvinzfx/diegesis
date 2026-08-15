package dev.diegesis.app.ui.story

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.PipelineOrchestrator
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoryViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var campaigns: CampaignStorage
    private lateinit var turns: TurnStorage
    private lateinit var npcs: NpcStorage
    private lateinit var memories: MemoryStorage
    private lateinit var fakeAi: FakeStoryAiCaller
    private lateinit var orchestrator: PipelineOrchestrator
    private lateinit var viewModel: StoryViewModel

    private val campaignId = "test-camp"

    private class FakeStoryAiCaller : AiCaller {
        var proseChunks = listOf("The ", "story ", "unfolds.")
        var shouldThrow = false

        override suspend fun <T> generateStructured(
            systemPrompt: String,
            userPrompt: String,
            decoder: (String) -> T,
            fallback: T
        ): T {
            if (shouldThrow) return fallback
            val json = when {
                systemPrompt.contains("router", ignoreCase = true) ->
                    """{"needs_check":false,"checks":[],"run_agency_update":false,"lore_query":null}"""
                systemPrompt.contains("plot engine", ignoreCase = true) ->
                    """{"synopsis":"A tense encounter.","present_npcs":[],"scene_change":false,"location":null,"tracker_updates":[]}"""
                systemPrompt.contains("inner life", ignoreCase = true) ->
                    """{"goal":"survive","stance":"wary","will_act_on":"danger"}"""
                systemPrompt.contains("Extract durable facts", ignoreCase = true) ->
                    """[]"""
                else -> return fallback
            }
            return runCatching { decoder(json) }.getOrDefault(fallback)
        }

        override suspend fun streamProse(systemPrompt: String, userPrompt: String): Flow<String> {
            if (shouldThrow) throw RuntimeException("Model failed")
            return proseChunks.asFlow()
        }
    }

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        campaigns = CampaignStorage(filesDir)
        turns = TurnStorage(filesDir)
        npcs = NpcStorage(filesDir)
        memories = MemoryStorage(filesDir)
        fakeAi = FakeStoryAiCaller()

        campaigns.save(
            Campaign(
                id = campaignId,
                title = "Chronicles of Ruin",
                premise = "A dark fantasy tale",
                sessionPlan = "Act 1",
                sceneState = SceneState(location = "Tavern")
            )
        )

        orchestrator = PipelineOrchestrator(
            aiCaller = fakeAi,
            campaignStorage = campaigns,
            npcStorage = npcs,
            turnStorage = turns,
            memoryStorage = memories
        )

        viewModel = StoryViewModel(
            campaignId = campaignId,
            orchestrator = orchestrator,
            campaignStorage = campaigns,
            turnStorage = turns,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `initial state loads campaign and empty turns`() {
        val state = viewModel.uiState.value
        assertNotNull(state.campaign)
        assertEquals("Chronicles of Ruin", state.campaign?.title)
        assertTrue(state.turns.isEmpty())
        assertFalse(state.isStreaming)
    }

    @Test
    fun `sendPlayerInput runs turn and updates state`() = runBlocking {
        viewModel.sendPlayerInput("I look around the room.")

        val state = viewModel.uiState.value
        assertEquals(1, state.turns.size)
        assertEquals("I look around the room.", state.turns[0].playerInput)
        assertEquals(1, state.turns[0].variants.size)
        assertEquals("The story unfolds.", state.turns[0].variants[0].sceneOutput)
        assertFalse(state.isStreaming)
    }

    @Test
    fun `blank player input is ignored`() {
        viewModel.sendPlayerInput("   ")
        assertTrue(viewModel.uiState.value.turns.isEmpty())
    }

    @Test
    fun `switchVariant updates active variant index for turn`() {
        // Seed turn with 2 variants
        turns.saveTurn(
            campaignId,
            Turn(
                index = 0,
                playerInput = "test",
                variants = listOf(
                    TurnVariant(id = "v1", synopsis = "s1", sceneOutput = "o1"),
                    TurnVariant(id = "v2", synopsis = "s2", sceneOutput = "o2")
                )
            )
        )
        viewModel.loadTurns()

        viewModel.switchVariant(0, 1)
        assertEquals(1, viewModel.uiState.value.selectedVariantIndices[0])
    }

    @Test
    fun `regenerateTurn appends new variant and switches to it`() = runBlocking {
        viewModel.sendPlayerInput("Initial action")
        assertEquals(1, viewModel.uiState.value.turns[0].variants.size)

        fakeAi.proseChunks = listOf("Alternate ", "outcome.")
        viewModel.regenerateTurn(0)

        val updated = viewModel.uiState.value.turns[0]
        assertEquals(2, updated.variants.size)
        assertEquals("Alternate outcome.", updated.variants[1].sceneOutput)
        assertEquals(1, viewModel.uiState.value.selectedVariantIndices[0])
    }

    @Test
    fun `deleteTurn removes target turn and subsequent turns`() = runBlocking {
        viewModel.sendPlayerInput("Turn 0")
        viewModel.sendPlayerInput("Turn 1")
        viewModel.sendPlayerInput("Turn 2")

        assertEquals(3, viewModel.uiState.value.turns.size)

        viewModel.deleteTurn(1)

        val remaining = viewModel.uiState.value.turns
        assertEquals(1, remaining.size)
        assertEquals("Turn 0", remaining[0].playerInput)
    }

    @Test
    fun `editAndResend truncates turn and executes new input`() = runBlocking {
        viewModel.sendPlayerInput("Original turn 0")
        viewModel.sendPlayerInput("Original turn 1")

        viewModel.editAndResend(1, "Modified turn 1")

        val state = viewModel.uiState.value
        assertEquals(2, state.turns.size)
        assertEquals("Original turn 0", state.turns[0].playerInput)
        assertEquals("Modified turn 1", state.turns[1].playerInput)
    }

    @Test
    fun `stage details sheet toggle`() {
        viewModel.showStageDetails(0)
        assertEquals(0, viewModel.uiState.value.activeStageDetailsTurn)

        viewModel.showStageDetails(null)
        assertNull(viewModel.uiState.value.activeStageDetailsTurn)
    }
}
