package dev.diegesis.app.ui.campaign

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.TurnStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CampaignEditViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var tempDir: File
    private lateinit var storage: CampaignStorage

    private val existingCampaign = Campaign(
        id = "edit-id",
        title = "Original Title",
        premise = "Original premise",
        sessionPlan = "## Act 1\nOriginal plan",
        playerPersona = "Original persona",
        sceneState = SceneState(location = "Tavern", presentNpcIds = listOf("npc-1")),
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Before
    fun setup() {
        tempDir = tmp.newFolder("campaign_edit_test")
        storage = CampaignStorage(tempDir)
    }

    private fun createViewModel(campaignId: String = "edit-id") = CampaignEditViewModel(
        storage = storage,
        campaignId = campaignId,
        coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        ioDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun `init loads existing campaign fields into state`() = runBlocking {
        storage.save(existingCampaign)
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
        assertEquals("Original Title", state.title)
        assertEquals("Original premise", state.premise)
        assertEquals("Tavern", state.location)
        assertEquals("Original persona", state.playerPersona)
        assertEquals("## Act 1\nOriginal plan", state.sessionPlan)
        assertNull(state.errorMessage)
    }

    @Test
    fun `init flags notFound for nonexistent campaign id`() = runBlocking {
        val viewModel = createViewModel(campaignId = "does-not-exist")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.notFound)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `saveCampaign fails when title is blank`() = runBlocking {
        storage.save(existingCampaign)
        val viewModel = createViewModel()

        viewModel.updateTitle("")
        var savedId: String? = null
        viewModel.saveCampaign { savedId = it }

        assertNull(savedId)
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Title"))
        // Disk untouched
        assertEquals("Original Title", storage.load("edit-id")!!.title)
    }

    @Test
    fun `saveCampaign fails when premise is blank`() = runBlocking {
        storage.save(existingCampaign)
        val viewModel = createViewModel()

        viewModel.updatePremise("")
        var savedId: String? = null
        viewModel.saveCampaign { savedId = it }

        assertNull(savedId)
        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("premise", ignoreCase = true))
    }

    @Test
    fun `saveCampaign for nonexistent campaign reports error and does not save`() = runBlocking {
        val viewModel = createViewModel(campaignId = "does-not-exist")

        viewModel.updateTitle("Title")
        viewModel.updatePremise("Premise")
        var savedId: String? = null
        viewModel.saveCampaign { savedId = it }

        assertNull(savedId)
        assertTrue(viewModel.uiState.value.notFound)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(storage.load("does-not-exist"))
    }

    @Test
    fun `saveCampaign persists changed fields under the same id`() = runBlocking {
        storage.save(existingCampaign)
        val viewModel = createViewModel()

        viewModel.updateTitle("New Title")
        viewModel.updatePremise("New premise")
        viewModel.updateLocation("Dungeon")
        viewModel.updatePlayerPersona("New persona")
        viewModel.updateSessionPlan("New plan")

        var savedId: String? = null
        viewModel.saveCampaign { savedId = it }

        assertEquals("edit-id", savedId)
        val saved = storage.load("edit-id")
        assertNotNull(saved)
        assertEquals("edit-id", saved!!.id)
        assertEquals("New Title", saved.title)
        assertEquals("New premise", saved.premise)
        assertEquals("Dungeon", saved.sceneState.location)
        assertEquals("New persona", saved.playerPersona)
        assertEquals("New plan", saved.sessionPlan)
    }

    @Test
    fun `saveCampaign preserves createdAt, presentNpcIds and bumps updatedAt`() = runBlocking {
        storage.save(existingCampaign)
        val viewModel = createViewModel()

        viewModel.updateTitle("New Title")
        viewModel.saveCampaign { }

        val saved = storage.load("edit-id")!!
        assertEquals(1000L, saved.createdAt)
        assertEquals(listOf("npc-1"), saved.sceneState.presentNpcIds)
        assertTrue(saved.updatedAt > 2000L)
    }

    @Test
    fun `saveCampaign leaves turns and memories untouched`() = runBlocking {
        storage.save(existingCampaign)
        val turnStorage = TurnStorage(tempDir)
        val memoryStorage = MemoryStorage(tempDir)
        turnStorage.saveTurn(
            "edit-id",
            Turn(
                index = 0,
                playerInput = "I open the door",
                variants = listOf(
                    TurnVariant(id = "v1", synopsis = "Door opens", sceneOutput = "The door creaks open.")
                )
            )
        )
        memoryStorage.appendMemory(
            "edit-id",
            MemoryEntry(scope = "world", fact = "The door creaks", turn = 0)
        )

        val viewModel = createViewModel()
        viewModel.updateTitle("New Title")
        viewModel.updateSessionPlan("Rewritten plan")
        viewModel.saveCampaign { }

        assertEquals(listOf(0), turnStorage.listTurnIndices("edit-id"))
        val turn = turnStorage.loadTurn("edit-id", 0)
        assertNotNull(turn)
        assertEquals("I open the door", turn!!.playerInput)
        assertEquals(1, turn.variants.size)
        assertEquals("The door creaks open.", turn.variants[0].sceneOutput)

        val memories = memoryStorage.loadMemories("edit-id")
        assertEquals(1, memories.size)
        assertEquals("The door creaks", memories[0].fact)
    }
}
