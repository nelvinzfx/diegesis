package dev.diegesis.app.engine.memory

import dev.diegesis.app.data.model.MemoryEntry

/**
 * Retrieves relevant memories using forced top-k BM25-style term overlap scoring.
 */
object MemoryRetriever {
    
    /**
     * Retrieve top-k memories based on term overlap with query.
     * 
     * @param query Combined playerInput + synopsis for scoring
     * @param allMemories All available memory entries
     * @param k Number of top results to return (default 5)
     * @return List of relevant memories, deduplicated by exact text
     */
    fun retrieve(
        query: String,
        allMemories: List<MemoryEntry>,
        k: Int = 5
    ): List<MemoryEntry> {
        // If we have fewer than 10 memories total, return all
        if (allMemories.size < 10) {
            return allMemories.distinctBy { it.fact }
        }
        
        // Tokenize query into terms (lowercase, alphanumeric only)
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) {
            return allMemories.take(k).distinctBy { it.fact }
        }
        
        // Score each memory by term overlap
        val scored = allMemories.map { memory ->
            val memoryTerms = tokenize(memory.fact)
            val score = calculateScore(queryTerms, memoryTerms)
            ScoredMemory(memory, score)
        }
        
        // Sort by score descending, take top k, deduplicate
        return scored
            .sortedByDescending { it.score }
            .take(k)
            .map { it.memory }
            .distinctBy { it.fact }
    }
    
    /**
     * Tokenize text into lowercase alphanumeric terms.
     */
    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 } // Skip single-char tokens
            .toSet()
    }
    
    /**
     * Calculate BM25-style term overlap score.
     * Simple version: count of query terms that appear in memory.
     */
    private fun calculateScore(queryTerms: Set<String>, memoryTerms: Set<String>): Double {
        val overlap = queryTerms.intersect(memoryTerms).size
        
        // Normalize by query length to avoid bias toward long queries
        return if (queryTerms.isEmpty()) {
            0.0
        } else {
            overlap.toDouble() / queryTerms.size
        }
    }
    
    private data class ScoredMemory(
        val memory: MemoryEntry,
        val score: Double
    )
}
