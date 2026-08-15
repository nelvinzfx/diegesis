package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.MemoryEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class MemoryStorage(private val filesDir: File) {
    private val campaignsDir = File(filesDir, "campaigns")
    private val mutexMap = mutableMapOf<String, Mutex>()

    private fun getMutex(campaignId: String): Mutex {
        synchronized(mutexMap) {
            return mutexMap.getOrPut(campaignId) { Mutex() }
        }
    }

    suspend fun appendMemory(campaignId: String, entry: MemoryEntry) {
        val file = File(campaignsDir, "$campaignId/memories.jsonl")
        val mutex = getMutex(campaignId)
        
        mutex.withLock {
            file.parentFile?.mkdirs()
            val json = Json.encodeToString(entry)
            file.appendText(json + "\n")
        }
    }

    fun loadMemories(campaignId: String): List<MemoryEntry> {
        val file = File(campaignsDir, "$campaignId/memories.jsonl")
        if (!file.exists()) {
            return emptyList()
        }
        
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    Json.decodeFromString<MemoryEntry>(line)
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Remove the FIRST line whose content matches [entry] on
     * scope + npc_id + fact + turn (timestamp is deliberately ignored so a
     * UI-held copy still matches the stored line). The file is rewritten
     * atomically (temp file + atomic rename, same convention as
     * [AtomicWriteHelper]) under the per-campaign mutex. Missing file or no
     * match is a no-op. Lines that fail to parse are preserved verbatim.
     */
    suspend fun deleteMemory(campaignId: String, entry: MemoryEntry) {
        val file = File(campaignsDir, "$campaignId/memories.jsonl")
        val mutex = getMutex(campaignId)

        mutex.withLock {
            if (!file.exists()) return

            val lines = file.readLines()
            var removed = false
            val kept = ArrayList<String>(lines.size)
            for (line in lines) {
                if (!removed && line.isNotBlank() && matchesEntry(line, entry)) {
                    removed = true
                    continue
                }
                kept.add(line)
            }
            if (!removed) return

            val content = if (kept.isEmpty()) "" else kept.joinToString("\n") + "\n"
            AtomicWriteHelper.writeString(file, content)
        }
    }

    /** Delete memories.jsonl for the campaign under the per-campaign mutex. */
    suspend fun clearMemories(campaignId: String) {
        val file = File(campaignsDir, "$campaignId/memories.jsonl")
        val mutex = getMutex(campaignId)

        mutex.withLock {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun matchesEntry(line: String, entry: MemoryEntry): Boolean {
        val decoded = try {
            Json.decodeFromString<MemoryEntry>(line)
        } catch (e: Exception) {
            return false
        }
        return decoded.scope == entry.scope &&
            decoded.npc_id == entry.npc_id &&
            decoded.fact == entry.fact &&
            decoded.turn == entry.turn
    }
}
