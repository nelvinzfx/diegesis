package dev.diegesis.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.model.AppSettings
import dev.diegesis.app.data.model.StageModelSelection
import dev.diegesis.app.data.storage.SettingsStorage
import dev.diegesis.app.engine.ai.ThinkingEffort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val openaiBaseUrl: String = "https://api.openai.com/v1",
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val thinkProvider: String = "openai-compat",
    val thinkModel: String = "gpt-4o-mini",
    val writeProvider: String = "anthropic",
    val writeModel: String = "claude-3-5-sonnet-20241022",
    val language: String = "English",
    // Reasoning budget for THINK-stage calls; "low" | "medium" | "high" | "xhigh".
    val thinkingEffort: String = "medium",
    // Generation controls held as text so the fields are freely editable;
    // validated (Int, >= 512) on save, invalid input reverts to last saved.
    val thinkMaxTokens: String = "4096",
    val writeMaxTokens: String = "8192",
    val contextWindowTokens: String = "32768",
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val storage: SettingsStorage,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val settings = withContext(ioDispatcher) {
                    storage.load()
                }
                _uiState.value = _uiState.value.copy(
                    openaiBaseUrl = settings.openaiBaseUrl,
                    openaiApiKey = settings.openaiApiKey,
                    anthropicApiKey = settings.anthropicApiKey,
                    thinkProvider = settings.thinkModel.provider,
                    thinkModel = settings.thinkModel.model,
                    writeProvider = settings.writeModel.provider,
                    writeModel = settings.writeModel.model,
                    language = settings.language,
                    thinkingEffort = ThinkingEffort.normalize(settings.thinkingEffort),
                    thinkMaxTokens = settings.thinkMaxTokens.toString(),
                    writeMaxTokens = settings.writeMaxTokens.toString(),
                    contextWindowTokens = settings.contextWindowTokens.toString(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load settings: ${e.message}"
                )
            }
        }
    }

    fun updateOpenaiBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(openaiBaseUrl = url)
    }

    fun updateOpenaiApiKey(key: String) {
        _uiState.value = _uiState.value.copy(openaiApiKey = key)
    }

    fun updateAnthropicApiKey(key: String) {
        _uiState.value = _uiState.value.copy(anthropicApiKey = key)
    }

    fun updateThinkProvider(provider: String) {
        _uiState.value = _uiState.value.copy(thinkProvider = provider)
    }

    fun updateThinkModel(model: String) {
        _uiState.value = _uiState.value.copy(thinkModel = model)
    }

    fun updateWriteProvider(provider: String) {
        _uiState.value = _uiState.value.copy(writeProvider = provider)
    }

    fun updateWriteModel(model: String) {
        _uiState.value = _uiState.value.copy(writeModel = model)
    }

    fun updateLanguage(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun updateThinkingEffort(level: String) {
        _uiState.value = _uiState.value.copy(thinkingEffort = ThinkingEffort.normalize(level))
    }

    fun updateThinkMaxTokens(value: String) {
        _uiState.value = _uiState.value.copy(thinkMaxTokens = value)
    }

    fun updateWriteMaxTokens(value: String) {
        _uiState.value = _uiState.value.copy(writeMaxTokens = value)
    }

    fun updateContextWindowTokens(value: String) {
        _uiState.value = _uiState.value.copy(contextWindowTokens = value)
    }

    fun saveSettings() {
        scope.launch {
            try {
                val state = _uiState.value
                // Invalid or too-small numeric input reverts to the last
                // persisted value rather than saving garbage.
                val previous = withContext(ioDispatcher) { storage.load() }
                val thinkTokens = parseTokens(state.thinkMaxTokens, previous.thinkMaxTokens)
                val writeTokens = parseTokens(state.writeMaxTokens, previous.writeMaxTokens)
                val windowTokens = parseTokens(state.contextWindowTokens, previous.contextWindowTokens)

                val settings = AppSettings(
                    openaiBaseUrl = state.openaiBaseUrl.trim().trimEnd('/'),
                    openaiApiKey = state.openaiApiKey.trim(),
                    anthropicApiKey = state.anthropicApiKey.trim(),
                    thinkModel = StageModelSelection(state.thinkProvider, state.thinkModel.trim()),
                    writeModel = StageModelSelection(state.writeProvider, state.writeModel.trim()),
                    language = state.language.trim(),
                    thinkingEffort = ThinkingEffort.normalize(state.thinkingEffort),
                    thinkMaxTokens = thinkTokens,
                    writeMaxTokens = writeTokens,
                    contextWindowTokens = windowTokens
                )

                withContext(ioDispatcher) {
                    storage.save(settings)
                }

                _uiState.value = _uiState.value.copy(
                    thinkMaxTokens = thinkTokens.toString(),
                    writeMaxTokens = writeTokens.toString(),
                    contextWindowTokens = windowTokens.toString(),
                    successMessage = "Settings saved"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save settings: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }

    companion object {
        const val MIN_TOKENS = 512

        /** Parse a token-count field: valid Int >= [MIN_TOKENS], else [fallback]. */
        fun parseTokens(raw: String, fallback: Int): Int {
            val parsed = raw.trim().toIntOrNull() ?: return fallback
            return if (parsed >= MIN_TOKENS) parsed else fallback
        }
    }
}
