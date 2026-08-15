package dev.diegesis.app.engine.assembler

import dev.diegesis.app.data.model.Turn

/**
 * Trims turn history to fit within a token budget.
 * 
 * Estimation: chars / 4 ≈ tokens (conservative for English, generous for CJK).
 * Drops OLDEST visible turns first, keeps newest.
 */
object ContextWindowTrimmer {
    
    /**
     * Trim the turn list to fit within the estimated token budget.
     * 
     * @param turns All visible turns (already filtered by visibility)
     * @param budgetTokens Token budget for history
     * @return Trimmed list of turns (newest N that fit)
     */
    fun trimToFit(turns: List<Turn>, budgetTokens: Int): List<Turn> {
        if (turns.isEmpty()) return emptyList()
        if (budgetTokens <= 0) return emptyList()
        
        val budgetChars = budgetTokens * 4
        
        // Walk backward from newest, accumulating until we exceed the budget.
        val kept = mutableListOf<Turn>()
        var accumulated = 0
        
        for (turn in turns.asReversed()) {
            val turnSize = estimateTurnSize(turn)
            if (accumulated + turnSize > budgetChars && kept.isNotEmpty()) {
                // Would exceed budget; stop here (but keep at least one turn).
                break
            }
            kept.add(turn)
            accumulated += turnSize
        }
        
        // Reverse back to chronological order.
        return kept.asReversed()
    }
    
    /**
     * Estimate the character size of a turn (input + latest scene output).
     */
    private fun estimateTurnSize(turn: Turn): Int {
        val inputSize = turn.playerInput.length
        val outputSize = turn.variants.lastOrNull()?.sceneOutput?.length ?: 0
        return inputSize + outputSize
    }
}
