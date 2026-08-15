package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.Campaign
import kotlinx.serialization.encodeToString
import java.io.File

class CampaignStorage(private val filesDir: File) {
    private val campaignsDir = File(filesDir, "campaigns")

    fun list(): List<String> {
        return campaignsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    fun load(campaignId: String): Campaign? {
        val file = File(campaignsDir, "$campaignId/campaign.json")
        return if (file.exists()) {
            val json = file.readText()
            AtomicWriteHelper.json.decodeFromString<Campaign>(json)
        } else {
            null
        }
    }

    fun save(campaign: Campaign) {
        val file = File(campaignsDir, "${campaign.id}/campaign.json")
        val json = AtomicWriteHelper.json.encodeToString(campaign)
        AtomicWriteHelper.writeString(file, json)
    }

    fun delete(campaignId: String) {
        val dir = File(campaignsDir, campaignId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
