package dev.diegesis.app.data

import dev.diegesis.app.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelSerializationTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAppSettingsSerialization() {
        val settings = AppSettings(
            thinkModel = StageModelSelection("openai-compat", "gpt-4o-mini"),
            writeModel = StageModelSelection("anthropic", "claude-3-5-sonnet-20241022"),
            openaiBaseUrl = "https://api.openai.com/v1",
            openaiApiKey = "test-key",
            anthropicApiKey = "test-key-2"
        )

        val jsonString = json.encodeToString(settings)
        val deserialized = json.decodeFromString<AppSettings>(jsonString)

        assertEquals(settings, deserialized)
        assertEquals("openai-compat", deserialized.thinkModel.provider)
        assertEquals("gpt-4o-mini", deserialized.thinkModel.model)
    }

    @Test
    fun testCampaignSerialization() {
        val campaign = Campaign(
            id = "campaign-1",
            title = "Test Campaign",
            premise = "A test premise",
            sessionPlan = "A test session plan",
            playerPersona = "Test player",
            sceneState = SceneState(
                location = "Tavern",
                presentNpcIds = listOf("npc-1", "npc-2")
            ),
            thinkModel = StageModelSelection("openai-compat", "gpt-4o"),
            writeModel = StageModelSelection("anthropic", "claude-3-5-sonnet"),
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val jsonString = json.encodeToString(campaign)
        val deserialized = json.decodeFromString<Campaign>(jsonString)

        assertEquals(campaign, deserialized)
        assertEquals("Tavern", deserialized.sceneState.location)
        assertEquals(2, deserialized.sceneState.presentNpcIds.size)
    }

    @Test
    fun testNpcSerialization() {
        val npc = Npc(
            id = "npc-1",
            name = "Test NPC",
            description = "A test NPC",
            personality = "Friendly",
            voiceExamples = listOf("Hello there!", "How are you?"),
            agency = NpcAgency(
                goal = "Help the player",
                stance = "Friendly",
                willActOn = "Player requests"
            ),
            trackers = mapOf("reputation" to 5, "trust" to 3),
            sourceCard = "base64encodeddata"
        )

        val jsonString = json.encodeToString(npc)
        val deserialized = json.decodeFromString<Npc>(jsonString)

        assertEquals(npc, deserialized)
        assertEquals(2, deserialized.voiceExamples.size)
        assertEquals(2, deserialized.trackers.size)
    }

    @Test
    fun testTurnSerialization() {
        val turn = Turn(
            index = 0,
            playerInput = "I enter the tavern",
            variants = listOf(
                TurnVariant(
                    id = "variant-1",
                    synopsis = "Player enters",
                    sceneOutput = "You enter the tavern...",
                    routerDecision = RouterDecision(
                        needs_check = true,
                        checks = listOf(
                            MechanicCheck(skill = "perception", dc = 10, modifier = 2, advantage = 1)
                        ),
                        run_agency_update = true,
                        lore_query = "tavern description"
                    ),
                    presentNpcIds = listOf("npc-1"),
                    mechanicResults = listOf(
                        MechanicResult(
                            skill = "perception",
                            dc = 10,
                            modifier = 2,
                            drawn = listOf(
                                DrawnCard(rank = 8, suit = "hearts", name = "8♥")
                            ),
                            value = 10,
                            tier = "success"
                        )
                    ),
                    interrupted = false,
                    timestamp = 1000L
                )
            ),
            createdAt = 1000L
        )

        val jsonString = json.encodeToString(turn)
        val deserialized = json.decodeFromString<Turn>(jsonString)

        assertEquals(turn, deserialized)
        assertEquals(1, deserialized.variants.size)
        val variant = deserialized.variants[0]
        assertEquals("variant-1", variant.id)
        assertNotNull(variant.routerDecision)
        assertEquals(1, variant.mechanicResults.size)
    }

    @Test
    fun testPipelineModelsSerialization() {
        val routerDecision = RouterDecision(
            needs_check = true,
            checks = listOf(
                MechanicCheck(skill = "stealth", dc = 15, modifier = -1, advantage = 0)
            ),
            run_agency_update = false,
            lore_query = null
        )

        val plotOutput = PlotOutput(
            synopsis = "Test synopsis",
            present_npcs = listOf("npc-1", "npc-2"),
            scene_change = true,
            location = "Forest",
            tracker_updates = listOf(
                TrackerUpdate(npc = "npc-1", key = "trust", delta = 1)
            )
        )

        val routerJson = json.encodeToString(routerDecision)
        val plotJson = json.encodeToString(plotOutput)

        val deserializedRouter = json.decodeFromString<RouterDecision>(routerJson)
        val deserializedPlot = json.decodeFromString<PlotOutput>(plotJson)

        assertEquals(routerDecision, deserializedRouter)
        assertEquals(plotOutput, deserializedPlot)
        assertTrue(deserializedPlot.scene_change)
        assertEquals("Forest", deserializedPlot.location)
    }

    @Test
    fun testMemoryEntrySerialization() {
        val memory = MemoryEntry(
            scope = "campaign",
            npc_id = "npc-1",
            fact = "Player helped rescue the merchant",
            turn = 5,
            ts = 1000L
        )

        val jsonString = json.encodeToString(memory)
        val deserialized = json.decodeFromString<MemoryEntry>(jsonString)

        assertEquals(memory, deserialized)
        assertEquals("campaign", deserialized.scope)
        assertEquals("npc-1", deserialized.npc_id)
    }

    @Test
    fun testDefaultValues() {
        // Test that default values work correctly
        val minimalCampaign = Campaign(
            id = "test",
            title = "Test",
            premise = "Test premise",
            sessionPlan = "Test plan"
        )

        val jsonString = json.encodeToString(minimalCampaign)
        val deserialized = json.decodeFromString<Campaign>(jsonString)

        assertEquals("", deserialized.playerPersona)
        assertEquals("", deserialized.sceneState.location)
        assertTrue(deserialized.sceneState.presentNpcIds.isEmpty())
        assertNull(deserialized.thinkModel)
        assertNull(deserialized.writeModel)
    }
}
