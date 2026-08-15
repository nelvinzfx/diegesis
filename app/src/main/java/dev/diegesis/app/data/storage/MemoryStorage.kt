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
}
