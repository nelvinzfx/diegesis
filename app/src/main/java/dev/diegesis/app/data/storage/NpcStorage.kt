package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.Npc
import kotlinx.serialization.encodeToString
import java.io.File

class NpcStorage(private val filesDir: File) {
    private val campaignsDir = File(filesDir, "campaigns")

    fun list(campaignId: String): List<String> {
        val npcsDir = File(campaignsDir, "$campaignId/npcs")
        return npcsDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun load(campaignId: String, npcId: String): Npc? {
        val file = File(campaignsDir, "$campaignId/npcs/$npcId.json")
        return if (file.exists()) {
            val json = file.readText()
            AtomicWriteHelper.json.decodeFromString<Npc>(json)
        } else {
            null
        }
    }

    fun save(campaignId: String, npc: Npc) {
        val file = File(campaignsDir, "$campaignId/npcs/${npc.id}.json")
        val json = AtomicWriteHelper.json.encodeToString(npc)
        AtomicWriteHelper.writeString(file, json)
    }

    fun delete(campaignId: String, npcId: String) {
        val file = File(campaignsDir, "$campaignId/npcs/$npcId.json")
        if (file.exists()) {
            file.delete()
        }
    }
}
