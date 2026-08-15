package dev.diegesis.app.ui.settings

import dev.diegesis.app.data.model.AppSettings
import dev.diegesis.app.data.model.StageModelSelection
import dev.diegesis.app.data.storage.SettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var tempDir: File
    private lateinit var storage: SettingsStorage
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        tempDir = tmp.newFolder("settings_test")
        storage = SettingsStorage(tempDir)
        viewModel = SettingsViewModel(
            storage = storage,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `initial state loads default settings`() = runBlocking {
        val state = viewModel.uiState.value
        assertEquals("https://api.openai.com/v1", state.openaiBaseUrl)
        assertEquals("", state.openaiApiKey)
        assertEquals("", state.anthropicApiKey)
        assertEquals("openai-compat", state.thinkProvider)
        assertEquals("gpt-4o-mini", state.thinkModel)
        assertEquals("anthropic", state.writeProvider)
        assertEquals("claude-3-5-sonnet-20241022", state.writeModel)
        assertEquals("English", state.language)
        assertEquals("4096", state.thinkMaxTokens)
        assertEquals("8192", state.writeMaxTokens)
        assertEquals("32768", state.contextWindowTokens)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadSettings loads persisted settings`() = runBlocking {
        val settings = AppSettings(
            openaiBaseUrl = "https://custom.api.com/v1",
            openaiApiKey = "test-key-123",
            anthropicApiKey = "anthropic-key",
            thinkModel = StageModelSelection("anthropic", "claude-3-5-haiku-20241022"),
            writeModel = StageModelSelection("openai-compat", "gpt-4o")
        )
        storage.save(settings)

        viewModel.loadSettings()

        val state = viewModel.uiState.value
        assertEquals("https://custom.api.com/v1", state.openaiBaseUrl)
        assertEquals("test-key-123", state.openaiApiKey)
        assertEquals("anthropic-key", state.anthropicApiKey)
        assertEquals("anthropic", state.thinkProvider)
        assertEquals("claude-3-5-haiku-20241022", state.thinkModel)
        assertEquals("openai-compat", state.writeProvider)
        assertEquals("gpt-4o", state.writeModel)
    }

    @Test
    fun `updateOpenaiBaseUrl updates state`() {
        viewModel.updateOpenaiBaseUrl("https://new-url.com")
        assertEquals("https://new-url.com", viewModel.uiState.value.openaiBaseUrl)
    }

    @Test
    fun `updateOpenaiApiKey updates state`() {
        viewModel.updateOpenaiApiKey("new-key")
        assertEquals("new-key", viewModel.uiState.value.openaiApiKey)
    }

    @Test
    fun `updateAnthropicApiKey updates state`() {
        viewModel.updateAnthropicApiKey("anthropic-key")
        assertEquals("anthropic-key", viewModel.uiState.value.anthropicApiKey)
    }

    @Test
    fun `updateThinkProvider updates state`() {
        viewModel.updateThinkProvider("anthropic")
        assertEquals("anthropic", viewModel.uiState.value.thinkProvider)
    }

    @Test
    fun `updateThinkModel updates state`() {
        viewModel.updateThinkModel("claude-3-5-haiku-20241022")
        assertEquals("claude-3-5-haiku-20241022", viewModel.uiState.value.thinkModel)
    }

    @Test
    fun `updateWriteProvider updates state`() {
        viewModel.updateWriteProvider("openai-compat")
        assertEquals("openai-compat", viewModel.uiState.value.writeProvider)
    }

    @Test
    fun `updateWriteModel updates state`() {
        viewModel.updateWriteModel("gpt-4o")
        assertEquals("gpt-4o", viewModel.uiState.value.writeModel)
    }

    @Test
    fun `saveSettings persists settings to storage`() = runBlocking {
        viewModel.updateOpenaiBaseUrl("https://custom.api.com/v1")
        viewModel.updateOpenaiApiKey("test-key")
        viewModel.updateAnthropicApiKey("anthropic-key")
        viewModel.updateThinkProvider("anthropic")
        viewModel.updateThinkModel("claude-3-5-haiku-20241022")
        viewModel.updateWriteProvider("openai-compat")
        viewModel.updateWriteModel("gpt-4o")
        viewModel.updateLanguage("Bahasa Indonesia")

        viewModel.saveSettings()

        val saved = storage.load()
        assertEquals("https://custom.api.com/v1", saved.openaiBaseUrl)
        assertEquals("test-key", saved.openaiApiKey)
        assertEquals("anthropic-key", saved.anthropicApiKey)
        assertEquals("anthropic", saved.thinkModel.provider)
        assertEquals("claude-3-5-haiku-20241022", saved.thinkModel.model)
        assertEquals("openai-compat", saved.writeModel.provider)
        assertEquals("gpt-4o", saved.writeModel.model)
        assertEquals("Bahasa Indonesia", saved.language)
        assertNotNull(viewModel.uiState.value.successMessage)
    }

    // ---- generation controls (phase 7) --------------------------------------

    @Test
    fun `generation token fields save and load round trip`() = runBlocking {
        viewModel.updateThinkMaxTokens("2048")
        viewModel.updateWriteMaxTokens("16384")
        viewModel.updateContextWindowTokens("65536")

        viewModel.saveSettings()

        val saved = storage.load()
        assertEquals(2048, saved.thinkMaxTokens)
        assertEquals(16384, saved.writeMaxTokens)
        assertEquals(65536, saved.contextWindowTokens)

        // A fresh ViewModel loads them back into the UI state as text.
        val fresh = SettingsViewModel(
            storage = storage,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        assertEquals("2048", fresh.uiState.value.thinkMaxTokens)
        assertEquals("16384", fresh.uiState.value.writeMaxTokens)
        assertEquals("65536", fresh.uiState.value.contextWindowTokens)
    }

    @Test
    fun `non numeric token input reverts to last saved value on save`() = runBlocking {
        // Persist a known-good value first.
        viewModel.updateThinkMaxTokens("2048")
        viewModel.saveSettings()

        viewModel.updateThinkMaxTokens("not a number")
        viewModel.saveSettings()

        assertEquals(2048, storage.load().thinkMaxTokens)
        // The UI field also snaps back to the persisted value.
        assertEquals("2048", viewModel.uiState.value.thinkMaxTokens)
    }

    @Test
    fun `token value below the lower bound reverts to last saved value`() = runBlocking {
        viewModel.updateWriteMaxTokens("100")
        viewModel.saveSettings()

        // 100 < 512, so the default (8192) must survive.
        assertEquals(8192, storage.load().writeMaxTokens)
        assertEquals("8192", viewModel.uiState.value.writeMaxTokens)
    }

    @Test
    fun `parseTokens accepts the lower bound and rejects below it`() {
        assertEquals(512, SettingsViewModel.parseTokens("512", 999))
        assertEquals(999, SettingsViewModel.parseTokens("511", 999))
        assertEquals(999, SettingsViewModel.parseTokens("", 999))
        assertEquals(999, SettingsViewModel.parseTokens("12.5", 999))
        assertEquals(4096, SettingsViewModel.parseTokens(" 4096 ", 999))
    }

    @Test
    fun `old settings json without generation fields loads with defaults`() {
        // Pre-phase-7 settings.json: no token fields anywhere.
        File(tempDir, "settings.json").writeText(
            """
            {
              "thinkModel": { "provider": "openai-compat", "model": "gpt-4o-mini" },
              "writeModel": { "provider": "anthropic", "model": "claude-3-5-sonnet-20241022" },
              "openaiBaseUrl": "https://api.openai.com/v1",
              "openaiApiKey": "legacy-key",
              "anthropicApiKey": "",
              "language": "English"
            }
            """.trimIndent()
        )

        val loaded = storage.load()
        assertEquals("legacy-key", loaded.openaiApiKey)
        assertEquals(4096, loaded.thinkMaxTokens)
        assertEquals(8192, loaded.writeMaxTokens)
        assertEquals(32768, loaded.contextWindowTokens)
    }

    @Test
    fun `language is saved as free text`() = runBlocking {
        viewModel.updateLanguage("日本語")
        viewModel.saveSettings()
        assertEquals("日本語", storage.load().language)
    }
}
