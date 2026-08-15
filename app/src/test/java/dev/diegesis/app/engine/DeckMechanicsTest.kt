package dev.diegesis.app.engine

import dev.diegesis.app.data.model.MechanicCheck
import dev.diegesis.app.engine.mechanics.DeckMechanics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DeckMechanicsTest {

    private val suits = setOf("hearts", "diamonds", "clubs", "spades")

    // ---- deck shape ------------------------------------------------------

    @Test
    fun `drawn ranks always fall in 2 to 14`() {
        // Many draws across many seeds: no rank may escape the 2..14 window
        // (no aces-low, no jokers, no off-by-one on the exclusive bound).
        repeat(500) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "athletics", dc = 10),
                Random(seed)
            )
            result.drawn.forEach { card ->
                assertTrue("rank ${card.rank} out of range", card.rank in 2..14)
            }
        }
    }

    @Test
    fun `drawn suits are always one of the four standard suits`() {
        repeat(200) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "insight", dc = 8),
                Random(seed)
            )
            result.drawn.forEach { card ->
                assertTrue("bad suit ${card.suit}", card.suit in suits)
            }
        }
    }

    @Test
    fun `face cards map to the documented values`() {
        // pipeline.md: J=11, Q=12, K=13, A=14. Verify via the generated names
        // so the mapping is checked at its user-visible surface.
        val names = mutableMapOf<Int, String>()
        repeat(2000) { seed ->
            DeckMechanics.executeCheck(MechanicCheck("x", dc = 10), Random(seed))
                .drawn.forEach { names[it.rank] = it.name }
        }
        assertTrue(names[11]!!.startsWith("Jack"))
        assertTrue(names[12]!!.startsWith("Queen"))
        assertTrue(names[13]!!.startsWith("King"))
        assertTrue(names[14]!!.startsWith("Ace"))
        assertTrue(names[10]!!.startsWith("10"))
        assertTrue(names[2]!!.startsWith("2"))
    }

    @Test
    fun `all thirteen ranks are reachable`() {
        val seen = mutableSetOf<Int>()
        repeat(3000) { seed ->
            DeckMechanics.executeCheck(MechanicCheck("x", dc = 10), Random(seed))
                .drawn.forEach { seen.add(it.rank) }
        }
        assertEquals((2..14).toSet(), seen)
    }

    // ---- draw counts -----------------------------------------------------

    @Test
    fun `advantage zero draws exactly one card`() {
        val result = DeckMechanics.executeCheck(
            MechanicCheck(skill = "stealth", dc = 10, advantage = 0),
            Random(1)
        )
        assertEquals(1, result.drawn.size)
    }

    @Test
    fun `advantage draws two cards`() {
        val result = DeckMechanics.executeCheck(
            MechanicCheck(skill = "stealth", dc = 10, advantage = 1),
            Random(1)
        )
        assertEquals(2, result.drawn.size)
    }

    @Test
    fun `disadvantage draws two cards`() {
        val result = DeckMechanics.executeCheck(
            MechanicCheck(skill = "stealth", dc = 10, advantage = -1),
            Random(1)
        )
        assertEquals(2, result.drawn.size)
    }

    // ---- advantage / disadvantage selection ------------------------------

    @Test
    fun `advantage takes the higher of the two draws`() {
        repeat(200) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "athletics", dc = 10, advantage = 1),
                Random(seed)
            )
            assertEquals(result.drawn.maxOf { it.rank }, result.value)
        }
    }

    @Test
    fun `disadvantage takes the lower of the two draws`() {
        repeat(200) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "athletics", dc = 10, advantage = -1),
                Random(seed)
            )
            assertEquals(result.drawn.minOf { it.rank }, result.value)
        }
    }

    @Test
    fun `advantage and disadvantage differ when the two draws differ`() {
        // Same seed => same two cards. Advantage must be >= disadvantage, and
        // strictly greater at least once across the sample (proves the two
        // branches are not silently identical).
        var sawStrictDifference = false
        repeat(200) { seed ->
            val adv = DeckMechanics.executeCheck(
                MechanicCheck("x", dc = 10, advantage = 1), Random(seed)
            ).value
            val dis = DeckMechanics.executeCheck(
                MechanicCheck("x", dc = 10, advantage = -1), Random(seed)
            ).value
            assertTrue("adv $adv < dis $dis", adv >= dis)
            if (adv > dis) sawStrictDifference = true
        }
        assertTrue("advantage never beat disadvantage", sawStrictDifference)
    }

    // ---- modifier --------------------------------------------------------

    @Test
    fun `value is card rank plus modifier`() {
        repeat(100) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "arcana", dc = 10, modifier = 3),
                Random(seed)
            )
            assertEquals(result.drawn.first().rank + 3, result.value)
        }
    }

    @Test
    fun `negative modifier lowers the value`() {
        repeat(100) { seed ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck(skill = "arcana", dc = 10, modifier = -4),
                Random(seed)
            )
            assertEquals(result.drawn.first().rank - 4, result.value)
        }
    }

    // ---- tier boundaries (the whole point) -------------------------------
    //
    // A DC-10 check with advantage=0 and a chosen modifier lets us pin `value`
    // exactly: modifier = target - rank. Sweep every boundary.

    private fun tierForValue(value: Int, dc: Int): String {
        // Find a seed whose single draw we can offset to exactly `value`.
        val probe = DeckMechanics.executeCheck(MechanicCheck("x", dc = dc), Random(7))
        val rank = probe.drawn.first().rank
        val result = DeckMechanics.executeCheck(
            MechanicCheck("x", dc = dc, modifier = value - rank),
            Random(7)
        )
        assertEquals("probe setup drifted", value, result.value)
        return result.tier
    }

    @Test
    fun `critical success at exactly DC plus 5`() {
        assertEquals("critical_success", tierForValue(15, 10))
    }

    @Test
    fun `critical success above DC plus 5`() {
        assertEquals("critical_success", tierForValue(30, 10))
    }

    @Test
    fun `success at one below DC plus 5`() {
        assertEquals("success", tierForValue(14, 10))
    }

    @Test
    fun `success at exactly DC`() {
        assertEquals("success", tierForValue(10, 10))
    }

    @Test
    fun `partial at one below DC`() {
        assertEquals("partial", tierForValue(9, 10))
    }

    @Test
    fun `partial at exactly DC minus 3`() {
        assertEquals("partial", tierForValue(7, 10))
    }

    @Test
    fun `failure at one below DC minus 3`() {
        assertEquals("failure", tierForValue(6, 10))
    }

    @Test
    fun `failure far below DC`() {
        assertEquals("failure", tierForValue(-20, 10))
    }

    @Test
    fun `boundaries hold across the full documented DC range`() {
        // dc 3..18 per pipeline.md. The four tiers must partition the line at
        // dc+5, dc, dc-3 for every DC, not just the DC-10 case above.
        for (dc in 3..18) {
            assertEquals("dc=$dc", "critical_success", tierForValue(dc + 5, dc))
            assertEquals("dc=$dc", "success", tierForValue(dc + 4, dc))
            assertEquals("dc=$dc", "success", tierForValue(dc, dc))
            assertEquals("dc=$dc", "partial", tierForValue(dc - 1, dc))
            assertEquals("dc=$dc", "partial", tierForValue(dc - 3, dc))
            assertEquals("dc=$dc", "failure", tierForValue(dc - 4, dc))
        }
    }

    // ---- result echo -----------------------------------------------------

    @Test
    fun `result echoes the check inputs verbatim`() {
        // The plot stage receives this object verbatim; skill/dc/modifier must
        // survive unmangled or the model narrates against the wrong target.
        val check = MechanicCheck(skill = "persuasion", dc = 14, modifier = 2, advantage = 1)
        val result = DeckMechanics.executeCheck(check, Random(3))
        assertEquals("persuasion", result.skill)
        assertEquals(14, result.dc)
        assertEquals(2, result.modifier)
    }

    @Test
    fun `same seed is deterministic`() {
        val a = DeckMechanics.executeCheck(MechanicCheck("x", dc = 10, advantage = 1), Random(42))
        val b = DeckMechanics.executeCheck(MechanicCheck("x", dc = 10, advantage = 1), Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `unknown advantage values fall back to a single draw`() {
        // Router output is model-generated; a stray 2 or -7 must not crash or
        // silently draw a weird number of cards.
        listOf(2, -2, 7, -7).forEach { adv ->
            val result = DeckMechanics.executeCheck(
                MechanicCheck("x", dc = 10, advantage = adv),
                Random(5)
            )
            assertEquals("advantage=$adv", 1, result.drawn.size)
        }
    }
}
