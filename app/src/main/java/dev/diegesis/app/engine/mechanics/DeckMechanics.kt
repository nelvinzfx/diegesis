package dev.diegesis.app.engine.mechanics

import dev.diegesis.app.data.model.DrawnCard
import dev.diegesis.app.data.model.MechanicCheck
import dev.diegesis.app.data.model.MechanicResult
import kotlin.random.Random

/**
 * Pure code mechanics for deck-based checks.
 * Standard 52-card deck: ranks 2..14 (J=11, Q=12, K=13, A=14)
 * Suits: hearts, diamonds, clubs, spades
 */
object DeckMechanics {
    private val SUITS = listOf("hearts", "diamonds", "clubs", "spades")
    
    private val RANK_NAMES = mapOf(
        2 to "2", 3 to "3", 4 to "4", 5 to "5", 6 to "6", 7 to "7", 8 to "8", 9 to "9", 10 to "10",
        11 to "Jack", 12 to "Queen", 13 to "King", 14 to "Ace"
    )

    /**
     * Execute a mechanic check with advantage/disadvantage.
     * @param check The check parameters from the router stage
     * @param random Random instance (injectable for testing)
     * @return MechanicResult with drawn cards and outcome tier
     */
    fun executeCheck(check: MechanicCheck, random: Random = Random.Default): MechanicResult {
        val drawnCards = when (check.advantage) {
            1 -> {
                // Advantage: draw 2, take higher
                val card1 = drawCard(random)
                val card2 = drawCard(random)
                listOf(card1, card2)
            }
            -1 -> {
                // Disadvantage: draw 2, take lower
                val card1 = drawCard(random)
                val card2 = drawCard(random)
                listOf(card1, card2)
            }
            else -> {
                // Normal: draw 1
                listOf(drawCard(random))
            }
        }

        // Determine the value used for the check
        val cardValue = when (check.advantage) {
            1 -> drawnCards.maxOf { it.rank }
            -1 -> drawnCards.minOf { it.rank }
            else -> drawnCards.first().rank
        }

        val totalValue = cardValue + check.modifier
        val tier = calculateTier(totalValue, check.dc)

        return MechanicResult(
            skill = check.skill,
            dc = check.dc,
            modifier = check.modifier,
            drawn = drawnCards,
            value = totalValue,
            tier = tier
        )
    }

    /**
     * Draw a single card from the deck.
     */
    private fun drawCard(random: Random): DrawnCard {
        val rank = random.nextInt(2, 15) // 2..14 inclusive
        val suit = SUITS.random(random)
        return DrawnCard(
            rank = rank,
            suit = suit,
            name = "${RANK_NAMES[rank]} of ${suit.replaceFirstChar { it.uppercase() }}"
        )
    }

    /**
     * Calculate outcome tier based on value vs DC.
     * - value >= DC + 5 → critical_success
     * - value >= DC → success
     * - value >= DC - 3 → partial
     * - else → failure
     */
    private fun calculateTier(value: Int, dc: Int): String {
        return when {
            value >= dc + 5 -> "critical_success"
            value >= dc -> "success"
            value >= dc - 3 -> "partial"
            else -> "failure"
        }
    }
}
