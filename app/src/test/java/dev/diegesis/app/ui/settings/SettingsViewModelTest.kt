package dev.diegesis.app.ui.settings

import dev.diegesis.app.data.model.AppSettings
import dev.diegesis.app.data.model.StageModelSelection
import dev.diegesis.app.data.storage.SettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
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

        viewModel.saveSettings()

        val saved = storage.load()
        assertEquals("https://custom.api.com/v1", saved.openaiBaseUrl)
        assertEquals("test-key", saved.openaiApiKey)
        assertEquals("anthropic-key", saved.anthropicApiKey)
        assertEquals("anthropic", saved.thinkModel.provider)
        assertEquals("claude-3-5-haiku-20241022", saved.thinkModel.model)
        assertEquals("openai-compat", saved.writeModel.provider)
        assertEquals("gpt-4o", saved.writeModel.model)
        assertNotNull(viewModel.uiState.value.successMessage)
    }
}
