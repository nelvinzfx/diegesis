package dev.diegesis.app.engine

import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.engine.assembler.VisibilityContextAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the core invariant from architecture.md: the scene stage may only see
 * narration from past turns where at least one currently present NPC was also
 * present. Everything else is off-screen and must not leak.
 */
class VisibilityContextAssemblerTest {

    private fun turn(
        index: Int,
        input: String,
        presentNpcIds: List<String>,
        sceneOutput: String = "scene-$index",
        synopsis: String = "synopsis-$index",
    ) = Turn(
        index = index,
        playerInput = input,
        variants = listOf(
            TurnVariant(
                id = "v$index",
                synopsis = synopsis,
                sceneOutput = sceneOutput,
                presentNpcIds = presentNpcIds,
            )
        )
    )

    private fun npc(id: String, name: String = "NPC $id") = Npc(
        id = id,
        name = name,
        description = "desc of $name",
        personality = "personality of $name",
        voiceExamples = listOf("A line from $name"),
        agency = NpcAgency(goal = "goal-$id", stance = "stance-$id", willActOn = "act-$id"),
        trackers = mapOf("trust" to 3),
    )

    private fun assemble(
        presentNpcIds: List<String>,
        allTurns: List<Turn>,
        presentNpcs: List<Npc> = presentNpcIds.map { npc(it) },
        synopsis: String = "fresh synopsis",
        memories: List<MemoryEntry> = emptyList(),
        playerInput: String = "current input",
    ) = VisibilityContextAssembler.assemble(
        synopsis = synopsis,
        mechanicResults = emptyList(),
        presentNpcIds = presentNpcIds,
        presentNpcs = presentNpcs,
        allTurns = allTurns,
        retrievedMemories = memories,
        playerInput = playerInput,
    )

    // ---- the exclusion half of the invariant -----------------------------

    @Test
    fun `turns where none of the present NPCs were present are excluded`() {
        val turns = listOf(
            turn(0, "talk to alice", listOf("alice"), sceneOutput = "ALICE_SCENE"),
            turn(1, "secret meeting with bob", listOf("bob"), sceneOutput = "BOB_SECRET"),
        )

        // Alice is present now. Bob's turn happened off-screen for her.
        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)

