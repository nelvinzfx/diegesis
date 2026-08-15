package dev.diegesis.app.ui.campaign

import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeAiCaller : AiCaller {
    override suspend fun <T> generateStructured(
        systemPrompt: String,
        userPrompt: String,
        decoder: (String) -> T,
        fallback: T
    ): T = fallback

    override suspend fun streamProse(
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> = flowOf("## Act 1\n", "### Setup\n", "The story begins...")
}

class CampaignCreateViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var tempDir: File
    private lateinit var storage: CampaignStorage
    private lateinit var aiCaller: AiCaller
    private lateinit var viewModel: CampaignCreateViewModel

    @Before
    fun setup() {
        tempDir = tmp.newFolder("campaign_create_test")
        storage = CampaignStorage(tempDir)
        aiCaller = FakeAiCaller()
        viewModel = CampaignCreateViewModel(
            storage = storage,
            aiCaller = aiCaller,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertEquals("", state.title)
        assertEquals("", state.premise)
        assertEquals("Beginning", state.initialLocation)
        assertEquals("", state.playerPersona)
        assertEquals("", state.sessionPlan)
        assertFalse(state.isGeneratingPlan)
    }

    @Test
    fun `updateTitle updates title in state`() {
        viewModel.updateTitle("My Campaign")
        assertEquals("My Campaign", viewModel.uiState.value.title)
    }

    @Test
    fun `updatePremise updates premise in state`() {
        viewModel.updatePremise("A dark fantasy")
        assertEquals("A dark fantasy", viewModel.uiState.value.premise)
    }

    @Test
    fun `updateInitialLocation updates location in state`() {
        viewModel.updateInitialLocation("The Tavern")
        assertEquals("The Tavern", viewModel.uiState.value.initialLocation)
    }

    @Test
    fun `updatePlayerPersona updates persona in state`() {
        viewModel.updatePlayerPersona("A rogue thief")
        assertEquals("A rogue thief", viewModel.uiState.value.playerPersona)
    }

    @Test
    fun `updateSessionPlan updates plan in state`() {
        viewModel.updateSessionPlan("Custom plan")
        assertEquals("Custom plan", viewModel.uiState.value.sessionPlan)
    }

    @Test
    fun `generateSessionPlan fails when premise is blank`() = runBlocking {
        viewModel.generateSessionPlan()
        
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Premise"))
    }

    @Test
    fun `generateSessionPlan streams plan from AI`() = runBlocking {
        viewModel.updatePremise("A fantasy adventure")
        viewModel.generateSessionPlan()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingPlan)
        assertTrue(state.sessionPlan.contains("Act 1"))
        assertTrue(state.sessionPlan.contains("The story begins"))
    }

    @Test
    fun `createCampaign fails when title is blank`() = runBlocking {
        var createdId: String? = null
        viewModel.createCampaign { createdId = it }

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Title"))
    }

    @Test
    fun `createCampaign fails when premise is blank`() = runBlocking {
        viewModel.updateTitle("Title")
        var createdId: String? = null
        viewModel.createCampaign { createdId = it }

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Premise"))
    }

    @Test
    fun `createCampaign saves campaign and invokes callback`() = runBlocking {
        viewModel.updateTitle("Title")
        viewModel.updatePremise("Premise")
        viewModel.updateInitialLocation("Dungeon")
        viewModel.updatePlayerPersona("Warrior")
        viewModel.updateSessionPlan("Plan")

        var createdId: String? = null
        viewModel.createCampaign { createdId = it }

        assertNotNull(createdId)
        val saved = storage.load(createdId!!)
        assertNotNull(saved)
        assertEquals("Title", saved.title)
        assertEquals("Premise", saved.premise)
        assertEquals("Dungeon", saved.sceneState.location)
        assertEquals("Warrior", saved.playerPersona)
        assertEquals("Plan", saved.sessionPlan)
    }
}
