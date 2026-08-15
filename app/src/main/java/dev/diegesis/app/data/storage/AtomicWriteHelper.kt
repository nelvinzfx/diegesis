package dev.diegesis.app.data.storage

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicWriteHelper {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Atomically write string content to target file.
     * Writes to temp file first, then renames.
     */
    fun writeString(target: File, content: String) {
        val temp = File("${target.path}.tmp")
        try {
            target.parentFile?.mkdirs()
            temp.writeText(content)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /**
     * Atomically write bytes to target file.
     * Writes to temp file first, then renames.
     */
    fun writeBytes(target: File, content: ByteArray) {
        val temp = File("${target.path}.tmp")
        try {
            target.parentFile?.mkdirs()
            temp.writeBytes(content)
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
