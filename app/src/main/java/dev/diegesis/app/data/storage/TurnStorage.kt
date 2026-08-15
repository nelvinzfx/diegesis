package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.model.TurnVariant
import kotlinx.serialization.encodeToString
import java.io.File

class TurnStorage(private val filesDir: File) {
    private val campaignsDir = File(filesDir, "campaigns")

    fun listTurnIndices(campaignId: String): List<Int> {
        val turnsDir = File(campaignsDir, "$campaignId/turns")
        return turnsDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            ?: emptyList()
    }

    fun loadTurn(campaignId: String, index: Int): Turn? {
        val file = File(campaignsDir, "$campaignId/turns/$index.json")
        return if (file.exists()) {
            val json = file.readText()
            AtomicWriteHelper.json.decodeFromString<Turn>(json)
        } else {
            null
        }
    }

    fun saveTurn(campaignId: String, turn: Turn) {
        val file = File(campaignsDir, "$campaignId/turns/${turn.index}.json")
        val json = AtomicWriteHelper.json.encodeToString(turn)
        AtomicWriteHelper.writeString(file, json)
    }

    fun appendVariant(campaignId: String, turnIndex: Int, variant: TurnVariant) {
        val turn = loadTurn(campaignId, turnIndex) ?: return
        val updatedTurn = turn.copy(variants = turn.variants + variant)
        saveTurn(campaignId, updatedTurn)
    }

    /**
     * Delete turn at index AND all turns with index > this index.
     * This implements the truncation rule from storage.md.
     */
    fun deleteTurn(campaignId: String, index: Int) {
        val turnsDir = File(campaignsDir, "$campaignId/turns")
        turnsDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                file.nameWithoutExtension.toIntOrNull()?.let { idx -> idx to file }
            }
            ?.filter { (idx, _) -> idx >= index }
            ?.forEach { (_, file) -> file.delete() }
    }
}
