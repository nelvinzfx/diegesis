package dev.diegesis.app.data

import dev.diegesis.app.data.importer.CharacterCardImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32

class CharacterCardImporterTest {

    @Test
    fun testJsonV2CardImport() {
        val jsonCard = """
        {
            "spec": "chara_card_v2",
            "spec_version": "2.0",
            "data": {
                "name": "Test Character",
                "description": "A brave warrior from the north",
                "personality": "Courageous and honorable",
                "scenario": "Fighting in the great war",
                "first_mes": "Hello, traveler!",
                "mes_example": "<START>\n{{char}}: I swear by my honor!\n{{user}}: That's noble of you.\n{{char}}: A warrior's word is their bond."
            }
        }
        """.trimIndent()

        val npc = CharacterCardImporter.fromJson(jsonCard, "npc-test")

        assertEquals("npc-test", npc.id)
        assertEquals("Test Character", npc.name)
        assertEquals("A brave warrior from the north", npc.description)
        assertEquals("Courageous and honorable", npc.personality)
        assertTrue(npc.voiceExamples.size >= 2)
        assertNull(npc.sourceCard)
    }

    @Test
    fun testJsonV2CardImportRootLevel() {
        // Test V2 card with fields at root level (legacy format)
        val jsonCard = """
        {
            "name": "Root Character",
            "description": "Character at root",
            "personality": "Mysterious",
            "scenario": "Exploring ruins",
            "mes_example": "{{char}}: Let's venture forth!\n{{user}}: Right behind you.\n{{char}}: Stay close."
        }
        """.trimIndent()

        val npc = CharacterCardImporter.fromJson(jsonCard, "npc-root")

        assertEquals("npc-root", npc.id)
        assertEquals("Root Character", npc.name)
        assertEquals("Character at root", npc.description)
        assertEquals("Mysterious", npc.personality)
        assertTrue(npc.voiceExamples.isNotEmpty())
    }

    @Test
    fun testPngCardImport() {
        val jsonCard = """
        {
            "spec": "chara_card_v2",
            "spec_version": "2.0",
            "data": {
                "name": "PNG Character",
                "description": "Character from PNG",
                "personality": "Friendly and helpful",
                "scenario": "In a tavern",
                "mes_example": "{{char}}: Welcome, friend!"
            }
        }
        """.trimIndent()

        val pngBytes = createTestPng(jsonCard)
        val npc = CharacterCardImporter.fromPngBytes(pngBytes, "npc-png")

        assertEquals("npc-png", npc.id)
        assertEquals("PNG Character", npc.name)
        assertEquals("Character from PNG", npc.description)
        assertEquals("Friendly and helpful", npc.personality)
        assertNotNull(npc.sourceCard)
        
        // Verify sourceCard is valid base64
        val decoded = Base64.getDecoder().decode(npc.sourceCard)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun testVoiceExamplesParsing() {
        val jsonCard = """
        {
            "data": {
                "name": "Talkative NPC",
                "description": "Loves to chat",
                "personality": "Chatty",
                "mes_example": "<START>\n{{char}}: Hello there, friend! How are you today?\n{{user}}: I'm doing well.\n<START>\n{{char}}: That's wonderful to hear! Would you like to hear a story?\n{{user}}: Sure!\n{{char}}: Once upon a time, in a land far away..."
            }
        }
        """.trimIndent()

        val npc = CharacterCardImporter.fromJson(jsonCard, "npc-talk")

        assertTrue(npc.voiceExamples.isNotEmpty())
        // Should have extracted multiple voice examples
        assertTrue(npc.voiceExamples.any { it.contains("Hello there") || it.contains("wonderful to hear") })
    }

    @Test
    fun testMinimalCard() {
        val jsonCard = """
        {
            "data": {
                "name": "Minimal NPC"
            }
        }
        """.trimIndent()

        val npc = CharacterCardImporter.fromJson(jsonCard, "npc-min")

        assertEquals("npc-min", npc.id)
        assertEquals("Minimal NPC", npc.name)
        assertEquals("", npc.description)
        assertEquals("", npc.personality)
        assertTrue(npc.voiceExamples.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPng() {
        val invalidBytes = "Not a PNG file".toByteArray()
        CharacterCardImporter.fromPngBytes(invalidBytes, "npc-invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPngWithoutCharaChunk() {
        // Create a valid PNG but without the chara chunk
        val pngBytes = createTestPngWithoutChara()
        CharacterCardImporter.fromPngBytes(pngBytes, "npc-no-chara")
    }

    /**
     * Helper function to create a valid PNG with embedded character card data.
     */
    private fun createTestPng(jsonData: String): ByteArray {
        val output = ByteArrayOutputStream()

        // PNG signature
        output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

        // IHDR chunk (minimal 1x1 image)
        writeChunk(output, "IHDR", byteArrayOf(
            0, 0, 0, 1,  // width: 1
            0, 0, 0, 1,  // height: 1
            8,           // bit depth
            2,           // color type: truecolor
            0, 0, 0      // compression, filter, interlace
        ))

        // tEXt chunk with chara data
        val base64Json = Base64.getEncoder().encodeToString(jsonData.toByteArray(Charsets.UTF_8))
        val textData = "chara\u0000$base64Json".toByteArray(Charsets.ISO_8859_1)
        writeChunk(output, "tEXt", textData)

        // IDAT chunk (minimal compressed image data)
        val idatData = byteArrayOf(
            0x78.toByte(), 0x9C.toByte(),  // zlib header
            0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01  // compressed data for 1x1 white pixel
        )
        writeChunk(output, "IDAT", idatData)

        // IEND chunk
        writeChunk(output, "IEND", byteArrayOf())

        return output.toByteArray()
    }

    /**
     * Helper function to create a valid PNG without chara chunk.
     */
    private fun createTestPngWithoutChara(): ByteArray {
        val output = ByteArrayOutputStream()

        // PNG signature
        output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

        // IHDR chunk
        writeChunk(output, "IHDR", byteArrayOf(
            0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0
        ))

        // IDAT chunk
        val idatData = byteArrayOf(
            0x78.toByte(), 0x9C.toByte(),
            0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01
        )
        writeChunk(output, "IDAT", idatData)

        // IEND chunk
        writeChunk(output, "IEND", byteArrayOf())

        return output.toByteArray()
    }

    /**
     * Write a PNG chunk with proper format: length, type, data, CRC.
     */
    private fun writeChunk(output: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)

        // Length (4 bytes, big-endian)
        val lengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        lengthBuffer.putInt(data.size)
        output.write(lengthBuffer.array())

        // Type (4 bytes)
        output.write(typeBytes)

        // Data
        output.write(data)

        // CRC (4 bytes) - calculate over type + data
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        crcBuffer.putInt(crc.value.toInt())
        output.write(crcBuffer.array())
    }
}
