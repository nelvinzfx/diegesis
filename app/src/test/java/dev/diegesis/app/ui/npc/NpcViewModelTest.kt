package dev.diegesis.app.ui.npc

import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.data.storage.NpcStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var tempDir: File
    private lateinit var storage: NpcStorage
    private lateinit var viewModel: NpcViewModel

    @Before
    fun setup() {
        tempDir = tmp.newFolder("npc_test")
        storage = NpcStorage(tempDir)
        viewModel = NpcViewModel(
            storage = storage,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertEquals("", state.campaignId)
        assertTrue(state.npcs.isEmpty())
        assertNull(state.editingNpc)
        assertFalse(state.isLoading)
        assertFalse(state.showImportDialog)
    }

    @Test
    fun `loadNpcs loads NPCs for campaign`() = runBlocking {
        val campaignId = "test-campaign"
        val npc = Npc(
            id = "npc-1",
            name = "Gandalf",
            description = "A wizard",
            personality = "Wise",
            voiceExamples = listOf("You shall not pass!")
        )
        storage.save(campaignId, npc)

        viewModel.loadNpcs(campaignId)

        val state = viewModel.uiState.value
        assertEquals(campaignId, state.campaignId)
        assertEquals(1, state.npcs.size)
        assertEquals("Gandalf", state.npcs[0].name)
    }

    @Test
    fun `createNewNpc sets editing NPC with empty fields`() {
        viewModel.createNewNpc()

        val npc = viewModel.uiState.value.editingNpc
        assertNotNull(npc)
        assertEquals("", npc.name)
        assertEquals("", npc.description)
        assertEquals("", npc.personality)
        assertTrue(npc.voiceExamples.isEmpty())
        assertTrue(npc.trackers.isEmpty())
    }

    @Test
    fun `editNpc sets the NPC for editing`() {
        val npc = Npc(
            id = "test-id",
            name = "Test NPC",
            description = "Test",
            personality = "Friendly"
        )

        viewModel.editNpc(npc)

        assertEquals(npc, viewModel.uiState.value.editingNpc)
    }

    @Test
    fun `updateEditingNpc updates the editing NPC`() {
        viewModel.createNewNpc()
        val npc = viewModel.uiState.value.editingNpc!!

        val updated = npc.copy(name = "Updated Name")
        viewModel.updateEditingNpc(updated)

        assertEquals("Updated Name", viewModel.uiState.value.editingNpc?.name)
    }

    @Test
    fun `saveNpc fails when name is blank`() = runBlocking {
        viewModel.loadNpcs("test-campaign")
        viewModel.createNewNpc()

        viewModel.saveNpc()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("name"))
    }

    @Test
    fun `saveNpc saves NPC and clears editing state`() = runBlocking {
        val campaignId = "test-campaign"
        viewModel.loadNpcs(campaignId)
        viewModel.createNewNpc()

        val npc = viewModel.uiState.value.editingNpc!!.copy(
            name = "Saved NPC",
            description = "Desc",
            personality = "Pers",
            voiceExamples = listOf("Quote 1"),
            agency = NpcAgency(goal = "Goal"),
            trackers = mapOf("trust" to 10)
        )
        viewModel.updateEditingNpc(npc)
        viewModel.saveNpc()

        assertNull(viewModel.uiState.value.editingNpc)
        assertEquals(1, viewModel.uiState.value.npcs.size)
        assertEquals("Saved NPC", viewModel.uiState.value.npcs[0].name)

        val saved = storage.load(campaignId, npc.id)
        assertNotNull(saved)
        assertEquals("Saved NPC", saved.name)
        assertEquals(10, saved.trackers["trust"])
    }

    @Test
    fun `deleteNpc removes NPC from storage and list`() = runBlocking {
        val campaignId = "test-campaign"
        val npc = Npc(id = "del-id", name = "To Delete", description = "", personality = "")
        storage.save(campaignId, npc)
        viewModel.loadNpcs(campaignId)

        assertEquals(1, viewModel.uiState.value.npcs.size)

        viewModel.deleteNpc("del-id")

        assertTrue(viewModel.uiState.value.npcs.isEmpty())
        assertNull(storage.load(campaignId, "del-id"))
    }

    @Test
    fun `importCardJson imports card and sets editing state`() {
        val json = """
            {
                "spec": "chara_card_v2",
                "spec_version": "2.0",
                "data": {
                    "name": "Imported Hero",
                    "description": "Hero description",
                    "personality": "Brave",
                    "mes_example": "<START>\n{{char}}: Onward!"
                }
            }
        """.trimIndent()

        viewModel.importCardJson(json)

        val editing = viewModel.uiState.value.editingNpc
        assertNotNull(editing)
        assertEquals("Imported Hero", editing.name)
        assertEquals("Hero description", editing.description)
        assertEquals("Brave", editing.personality)
        assertFalse(viewModel.uiState.value.showImportDialog)
    }
}
