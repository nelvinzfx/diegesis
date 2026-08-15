package dev.diegesis.app.ui.memories

import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoriesViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var memoryStorage: MemoryStorage
    private lateinit var npcStorage: NpcStorage
    private lateinit var viewModel: MemoriesViewModel

    private val campaignId = "camp-vm"

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        memoryStorage = MemoryStorage(filesDir)
        npcStorage = NpcStorage(filesDir)
        viewModel = MemoriesViewModel(
            memoryStorage = memoryStorage,
            npcStorage = npcStorage,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun entry(fact: String, turn: Int = 0, scope: String = "campaign", npcId: String? = null) =
        MemoryEntry(scope = scope, npc_id = npcId, fact = fact, turn = turn)

    // ---- load ---------------------------------------------------------------

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertTrue(state.memories.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.showClearConfirm)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadMemories loads facts in file order newest last`() = runBlocking {
        memoryStorage.appendMemory(campaignId, entry("oldest", turn = 0))
        memoryStorage.appendMemory(campaignId, entry("newest", turn = 12))

        viewModel.loadMemories(campaignId)

        val state = viewModel.uiState.value
        assertEquals(campaignId, state.campaignId)
        assertEquals(listOf("oldest", "newest"), state.memories.map { it.fact })
        assertEquals(12, state.memories.last().turn)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadMemories with no file yields the empty state`() {
        viewModel.loadMemories(campaignId)
        assertTrue(viewModel.uiState.value.memories.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ---- grouping / npc name resolution --------------------------------------

    @Test
    fun `npc-scope facts resolve the NPC name via storage`() = runBlocking {
        npcStorage.save(campaignId, Npc(id = "alice", name = "Alice the Bold", description = "", personality = ""))
        memoryStorage.appendMemory(campaignId, entry("fears water", scope = "npc", npcId = "alice"))
        memoryStorage.appendMemory(campaignId, entry("the bridge is out", scope = "campaign"))

        viewModel.loadMemories(campaignId)

        val state = viewModel.uiState.value
        assertEquals("Alice the Bold", state.npcNames["alice"])
        val npcEntry = state.memories.first { it.scope == "npc" }
        assertEquals("Alice the Bold", viewModel.npcNameFor(npcEntry))
        val campaignEntry = state.memories.first { it.scope == "campaign" }
        assertNull(viewModel.npcNameFor(campaignEntry))
    }

    @Test
    fun `missing NPC falls back to the raw id`() = runBlocking {
        memoryStorage.appendMemory(campaignId, entry("ghost fact", scope = "npc", npcId = "deleted-npc"))

        viewModel.loadMemories(campaignId)

        val npcEntry = viewModel.uiState.value.memories.first()
        assertEquals("deleted-npc", viewModel.npcNameFor(npcEntry))
    }

    // ---- delete ---------------------------------------------------------------

    @Test
    fun `deleteMemory removes the entry and reloads`() = runBlocking {
        memoryStorage.appendMemory(campaignId, entry("delete me", turn = 1))
        memoryStorage.appendMemory(campaignId, entry("keep me", turn = 2))
        viewModel.loadMemories(campaignId)

        viewModel.deleteMemory(entry("delete me", turn = 1))

        assertEquals(listOf("keep me"), viewModel.uiState.value.memories.map { it.fact })
        assertEquals(listOf("keep me"), memoryStorage.loadMemories(campaignId).map { it.fact })
    }

    @Test
    fun `deleteMemory before load is a safe no-op`() = runBlocking {
        memoryStorage.appendMemory(campaignId, entry("untouched", turn = 0))

        // campaignId is still blank in state; must not throw or delete anything.
        viewModel.deleteMemory(entry("untouched", turn = 0))

        assertEquals(1, memoryStorage.loadMemories(campaignId).size)
    }

    // ---- clear all -------------------------------------------------------------

    @Test
    fun `requestClearAll and cancelClearAll toggle the confirm flag`() {
        viewModel.requestClearAll()
        assertTrue(viewModel.uiState.value.showClearConfirm)

        viewModel.cancelClearAll()
        assertFalse(viewModel.uiState.value.showClearConfirm)
    }

    @Test
    fun `confirmClearAll wipes storage and the list`() = runBlocking {
        memoryStorage.appendMemory(campaignId, entry("a", 0))
        memoryStorage.appendMemory(campaignId, entry("b", 1))
        viewModel.loadMemories(campaignId)
        viewModel.requestClearAll()

        viewModel.confirmClearAll()

        assertFalse(viewModel.uiState.value.showClearConfirm)
        assertTrue(viewModel.uiState.value.memories.isEmpty())
        assertTrue(memoryStorage.loadMemories(campaignId).isEmpty())
    }
}
