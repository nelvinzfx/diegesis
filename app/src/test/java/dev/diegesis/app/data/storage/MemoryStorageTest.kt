package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.MemoryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var storage: MemoryStorage

    private val campaignId = "camp-mem"

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        storage = MemoryStorage(filesDir)
    }

    private fun memoriesFile() =
        File(filesDir, "campaigns/$campaignId/memories.jsonl")

    private fun entry(fact: String, turn: Int = 0, scope: String = "campaign", npcId: String? = null) =
        MemoryEntry(scope = scope, npc_id = npcId, fact = fact, turn = turn)

    // ---- append + load (regression safety) ---------------------------------

    @Test
    fun `append then load round-trips entries in order`() = runBlocking {
        storage.appendMemory(campaignId, entry("first", 0))
        storage.appendMemory(campaignId, entry("second", 1))

        val loaded = storage.loadMemories(campaignId)
        assertEquals(listOf("first", "second"), loaded.map { it.fact })
    }

    // ---- deleteMemory ------------------------------------------------------

    @Test
    fun `deleteMemory removes only the first matching line`() = runBlocking {
        // Two identical entries (same scope+npc_id+fact+turn) plus a distinct one.
        storage.appendMemory(campaignId, entry("duplicate fact", turn = 3))
        storage.appendMemory(campaignId, entry("keep me", turn = 4))
        storage.appendMemory(campaignId, entry("duplicate fact", turn = 3))

        storage.deleteMemory(campaignId, entry("duplicate fact", turn = 3))

        val remaining = storage.loadMemories(campaignId)
        assertEquals(2, remaining.size)
        assertEquals(listOf("keep me", "duplicate fact"), remaining.map { it.fact })
    }

    @Test
    fun `deleteMemory matches on scope npc_id fact and turn, ignoring timestamp`() = runBlocking {
        storage.appendMemory(
            campaignId,
            entry("alice fact", turn = 2, scope = "npc", npcId = "alice").copy(ts = 111L)
        )

        // Same identity, different timestamp — must still match.
        storage.deleteMemory(
            campaignId,
            entry("alice fact", turn = 2, scope = "npc", npcId = "alice").copy(ts = 999L)
        )

        assertTrue(storage.loadMemories(campaignId).isEmpty())
    }

    @Test
    fun `deleteMemory with no match is a no-op`() = runBlocking {
        storage.appendMemory(campaignId, entry("stays", turn = 1))
        val before = memoriesFile().readText()

        storage.deleteMemory(campaignId, entry("never existed", turn = 7))

        assertEquals(before, memoriesFile().readText())
        assertEquals(1, storage.loadMemories(campaignId).size)
    }

    @Test
    fun `deleteMemory on a missing file is a no-op`() = runBlocking {
        storage.deleteMemory(campaignId, entry("anything", turn = 0))
        assertFalse(memoriesFile().exists())
        assertTrue(storage.loadMemories(campaignId).isEmpty())
    }

    @Test
    fun `deleteMemory leaves no temp file behind`() = runBlocking {
        storage.appendMemory(campaignId, entry("a", 0))
        storage.appendMemory(campaignId, entry("b", 1))
        storage.deleteMemory(campaignId, entry("a", 0))

        assertFalse(File(memoriesFile().path + ".tmp").exists())
        assertEquals(listOf("b"), storage.loadMemories(campaignId).map { it.fact })
    }

    // ---- clearMemories -----------------------------------------------------

    @Test
    fun `clearMemories deletes the file`() = runBlocking {
        storage.appendMemory(campaignId, entry("wipe me", 0))
        assertTrue(memoriesFile().exists())

        storage.clearMemories(campaignId)

        assertFalse(memoriesFile().exists())
        assertTrue(storage.loadMemories(campaignId).isEmpty())
    }

    @Test
    fun `clearMemories on a missing file is a no-op`() = runBlocking {
        storage.clearMemories(campaignId)
        assertFalse(memoriesFile().exists())
    }

    @Test
    fun `append after clear starts a fresh file`() = runBlocking {
        storage.appendMemory(campaignId, entry("old", 0))
        storage.clearMemories(campaignId)
        storage.appendMemory(campaignId, entry("new", 1))

        assertEquals(listOf("new"), storage.loadMemories(campaignId).map { it.fact })
    }
}
