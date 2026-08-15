package dev.diegesis.app.engine

import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.engine.memory.MemoryRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrieverTest {

    private fun mem(fact: String, turn: Int = 0) =
        MemoryEntry(scope = "campaign", fact = fact, turn = turn)

    /** Filler that shares no vocabulary with the queries used below. */
    private fun filler(n: Int) = (1..n).map { mem("zzz filler entry number $it") }

    // ---- the under-10 short circuit --------------------------------------

    @Test
    fun `returns everything when fewer than ten memories exist`() {
        // pipeline.md: skip retrieval below 10 entries — with a corpus that
        // small, ranking costs more than it buys.
        val memories = (1..9).map { mem("fact $it") }
        val result = MemoryRetriever.retrieve("anything at all", memories)
        assertEquals(9, result.size)
    }

    @Test
    fun `ranks once the corpus reaches ten`() {
        val memories = filler(9) + mem("the harbor gate is sealed at dusk")
        val result = MemoryRetriever.retrieve("harbor gate", memories)
        assertEquals(5, result.size)
        assertEquals("the harbor gate is sealed at dusk", result.first().fact)
    }

    // ---- term matching ----------------------------------------------------

    @Test
    fun `matching terms outrank non-matching ones`() {
        val memories = filler(12) + listOf(
            mem("Kestrel betrayed the guild"),
            mem("The lighthouse keeper is deaf"),
        )
        val result = MemoryRetriever.retrieve("what did Kestrel do to the guild", memories)
        assertEquals("Kestrel betrayed the guild", result.first().fact)
    }

    @Test
    fun `matching is case insensitive`() {
        val memories = filler(12) + mem("KESTREL owns the tavern")
        val result = MemoryRetriever.retrieve("kestrel", memories)
        assertEquals("KESTREL owns the tavern", result.first().fact)
    }

    @Test
    fun `punctuation does not block a match`() {
        val memories = filler(12) + mem("Kestrel's debt: unpaid.")
        val result = MemoryRetriever.retrieve("kestrel debt", memories)
        assertEquals("Kestrel's debt: unpaid.", result.first().fact)
    }

    @Test
    fun `more overlapping terms rank higher`() {
        val memories = filler(12) + listOf(
            mem("the silver key opens the vault door"),
            mem("the key is silver"),
            mem("a door creaks"),
        )
        val result = MemoryRetriever.retrieve("silver key vault door", memories)
        assertEquals("the silver key opens the vault door", result.first().fact)
    }

    @Test
    fun `single character tokens are ignored`() {
        // Stray one-letter tokens would otherwise match nearly everything.
        val memories = filler(12) + mem("a i o u brief noise")
        val result = MemoryRetriever.retrieve("a i o u", memories)
        // No meaningful terms in the query, so nothing should be pulled to the
        // top on the strength of single letters alone.
        assertEquals(5, result.size)
    }

    // ---- top-k ------------------------------------------------------------

    @Test
    fun `returns at most k results`() {
        val memories = (1..40).map { mem("shared term entry $it") }
        val result = MemoryRetriever.retrieve("shared term", memories)
        assertEquals(5, result.size)
    }

    @Test
    fun `k is configurable`() {
        val memories = (1..40).map { mem("shared term entry $it") }
        assertEquals(3, MemoryRetriever.retrieve("shared term", memories, k = 3).size)
        assertEquals(10, MemoryRetriever.retrieve("shared term", memories, k = 10).size)
    }

    @Test
    fun `empty corpus yields nothing`() {
        assertEquals(emptyList<MemoryEntry>(), MemoryRetriever.retrieve("query", emptyList()))
    }

    @Test
    fun `blank query still returns k results without crashing`() {
        val memories = (1..20).map { mem("entry $it") }
        val result = MemoryRetriever.retrieve("", memories)
        assertEquals(5, result.size)
    }

    // ---- deduplication ----------------------------------------------------

    @Test
    fun `exact duplicate facts are collapsed`() {
        // memories.jsonl is append-only, so the same fact can be extracted on
        // several turns. The scene prompt should carry it once.
        val memories = filler(12) + listOf(
            mem("Kestrel betrayed the guild", turn = 1),
            mem("Kestrel betrayed the guild", turn = 4),
            mem("Kestrel betrayed the guild", turn = 9),
        )
        val result = MemoryRetriever.retrieve("Kestrel guild betrayal", memories)
        assertEquals(1, result.count { it.fact == "Kestrel betrayed the guild" })
    }

    @Test
    fun `deduplication also applies below the ten entry threshold`() {
        val memories = listOf(
            mem("same fact", turn = 1),
            mem("same fact", turn = 2),
            mem("other fact", turn = 3),
        )
        val result = MemoryRetriever.retrieve("anything", memories)
        assertEquals(2, result.size)
    }

    @Test
    fun `near duplicates are kept as distinct facts`() {
        // Dedup is by exact text on purpose; differing wording may carry
        // different detail and is not ours to merge.
        val memories = filler(12) + listOf(
            mem("Kestrel betrayed the guild"),
            mem("Kestrel betrayed the guild in winter"),
        )
        val result = MemoryRetriever.retrieve("Kestrel betrayed guild", memories)
        assertEquals(2, result.count { it.fact.startsWith("Kestrel betrayed the guild") })
    }

    // ---- combined query surface ------------------------------------------

    @Test
    fun `query combining player input and synopsis matches on either half`() {
        // The orchestrator scores against playerInput + " " + synopsis.
        val memories = filler(12) + listOf(
            mem("the ferryman only takes silver"),
            mem("the abbot keeps a ledger of debts"),
        )
        val result = MemoryRetriever.retrieve("pay the ferryman the abbot watches", memories)
        val facts = result.map { it.fact }
        assertTrue(facts.contains("the ferryman only takes silver"))
        assertTrue(facts.contains("the abbot keeps a ledger of debts"))
    }
}
