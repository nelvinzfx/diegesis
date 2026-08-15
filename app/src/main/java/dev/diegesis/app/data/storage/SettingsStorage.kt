package dev.diegesis.app.data.storage

import dev.diegesis.app.data.model.AppSettings
import kotlinx.serialization.encodeToString
import java.io.File

class SettingsStorage(private val filesDir: File) {
    private val settingsFile = File(filesDir, "settings.json")

    fun load(): AppSettings {
        return if (settingsFile.exists()) {
            val json = settingsFile.readText()
            AtomicWriteHelper.json.decodeFromString<AppSettings>(json)
        } else {
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        val json = AtomicWriteHelper.json.encodeToString(settings)
        AtomicWriteHelper.writeString(settingsFile, json)
    }
}