        val outputs = context.filteredHistory.map { it.sceneOutput }
        assertTrue("alice's own turn must survive", outputs.contains("ALICE_SCENE"))
        assertFalse("bob's off-screen turn leaked", outputs.contains("BOB_SECRET"))
        assertEquals(1, context.filteredHistory.size)
    }

    @Test
    fun `excluded turns leak neither prose nor player input into the prompt`() {
        // The filter must drop the whole turn, not just the narration: the
        // player's own phrasing can carry the secret too.
        val turns = listOf(
            turn(0, "PLAYER_SECRET_PLAN", listOf("bob"), sceneOutput = "BOB_SECRET"),
            turn(1, "greet alice", listOf("alice"), sceneOutput = "ALICE_SCENE"),
        )

        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)
        val prompt = VisibilityContextAssembler.formatPrompt(context)

        assertFalse(prompt.contains("BOB_SECRET"))
        assertFalse(prompt.contains("PLAYER_SECRET_PLAN"))
        assertTrue(prompt.contains("ALICE_SCENE"))
    }

    @Test
    fun `a turn with no NPCs present is invisible to any NPC scene`() {
        // Solo player turns are private: nobody witnessed them.
        val turns = listOf(
            turn(0, "brood alone", emptyList(), sceneOutput = "SOLO_SCENE"),
            turn(1, "meet alice", listOf("alice"), sceneOutput = "ALICE_SCENE"),
        )

        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)
        val outputs = context.filteredHistory.map { it.sceneOutput }

        assertFalse("solo turn leaked into an NPC scene", outputs.contains("SOLO_SCENE"))
        assertTrue(outputs.contains("ALICE_SCENE"))
    }

    @Test
    fun `turns with no variants are excluded`() {
        // A turn that never produced a variant has no presentNpcIds to match on;
        // treating it as visible would be an unprovable assumption.
        val turns = listOf(
            Turn(index = 0, playerInput = "unfinished", variants = emptyList()),
            turn(1, "meet alice", listOf("alice"), sceneOutput = "ALICE_SCENE"),
        )

        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)
        assertEquals(1, context.filteredHistory.size)
        assertEquals("ALICE_SCENE", context.filteredHistory.first().sceneOutput)
    }

    // ---- the retention half of the invariant ------------------------------

    @Test
    fun `turns where a present NPC was present are retained`() {
        val turns = listOf(
            turn(0, "first", listOf("alice"), sceneOutput = "S0"),
            turn(1, "second", listOf("alice"), sceneOutput = "S1"),
            turn(2, "third", listOf("alice"), sceneOutput = "S2"),
        )

        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)

        assertEquals(listOf("S0", "S1", "S2"), context.filteredHistory.map { it.sceneOutput })
    }

    @Test
    fun `partial overlap is enough to make a turn visible`() {
        // Bob was there when Alice and Bob were both present, so that turn is
        // fair game for a Bob-only scene.
        val turns = listOf(
            turn(0, "both here", listOf("alice", "bob"), sceneOutput = "SHARED"),
            turn(1, "alice alone", listOf("alice"), sceneOutput = "ALICE_ONLY"),
        )

        val context = assemble(presentNpcIds = listOf("bob"), allTurns = turns)
        val outputs = context.filteredHistory.map { it.sceneOutput }

        assertTrue(outputs.contains("SHARED"))
        assertFalse(outputs.contains("ALICE_ONLY"))
    }

    @Test
    fun `any one of several present NPCs can admit a turn`() {
        val turns = listOf(
            turn(0, "alice scene", listOf("alice"), sceneOutput = "A"),
            turn(1, "bob scene", listOf("bob"), sceneOutput = "B"),
            turn(2, "carol scene", listOf("carol"), sceneOutput = "C"),
        )

        val context = assemble(presentNpcIds = listOf("alice", "bob"), allTurns = turns)
        val outputs = context.filteredHistory.map { it.sceneOutput }

        assertTrue(outputs.contains("A"))
        assertTrue(outputs.contains("B"))
        assertFalse("carol was never in the room", outputs.contains("C"))
    }

    @Test
    fun `visibility is judged on the latest variant of a past turn`() {
        // Swipe/regenerate rewrites who was present. The newest variant is the
        // canonical branch, so the filter must read it, not variant[0].
        val turnWithReroll = Turn(
            index = 0,
            playerInput = "rerolled turn",
            variants = listOf(
                TurnVariant(id = "old", synopsis = "s", sceneOutput = "OLD", presentNpcIds = listOf("bob")),
                TurnVariant(id = "new", synopsis = "s", sceneOutput = "NEW", presentNpcIds = listOf("alice")),
            )
        )

        val context = assemble(presentNpcIds = listOf("alice"), allTurns = listOf(turnWithReroll))

        assertEquals(1, context.filteredHistory.size)
        assertEquals("NEW", context.filteredHistory.first().sceneOutput)
    }

    // ---- solo scenes ------------------------------------------------------

    @Test
    fun `empty present NPCs means a solo scene that sees all turns`() {
        val turns = listOf(
            turn(0, "a", listOf("alice"), sceneOutput = "A"),
            turn(1, "b", listOf("bob"), sceneOutput = "B"),
            turn(2, "c", emptyList(), sceneOutput = "C"),
        )

        val context = assemble(presentNpcIds = emptyList(), allTurns = turns, presentNpcs = emptyList())

        assertEquals(3, context.filteredHistory.size)
        assertEquals(listOf("A", "B", "C"), context.filteredHistory.map { it.sceneOutput })
    }

    @Test
    fun `empty history assembles cleanly`() {
        val context = assemble(presentNpcIds = listOf("alice"), allTurns = emptyList())
        assertTrue(context.filteredHistory.isEmpty())
    }

    // ---- payload assembly order and content -------------------------------

    @Test
    fun `synopsis is carried verbatim`() {
        val context = assemble(
            presentNpcIds = emptyList(),
            allTurns = emptyList(),
            presentNpcs = emptyList(),
            synopsis = "The bridge groans and begins to tilt.",
        )
        assertEquals("The bridge groans and begins to tilt.", context.synopsis)
        assertTrue(
            VisibilityContextAssembler.formatPrompt(context)
                .contains("The bridge groans and begins to tilt.")
        )
    }

    @Test
    fun `present NPC payload carries sheet agency and trackers`() {
        val context = assemble(presentNpcIds = listOf("alice"), allTurns = emptyList())
        val payload = context.presentNpcs.single()

        assertEquals("alice", payload.id)
        assertEquals("desc of NPC alice", payload.description)
        assertEquals("personality of NPC alice", payload.personality)
        assertEquals(listOf("A line from NPC alice"), payload.voiceExamples)
        assertTrue(payload.agency.contains("goal-alice"))
        assertTrue(payload.agency.contains("stance-alice"))
        assertEquals(mapOf("trust" to 3), payload.trackers)
    }

    @Test
    fun `prompt sections appear in the documented order`() {
        // pipeline.md section 5: synopsis, mechanics, NPCs, history, memories,
        // player input. Order is part of the contract.
        val turns = listOf(turn(0, "past input", listOf("alice"), sceneOutput = "PAST_SCENE"))
        val context = assemble(
            presentNpcIds = listOf("alice"),
            allTurns = turns,
            memories = listOf(MemoryEntry(scope = "campaign", fact = "REMEMBERED_FACT", turn = 0)),
            playerInput = "CURRENT_INPUT",
        )

        val prompt = VisibilityContextAssembler.formatPrompt(context)
        val synopsisAt = prompt.indexOf("fresh synopsis")
        val npcAt = prompt.indexOf("Present NPCs")
        val historyAt = prompt.indexOf("PAST_SCENE")
        val memoryAt = prompt.indexOf("REMEMBERED_FACT")
        val inputAt = prompt.indexOf("CURRENT_INPUT")

        assertTrue(synopsisAt >= 0 && npcAt > synopsisAt)
        assertTrue(historyAt > npcAt)
        assertTrue(memoryAt > historyAt)
        assertTrue(inputAt > memoryAt)
    }

    @Test
    fun `history preserves chronological order`() {
        val turns = listOf(
            turn(0, "first", listOf("alice"), sceneOutput = "S0"),
            turn(1, "second", listOf("alice"), sceneOutput = "S1"),
        )
        val context = assemble(presentNpcIds = listOf("alice"), allTurns = turns)
        val prompt = VisibilityContextAssembler.formatPrompt(context)

        assertTrue(prompt.indexOf("S0") < prompt.indexOf("S1"))
    }
}
