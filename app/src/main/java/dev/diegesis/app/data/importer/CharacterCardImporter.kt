package dev.diegesis.app.data.importer

import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Character Card V2 format importer.
 * Supports both JSON files and PNG files with embedded character data.
 */
object CharacterCardImporter {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class CharacterCardV2(
        val spec: String? = null,
        val spec_version: String? = null,
        val data: CharacterData? = null,
        val name: String? = null,
        val description: String? = null,
        val personality: String? = null,
        val scenario: String? = null,
        val first_mes: String? = null,
        val mes_example: String? = null
    )

    @Serializable
    private data class CharacterData(
        val name: String,
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        val first_mes: String = "",
        val mes_example: String = ""
    )

    /**
     * Import from JSON string.
     */
    fun fromJson(jsonString: String, npcId: String): Npc {
        val card = json.decodeFromString<CharacterCardV2>(jsonString)
        return toNpc(card, npcId, null)
    }

    /**
     * Import from PNG bytes with embedded character card data.
     */
    fun fromPngBytes(pngBytes: ByteArray, npcId: String): Npc {
        val jsonString = extractCharaFromPng(pngBytes)
        val card = json.decodeFromString<CharacterCardV2>(jsonString)
        return toNpc(card, npcId, Base64.getEncoder().encodeToString(pngBytes))
    }

    /**
     * Import from PNG input stream with embedded character card data.
     */
    fun fromPngStream(stream: InputStream, npcId: String): Npc {
        return fromPngBytes(stream.readBytes(), npcId)
    }

    private fun toNpc(card: CharacterCardV2, npcId: String, sourceCard: String?): Npc {
        // V2 cards can have data either at root or in a 'data' block
        val name = card.data?.name ?: card.name ?: "Unnamed"
        val description = card.data?.description ?: card.description ?: ""
        val personality = card.data?.personality ?: card.personality ?: ""
        val scenario = card.data?.scenario ?: card.scenario ?: ""
        val mesExample = card.data?.mes_example ?: card.mes_example ?: ""

        // Parse mes_example into voice examples (split by common delimiters)
        val voiceExamples = if (mesExample.isNotBlank()) {
            mesExample.split("<START>", "{{char}}:", "{{user}}:")
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length > 10 }
                .take(5)
        } else {
            emptyList()
        }

        return Npc(
            id = npcId,
            name = name,
            description = description,
            personality = personality,
            voiceExamples = voiceExamples,
            agency = NpcAgency(),
            trackers = emptyMap(),
            sourceCard = sourceCard
        )
    }

    /**
     * Extract character card JSON from PNG tEXt chunk.
     * PNG format: signature, then chunks of [length][type][data][crc].
     * We look for a tEXt chunk with keyword "chara".
     */
    private fun extractCharaFromPng(pngBytes: ByteArray): String {
        val stream = ByteArrayInputStream(pngBytes)
        
        // Verify PNG signature: 137 80 78 71 13 10 26 10
        val signature = ByteArray(8)
        stream.read(signature)
        if (!signature.contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
            throw IllegalArgumentException("Not a valid PNG file")
        }

        while (true) {
            // Read chunk length (4 bytes, big-endian)
            val lengthBytes = ByteArray(4)
            if (stream.read(lengthBytes) != 4) break
            val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int

            // Read chunk type (4 bytes)
            val typeBytes = ByteArray(4)
            if (stream.read(typeBytes) != 4) break
            val type = String(typeBytes, Charsets.ISO_8859_1)

            // Read chunk data
            val data = ByteArray(length)
            if (stream.read(data) != length) break

            // Skip CRC (4 bytes)
            stream.skip(4)

            // Check if this is a tEXt chunk with keyword "chara"
            if (type == "tEXt") {
                val nullIndex = data.indexOf(0)
                if (nullIndex != -1) {
                    val keyword = String(data, 0, nullIndex, Charsets.ISO_8859_1)
                    if (keyword == "chara") {
                        val textData = data.copyOfRange(nullIndex + 1, data.size)
                        val base64String = String(textData, Charsets.ISO_8859_1)
                        val decodedBytes = Base64.getDecoder().decode(base64String)
                        return String(decodedBytes, Charsets.UTF_8)
                    }
                }
            }

            // Stop at IEND chunk
            if (type == "IEND") break
        }

        throw IllegalArgumentException("No 'chara' tEXt chunk found in PNG")
    }
}
