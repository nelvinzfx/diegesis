package dev.diegesis.app.engine

import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import dev.diegesis.app.engine.assembler.ContextWindowTrimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTrimmerTest {

    private fun turnOf(index: Int, input: String, output: String) = Turn(
        index = index,
        playerInput = input,
        variants = listOf(
            TurnVariant(
                id = "v$index",
                synopsis = "synopsis $index",
                sceneOutput = output,
            )
        ),
    )

    /** A turn whose input+output totals exactly [chars] characters. */
    private fun turnOfSize(index: Int, chars: Int): Turn {
        val half = chars / 2
        return turnOf(index, "a".repeat(half), "b".repeat(chars - half))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<Turn>(), ContextWindowTrimmer.trimToFit(emptyList(), 1000))
    }

    @Test
    fun `zero or negative budget yields empty output`() {
        val turns = listOf(turnOfSize(0, 100))
        assertEquals(emptyList<Turn>(), ContextWindowTrimmer.trimToFit(turns, 0))
        assertEquals(emptyList<Turn>(), ContextWindowTrimmer.trimToFit(turns, -5))
    }

    @Test
    fun `everything fits when under budget`() {
        // 3 turns x 400 chars = 1200 chars = 300 tokens; budget 1000 tokens.
        val turns = (0..2).map { turnOfSize(it, 400) }
        assertEquals(turns, ContextWindowTrimmer.trimToFit(turns, 1000))
    }

    @Test
    fun `oldest turns are dropped first and newest kept`() {
        // Each turn is 4000 chars = 1000 tokens. Budget 2000 tokens fits 2.
        val turns = (0..4).map { turnOfSize(it, 4000) }

        val trimmed = ContextWindowTrimmer.trimToFit(turns, 2000)

        assertEquals(2, trimmed.size)
        assertEquals(listOf(3, 4), trimmed.map { it.index })
    }

    @Test
    fun `result stays in chronological order`() {
        val turns = (0..9).map { turnOfSize(it, 2000) } // 500 tokens each

        val trimmed = ContextWindowTrimmer.trimToFit(turns, 1600) // fits 3

        assertEquals(trimmed.map { it.index }, trimmed.map { it.index }.sorted())
        assertEquals(listOf(7, 8, 9), trimmed.map { it.index })
    }

    @Test
    fun `budget is respected by estimated size`() {
        val turns = (0..9).map { turnOfSize(it, 4000) } // 1000 tokens each
        val budget = 3500

        val trimmed = ContextWindowTrimmer.trimToFit(turns, budget)

        val estimatedTokens = trimmed.sumOf { turn ->
            (turn.playerInput.length + (turn.variants.last().sceneOutput.length)) / 4
        }
        assertTrue(
            "estimated $estimatedTokens tokens exceeds budget $budget",
            estimatedTokens <= budget
        )
        assertEquals(3, trimmed.size)
    }

    @Test
    fun `a single oversized newest turn is still kept`() {
        // The newest turn alone exceeds the budget; dropping everything would
        // starve the scene stage, so at least one turn is always kept.
        val turns = listOf(turnOfSize(0, 400), turnOfSize(1, 40000))

        val trimmed = ContextWindowTrimmer.trimToFit(turns, 100)

        assertEquals(1, trimmed.size)
        assertEquals(1, trimmed.first().index)
    }

    @Test
    fun `turn without variants counts only its input`() {
        val bare = Turn(index = 0, playerInput = "x".repeat(400), variants = emptyList())
        val newest = turnOfSize(1, 400)

        // Budget of 250 tokens = 1000 chars fits both (400 + 400 chars).
        val trimmed = ContextWindowTrimmer.trimToFit(listOf(bare, newest), 250)

        assertEquals(2, trimmed.size)
    }
}
