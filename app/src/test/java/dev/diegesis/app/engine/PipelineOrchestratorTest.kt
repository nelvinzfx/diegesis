package dev.diegesis.app.engine

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class PipelineOrchestratorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var campaigns: CampaignStorage
    private lateinit var npcs: NpcStorage
    private lateinit var turns: TurnStorage
    private lateinit var memories: MemoryStorage

    private val campaignId = "camp-1"

    /**
     * Scripted AiCaller. Structured stages are answered by matching the system
     * prompt to a canned JSON string; prose is emitted as fixed chunks so the
     * streaming callback can be observed.
     */
    private class FakeAiCaller(
        var routerJson: String = """{"needs_check":false,"checks":[],"run_agency_update":false,"lore_query":null}""",
        var plotJson: String = """{"synopsis":"Something happens.","present_npcs":[],"scene_change":false,"location":null,"tracker_updates":[]}""",
        var agencyJson: String = """{"goal":"g","stance":"s","will_act_on":"w"}""",
        var extractionJson: String = """[]""",
        var proseChunks: List<String> = listOf("Once ", "upon ", "a time."),
        var proseThrows: Boolean = false,
    ) : AiCaller {
        val structuredCalls = mutableListOf<String>()
        var proseCalls = 0
        var lastScenePrompt: String? = null

        override suspend fun <T> generateStructured(
            systemPrompt: String,
            userPrompt: String,
            decoder: (String) -> T,
            fallback: T,
        ): T {
            val stage = when {
                systemPrompt.contains("router", ignoreCase = true) -> "router"
                systemPrompt.contains("plot engine", ignoreCase = true) -> "plot"
                systemPrompt.contains("inner life", ignoreCase = true) -> "agency"
                systemPrompt.contains("Extract durable facts", ignoreCase = true) -> "extraction"
                else -> "unknown"
            }
            structuredCalls += stage

            val payload = when (stage) {
                "router" -> routerJson
                "plot" -> plotJson
                "agency" -> agencyJson
                "extraction" -> extractionJson
                else -> return fallback
            }
            // Mirror the real caller's contract: never throw, fall back instead.
            return runCatching { decoder(payload) }.getOrDefault(fallback)
        }

        override suspend fun streamProse(systemPrompt: String, userPrompt: String): Flow<String> {
            proseCalls++
            lastScenePrompt = userPrompt
            if (proseThrows) throw RuntimeException("scene model exploded")
            return proseChunks.asFlow()
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
                title = "Test Campaign",
                premise = "A premise",
                sessionPlan = "Act 1: arrive. Act 2: betrayal.",
                sceneState = SceneState(location = "The Docks", presentNpcIds = listOf("alice")),
            )
        )
        npcs.save(campaignId, npcOf("alice", trust = 2))
        npcs.save(campaignId, npcOf("bob", trust = 0))
    }

    private fun npcOf(id: String, trust: Int) = Npc(
        id = id,
        name = "NPC $id",
        description = "desc $id",
        personality = "personality $id",
        voiceExamples = listOf("line $id"),
        agency = NpcAgency(goal = "old-goal-$id", stance = "old-stance-$id", willActOn = "old-act-$id"),
        trackers = mapOf("trust" to trust),
    )

    private fun orchestrator(fake: FakeAiCaller, seed: Int = 7) = PipelineOrchestrator(
        aiCaller = fake,
        campaignStorage = campaigns,
        npcStorage = npcs,
        turnStorage = turns,
        memoryStorage = memories,
        random = Random(seed),
    )

    private fun orchestratorWithWindow(
        fake: FakeAiCaller,
        contextWindowTokens: Int,
        writeMaxTokens: Int,
    ) = PipelineOrchestrator(
        aiCaller = fake,
        campaignStorage = campaigns,
        npcStorage = npcs,
        turnStorage = turns,
        memoryStorage = memories,
        random = Random(7),
        contextWindowTokens = contextWindowTokens,
        writeMaxTokens = writeMaxTokens,
    )

    // ---- happy path -------------------------------------------------------

    @Test
    fun `full turn streams prose saves the turn and returns the variant`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"The rope snaps.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}""",
            proseChunks = listOf("You ", "fall."),
        )

        val chunks = mutableListOf<String>()
        val variant = orchestrator(fake).executeTurn(campaignId, "grab the rope") { chunks += it }

        assertEquals(listOf("You ", "fall."), chunks)
        assertEquals("You fall.", variant.sceneOutput)
        assertEquals("The rope snaps.", variant.synopsis)
        assertFalse(variant.interrupted)

        val saved = turns.loadTurn(campaignId, 0)
        assertNotNull(saved)
        assertEquals("grab the rope", saved!!.playerInput)
        assertEquals(1, saved.variants.size)
        assertEquals("You fall.", saved.variants.first().sceneOutput)
    }

    @Test
    fun `stages run in pipeline order`() {
        val fake = FakeAiCaller(
            routerJson = """{"needs_check":false,"checks":[],"run_agency_update":false,"lore_query":null}"""
        )
        runBlocking { orchestrator(fake).executeTurn(campaignId, "look around") {} }

        // Agency is conditional and off here; router precedes plot precedes extraction.
        assertEquals(listOf("router", "plot", "extraction"), fake.structuredCalls)
    }

    @Test
    fun `turn indices increment across successive turns`() = runBlocking {
        val fake = FakeAiCaller()
        val orch = orchestrator(fake)
        orch.executeTurn(campaignId, "first") {}
        orch.executeTurn(campaignId, "second") {}

        assertEquals(listOf(0, 1), turns.listTurnIndices(campaignId))
        assertEquals("second", turns.loadTurn(campaignId, 1)!!.playerInput)
    }

    // ---- mechanics wiring -------------------------------------------------

    @Test
    fun `router requesting a check produces a mechanic result on the variant`() = runBlocking {
        val fake = FakeAiCaller(
            routerJson = """{"needs_check":true,"checks":[{"skill":"athletics","dc":10,"modifier":0,"advantage":0}],"run_agency_update":false,"lore_query":null}"""
        )

        val variant = orchestrator(fake).executeTurn(campaignId, "leap the gap") {}

        assertEquals(1, variant.mechanicResults.size)
        val result = variant.mechanicResults.first()
        assertEquals("athletics", result.skill)
        assertEquals(10, result.dc)
        assertTrue(result.tier in setOf("critical_success", "success", "partial", "failure"))
    }

    @Test
    fun `mechanic outcomes reach the scene prompt`() = runBlocking {
        val fake = FakeAiCaller(
            routerJson = """{"needs_check":true,"checks":[{"skill":"athletics","dc":10,"modifier":0,"advantage":0}],"run_agency_update":false,"lore_query":null}"""
        )
        orchestrator(fake).executeTurn(campaignId, "leap the gap") {}

        assertTrue(fake.lastScenePrompt!!.contains("athletics"))
    }

    @Test
    fun `no check means no mechanic results`() = runBlocking {
        val fake = FakeAiCaller()
        val variant = orchestrator(fake).executeTurn(campaignId, "sit quietly") {}
        assertTrue(variant.mechanicResults.isEmpty())
    }

    // ---- state updates ----------------------------------------------------

    @Test
    fun `tracker deltas are applied to the stored NPC`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"She recoils.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[{"npc":"alice","key":"trust","delta":-3}]}"""
        )

        orchestrator(fake).executeTurn(campaignId, "insult alice") {}

        // started at 2, delta -3
        assertEquals(-1, npcs.load(campaignId, "alice")!!.trackers["trust"])
    }

    @Test
    fun `tracker update on an unseen key starts from zero`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Coins change hands.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[{"npc":"alice","key":"coin","delta":7}]}"""
        )
        orchestrator(fake).executeTurn(campaignId, "pay alice") {}
        assertEquals(7, npcs.load(campaignId, "alice")!!.trackers["coin"])
    }

    @Test
    fun `scene change updates location and present NPCs`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"You arrive.","present_npcs":["bob"],"scene_change":true,"location":"The Chapel","tracker_updates":[]}"""
        )

        orchestrator(fake).executeTurn(campaignId, "go to the chapel") {}

        val state = campaigns.load(campaignId)!!.sceneState
        assertEquals("The Chapel", state.location)
        assertEquals(listOf("bob"), state.presentNpcIds)
    }

    @Test
    fun `null location keeps the previous location`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Still here.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}"""
        )
        orchestrator(fake).executeTurn(campaignId, "wait") {}
        assertEquals("The Docks", campaigns.load(campaignId)!!.sceneState.location)
    }

    // ---- memory -----------------------------------------------------------

    @Test
    fun `extracted memories are appended to storage`() = runBlocking {
        val fake = FakeAiCaller(
            extractionJson = """[{"scope":"campaign","npc_id":null,"fact":"The bridge is out."},{"scope":"npc","npc_id":"alice","fact":"Alice fears water."}]"""
        )

        orchestrator(fake).executeTurn(campaignId, "cross the bridge") {}

        val stored = memories.loadMemories(campaignId)
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.fact == "The bridge is out." && it.scope == "campaign" })
        assertTrue(stored.any { it.fact == "Alice fears water." && it.npc_id == "alice" })
        assertTrue(stored.all { it.turn == 0 })
    }

    @Test
    fun `empty extraction appends nothing`() = runBlocking {
        val fake = FakeAiCaller(extractionJson = "[]")
        orchestrator(fake).executeTurn(campaignId, "breathe") {}
        assertTrue(memories.loadMemories(campaignId).isEmpty())
    }

    // ---- agency -----------------------------------------------------------

    @Test
    fun `agency runs when the router asks for it`() = runBlocking {
        val fake = FakeAiCaller(
            routerJson = """{"needs_check":false,"checks":[],"run_agency_update":true,"lore_query":null}""",
            plotJson = """{"synopsis":"A shift.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}""",
            agencyJson = """{"goal":"new-goal","stance":"new-stance","will_act_on":"new-act"}""",
        )

        orchestrator(fake).executeTurn(campaignId, "confess") {}

        val alice = npcs.load(campaignId, "alice")!!
        assertEquals("new-goal", alice.agency.goal)
        assertEquals("new-stance", alice.agency.stance)
        assertEquals("new-act", alice.agency.willActOn)
        assertTrue(fake.structuredCalls.contains("agency"))
    }

    @Test
    fun `agency is skipped on a quiet turn`() = runBlocking {
        val fake = FakeAiCaller()
        orchestrator(fake).executeTurn(campaignId, "nod") {}

        assertFalse(fake.structuredCalls.contains("agency"))
        assertEquals("old-goal-alice", npcs.load(campaignId, "alice")!!.agency.goal)
    }

    // ---- visibility, end to end -------------------------------------------

    @Test
    fun `scene prompt excludes turns the present NPC did not witness`() = runBlocking {
        // Seed a past turn witnessed only by bob, then run a turn with alice present.
        turns.saveTurn(
            campaignId,
            Turn(
                index = 0,
                playerInput = "PLOT_WITH_BOB",
                variants = listOf(
                    TurnVariant(
                        id = "v0",
                        synopsis = "bob synopsis",
                        sceneOutput = "BOB_ONLY_SCENE",
                        presentNpcIds = listOf("bob"),
                    )
                ),
            )
        )

        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Alice turns.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}"""
        )
        orchestrator(fake).executeTurn(campaignId, "ask alice") {}

        val prompt = fake.lastScenePrompt!!
        assertFalse("off-screen scene leaked", prompt.contains("BOB_ONLY_SCENE"))
        assertFalse("off-screen input leaked", prompt.contains("PLOT_WITH_BOB"))
    }

    @Test
    fun `scene prompt includes turns the present NPC witnessed`() = runBlocking {
        turns.saveTurn(
            campaignId,
            Turn(
                index = 0,
                playerInput = "earlier",
                variants = listOf(
                    TurnVariant(
                        id = "v0",
                        synopsis = "alice synopsis",
                        sceneOutput = "ALICE_WITNESSED_SCENE",
                        presentNpcIds = listOf("alice"),
                    )
                ),
            )
        )

        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Alice speaks.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}"""
        )
        orchestrator(fake).executeTurn(campaignId, "ask alice again") {}

        assertTrue(fake.lastScenePrompt!!.contains("ALICE_WITNESSED_SCENE"))
    }

    // ---- resilience -------------------------------------------------------

    @Test
    fun `malformed plot JSON falls back without crashing the turn`() = runBlocking {
        val fake = FakeAiCaller(plotJson = "this is not json at all")

        val variant = orchestrator(fake).executeTurn(campaignId, "do something") {}

        assertEquals("The moment stretches; the situation stays tense.", variant.synopsis)
        // The turn still completes and persists.
        assertNotNull(turns.loadTurn(campaignId, 0))
    }

    // ---- stage events (pipeline transparency) -------------------------------

    @Test
    fun `malformed plot JSON records a fallback stage event on the variant`() = runBlocking {
        val fake = FakeAiCaller(plotJson = "this is not json at all")

        val variant = orchestrator(fake).executeTurn(campaignId, "do something") {}

        assertTrue(
            "expected a plot fallback event, got ${variant.stageEvents}",
            variant.stageEvents.any { it.startsWith("plot: fallback used") }
        )
        // The event survives the round-trip to disk.
        val saved = turns.loadTurn(campaignId, 0)!!.variants.first()
        assertTrue(saved.stageEvents.any { it.startsWith("plot: fallback used") })
    }

    @Test
    fun `a clean turn has an empty stage event list`() = runBlocking {
        val fake = FakeAiCaller()

        val variant = orchestrator(fake).executeTurn(campaignId, "look around") {}

        assertTrue(
            "clean turn must record no events, got ${variant.stageEvents}",
            variant.stageEvents.isEmpty()
        )
    }

    @Test
    fun `scene failure records an interrupted stage event`() = runBlocking {
        val fake = FakeAiCaller(proseThrows = true)

        val variant = orchestrator(fake).executeTurn(campaignId, "provoke") {}

        assertTrue(variant.interrupted)
        assertTrue(
            variant.stageEvents.any {
                it.startsWith("scene: interrupted") && it.contains("scene model exploded")
            }
        )
    }

    @Test
    fun `applied tracker update records a stage event`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"She recoils.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[{"npc":"alice","key":"trust","delta":-3}]}"""
        )

        val variant = orchestrator(fake).executeTurn(campaignId, "insult alice") {}

        assertTrue(variant.stageEvents.any { it == "tracker: trust -3 applied to alice" })
        // Agency runs because a tracker update happened; that is recorded too.
        assertTrue(variant.stageEvents.any { it.startsWith("agency: run") })
    }

    @Test
    fun `old turn files without stageEvents still deserialize`() {
        val turnsDir = File(filesDir, "campaigns/$campaignId/turns")
        turnsDir.mkdirs()
        // Pre-phase-6 turn file: no stageEvents key anywhere.
        File(turnsDir, "0.json").writeText(
            """
            {
              "index": 0,
              "playerInput": "legacy input",
              "variants": [
                {
                  "id": "v-legacy",
                  "synopsis": "legacy synopsis",
                  "sceneOutput": "legacy prose",
                  "interrupted": false,
                  "timestamp": 1000
                }
              ],
              "createdAt": 1000
            }
            """.trimIndent()
        )

        val loaded = turns.loadTurn(campaignId, 0)
        assertNotNull(loaded)
        assertEquals("legacy input", loaded!!.playerInput)
        assertTrue(loaded.variants.first().stageEvents.isEmpty())
    }

    // ---- context window enforcement (phase 7) ------------------------------

    @Test
    fun `oversized history is trimmed and records a stage event`() = runBlocking {
        // Budget = (1024 - 512) * 0.8 = 409 tokens = 1636 chars. Three past
        // turns of ~4000 chars each: only the newest survives.
        (0..2).forEach { i ->
            turns.saveTurn(
                campaignId,
                Turn(
                    index = i,
                    playerInput = "TURN_${i}_INPUT",
                    variants = listOf(
                        TurnVariant(
                            id = "v$i",
                            synopsis = "s$i",
                            sceneOutput = "TURN_${i}_SCENE " + "x".repeat(4000),
                            presentNpcIds = listOf("alice"),
                        )
                    ),
                )
            )
        }

        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Continues.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}"""
        )
        val variant = orchestratorWithWindow(fake, contextWindowTokens = 1024, writeMaxTokens = 512)
            .executeTurn(campaignId, "press on") {}

        assertTrue(
            "expected a context trim event, got ${variant.stageEvents}",
            variant.stageEvents.any { it.startsWith("context: history trimmed to last 1 turns") }
        )
        // The newest turn stays in the prompt; the oldest is gone.
        val prompt = fake.lastScenePrompt!!
        assertTrue("newest turn missing", prompt.contains("TURN_2_SCENE"))
        assertFalse("oldest turn leaked past the trim", prompt.contains("TURN_0_SCENE"))
    }

    @Test
    fun `history under the budget is not trimmed and records no event`() = runBlocking {
        turns.saveTurn(
            campaignId,
            Turn(
                index = 0,
                playerInput = "small",
                variants = listOf(
                    TurnVariant(
                        id = "v0",
                        synopsis = "s0",
                        sceneOutput = "SMALL_SCENE",
                        presentNpcIds = listOf("alice"),
                    )
                ),
            )
        )

        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"Continues.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[]}"""
        )
        val variant = orchestrator(fake).executeTurn(campaignId, "press on") {}

        assertTrue(variant.stageEvents.none { it.startsWith("context:") })
        assertTrue(fake.lastScenePrompt!!.contains("SMALL_SCENE"))
    }

    @Test
    fun `malformed router JSON falls back to no check`() = runBlocking {
        val fake = FakeAiCaller(routerJson = "{{{garbage")
        val variant = orchestrator(fake).executeTurn(campaignId, "swing wildly") {}
        assertTrue(variant.mechanicResults.isEmpty())
        assertNotNull(turns.loadTurn(campaignId, 0))
    }

    @Test
    fun `plot fallback keeps the previous scene state`() = runBlocking {
        val fake = FakeAiCaller(plotJson = "not json")
        orchestrator(fake).executeTurn(campaignId, "hesitate") {}

        val state = campaigns.load(campaignId)!!.sceneState
        assertEquals("The Docks", state.location)
        assertEquals(listOf("alice"), state.presentNpcIds)
    }

    @Test
    fun `a failing scene stage marks the variant interrupted but still saves`() = runBlocking {
        val fake = FakeAiCaller(proseThrows = true)

        val variant = orchestrator(fake).executeTurn(campaignId, "provoke the storm") {}

        assertTrue(variant.interrupted)
        assertEquals("", variant.sceneOutput)
        assertNotNull("turn must persist even when the scene fails", turns.loadTurn(campaignId, 0))
    }

    @Test
    fun `empty prose is treated as interrupted`() = runBlocking {
        val fake = FakeAiCaller(proseChunks = emptyList())
        val variant = orchestrator(fake).executeTurn(campaignId, "stare") {}
        assertTrue(variant.interrupted)
    }

    // ---- pipeline events callback (live progress) -------------------------

    @Test
    fun `onPipelineEvent receives stage boundary events on happy path`() = runBlocking {
        val fake = FakeAiCaller()
        val events = mutableListOf<String>()
        val orch = PipelineOrchestrator(
            aiCaller = fake,
            campaignStorage = campaigns,
            npcStorage = npcs,
            turnStorage = turns,
            memoryStorage = memories,
            random = Random(7),
            onPipelineEvent = { events += it }
        )

        orch.executeTurn(campaignId, "look around") {}

        assertEquals(
            listOf(
                "router: deciding checks…",
                "router: done",
                "plot: generating turn plan…",
                "plot: done",
                "scene: streaming…",
                "memory: extracting…",
                "memory: done",
            ),
            events
        )
    }

    @Test
    fun `onPipelineEvent null is safe`() = runBlocking {
        val fake = FakeAiCaller()
        val orch = PipelineOrchestrator(
            aiCaller = fake,
            campaignStorage = campaigns,
            npcStorage = npcs,
            turnStorage = turns,
            memoryStorage = memories,
            random = Random(7),
            onPipelineEvent = null
        )
        val variant = orch.executeTurn(campaignId, "test") {}
        assertFalse(variant.interrupted)
    }

    @Test
    fun `onPipelineEvent receives fallback events`() = runBlocking {
        val fake = FakeAiCaller(plotJson = "garbage")
        val events = mutableListOf<String>()
        val orch = PipelineOrchestrator(
            aiCaller = fake,
            campaignStorage = campaigns,
            npcStorage = npcs,
            turnStorage = turns,
            memoryStorage = memories,
            random = Random(7),
            onPipelineEvent = { events += it }
        )

        val variant = orch.executeTurn(campaignId, "test") {}

        assertTrue(
            "expected plot fallback in callback",
            events.any { it.startsWith("plot: fallback used") }
        )
        // Every persisted stageEvents line was also emitted live, in order:
        // the live log and the Stage Details log read identically.
        assertEquals(
            variant.stageEvents,
            events.filter { it in variant.stageEvents }
        )
    }

    @Test
    fun `malformed extraction JSON does not fail the turn`() = runBlocking {
        val fake = FakeAiCaller(extractionJson = "nope")
        val variant = orchestrator(fake).executeTurn(campaignId, "remember this") {}

        assertFalse(variant.interrupted)
        assertTrue(memories.loadMemories(campaignId).isEmpty())
    }

    @Test
    fun `tracker update naming an unknown NPC is ignored`() = runBlocking {
        val fake = FakeAiCaller(
            plotJson = """{"synopsis":"A ghost stirs.","present_npcs":["alice"],"scene_change":false,"location":null,"tracker_updates":[{"npc":"nobody","key":"trust","delta":5}]}"""
        )

        val variant = orchestrator(fake).executeTurn(campaignId, "address the void") {}

        assertFalse(variant.interrupted)
        assertEquals(2, npcs.load(campaignId, "alice")!!.trackers["trust"])
    }
}
