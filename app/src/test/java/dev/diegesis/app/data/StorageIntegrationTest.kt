package dev.diegesis.app.data

import dev.diegesis.app.data.model.*
import dev.diegesis.app.data.storage.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testSettingsSaveAndLoad() {
        val storage = SettingsStorage(tempFolder.root)
        
        val settings = AppSettings(
            thinkModel = StageModelSelection("openai-compat", "gpt-4o"),
            writeModel = StageModelSelection("anthropic", "claude-3-5-sonnet"),
            openaiBaseUrl = "https://custom.api.com/v1",
            openaiApiKey = "test-key",
            anthropicApiKey = "test-key-2"
        )

        storage.save(settings)
        val loaded = storage.load()

        assertEquals(settings, loaded)
        
        // Verify file was created
        assertTrue(File(tempFolder.root, "settings.json").exists())
    }

    @Test
    fun testSettingsLoadDefault() {
        val storage = SettingsStorage(tempFolder.root)
        val loaded = storage.load()
        
        // Should return default settings when file doesn't exist
        assertNotNull(loaded)
        assertEquals("openai-compat", loaded.thinkModel.provider)
    }

    @Test
    fun testCampaignCrud() {
        val storage = CampaignStorage(tempFolder.root)
        
        val campaign = Campaign(
            id = "campaign-1",
            title = "Test Campaign",
            premise = "Test premise",
            sessionPlan = "Test plan",
            playerPersona = "Test player",
            sceneState = SceneState(location = "Tavern", presentNpcIds = listOf("npc-1"))
        )

        // Save
        storage.save(campaign)
        
        // List
        val campaigns = storage.list()
        assertEquals(1, campaigns.size)
        assertTrue(campaigns.contains("campaign-1"))

        // Load
        val loaded = storage.load("campaign-1")
        assertNotNull(loaded)
        assertEquals(campaign, loaded)

        // Delete
        storage.delete("campaign-1")
        assertEquals(0, storage.list().size)
        assertNull(storage.load("campaign-1"))
    }

    @Test
    fun testNpcCrud() {
        val storage = NpcStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        val npc = Npc(
            id = "npc-1",
            name = "Test NPC",
            description = "A test NPC",
            personality = "Friendly",
            voiceExamples = listOf("Hello!"),
            agency = NpcAgency(goal = "Help player"),
            trackers = mapOf("trust" to 5)
        )

        // Save
        storage.save(campaignId, npc)
        
        // List
        val npcs = storage.list(campaignId)
        assertEquals(1, npcs.size)
        assertTrue(npcs.contains("npc-1"))

        // Load
        val loaded = storage.load(campaignId, "npc-1")
        assertNotNull(loaded)
        assertEquals(npc, loaded)

        // Delete
        storage.delete(campaignId, "npc-1")
        assertEquals(0, storage.list(campaignId).size)
        assertNull(storage.load(campaignId, "npc-1"))
    }

    @Test
    fun testTurnSaveAndLoad() {
        val storage = TurnStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        val turn = Turn(
            index = 0,
            playerInput = "I enter the tavern",
            variants = listOf(
                TurnVariant(
                    id = "variant-1",
                    synopsis = "Player enters",
                    sceneOutput = "You enter the tavern...",
                    presentNpcIds = listOf("npc-1")
                )
            )
        )

        storage.saveTurn(campaignId, turn)
        
        val indices = storage.listTurnIndices(campaignId)
        assertEquals(1, indices.size)
        assertEquals(0, indices[0])

        val loaded = storage.loadTurn(campaignId, 0)
        assertNotNull(loaded)
        assertEquals(turn, loaded)
    }

    @Test
    fun testTurnVariantAppend() {
        val storage = TurnStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        val turn = Turn(
            index = 0,
            playerInput = "I enter the tavern",
            variants = listOf(
                TurnVariant(
                    id = "variant-1",
                    synopsis = "Player enters",
                    sceneOutput = "Output 1"
                )
            )
        )

        storage.saveTurn(campaignId, turn)

        val newVariant = TurnVariant(
            id = "variant-2",
            synopsis = "Alternate entry",
            sceneOutput = "Output 2"
        )

        storage.appendVariant(campaignId, 0, newVariant)

        val loaded = storage.loadTurn(campaignId, 0)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.variants.size)
        assertEquals("variant-2", loaded.variants[1].id)
    }

    @Test
    fun testTurnTruncation() {
        val storage = TurnStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        // Create turns 0-4
        for (i in 0..4) {
            val turn = Turn(
                index = i,
                playerInput = "Input $i",
                variants = listOf(
                    TurnVariant(id = "variant-$i", synopsis = "Synopsis $i", sceneOutput = "Output $i")
                )
            )
            storage.saveTurn(campaignId, turn)
        }

        assertEquals(5, storage.listTurnIndices(campaignId).size)

        // Delete turn 2, which should delete 2, 3, and 4
        storage.deleteTurn(campaignId, 2)

        val remaining = storage.listTurnIndices(campaignId)
        assertEquals(2, remaining.size)
        assertEquals(listOf(0, 1), remaining)
        
        assertNotNull(storage.loadTurn(campaignId, 0))
        assertNotNull(storage.loadTurn(campaignId, 1))
        assertNull(storage.loadTurn(campaignId, 2))
        assertNull(storage.loadTurn(campaignId, 3))
        assertNull(storage.loadTurn(campaignId, 4))
    }

    @Test
    fun testMemoryAppendAndLoad() = runBlocking {
        val storage = MemoryStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        val memory1 = MemoryEntry(
            scope = "campaign",
            npc_id = null,
            fact = "Player entered the tavern",
            turn = 0
        )

        val memory2 = MemoryEntry(
            scope = "npc",
            npc_id = "npc-1",
            fact = "Merchant is grateful",
            turn = 1
        )

        storage.appendMemory(campaignId, memory1)
        storage.appendMemory(campaignId, memory2)

        val loaded = storage.loadMemories(campaignId)
        assertEquals(2, loaded.size)
        assertEquals("Player entered the tavern", loaded[0].fact)
        assertEquals("Merchant is grateful", loaded[1].fact)
    }

    @Test
    fun testMemoryConcurrentAppend() = runBlocking {
        val storage = MemoryStorage(tempFolder.root)
        val campaignId = "campaign-1"
        
        // Append 20 memories concurrently
        val jobs = (0 until 20).map { i ->
            async {
                val memory = MemoryEntry(
                    scope = "test",
                    fact = "Fact $i",
                    turn = i
                )
                storage.appendMemory(campaignId, memory)
            }
        }

        jobs.awaitAll()

        val loaded = storage.loadMemories(campaignId)
        assertEquals(20, loaded.size)
        
        // All facts should be present (order might vary)
        val facts = loaded.map { it.fact }.toSet()
        assertEquals(20, facts.size)
        for (i in 0 until 20) {
            assertTrue(facts.contains("Fact $i"))
        }
    }

    @Test
    fun testAtomicWriteOverwrites() {
        val storage = CampaignStorage(tempFolder.root)
        
        val campaign1 = Campaign(
            id = "campaign-1",
            title = "Original",
            premise = "Original premise",
            sessionPlan = "Original plan"
        )

        storage.save(campaign1)
        
        val campaign2 = campaign1.copy(
            title = "Updated",
            premise = "Updated premise"
        )

        storage.save(campaign2)
        
        val loaded = storage.load("campaign-1")
        assertNotNull(loaded)
        assertEquals("Updated", loaded!!.title)
        assertEquals("Updated premise", loaded.premise)
        
        // Verify no temp file left behind
        val campaignDir = File(tempFolder.root, "campaigns/campaign-1")
        val tempFiles = campaignDir.listFiles()?.filter { it.name.endsWith(".tmp") }
        assertTrue(tempFiles.isNullOrEmpty())
    }

    @Test
    fun testMultipleCampaigns() {
        val storage = CampaignStorage(tempFolder.root)
        
        for (i in 1..5) {
            val campaign = Campaign(
                id = "campaign-$i",
                title = "Campaign $i",
                premise = "Premise $i",
                sessionPlan = "Plan $i"
            )
            storage.save(campaign)
        }

        val campaigns = storage.list()
        assertEquals(5, campaigns.size)
        
        for (i in 1..5) {
            assertTrue(campaigns.contains("campaign-$i"))
            val loaded = storage.load("campaign-$i")
            assertNotNull(loaded)
            assertEquals("Campaign $i", loaded!!.title)
        }
    }
}
