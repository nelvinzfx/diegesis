package dev.diegesis.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.model.MemoryEntry
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MemoriesUiState(
    val campaignId: String = "",
    /** Extracted facts in file order (newest last). */
    val memories: List<MemoryEntry> = emptyList(),
    /** npc_id -> display name, resolved via NpcStorage. */
    val npcNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showClearConfirm: Boolean = false
)

class MemoriesViewModel(
    private val memoryStorage: MemoryStorage,
    private val npcStorage: NpcStorage,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    fun loadMemories(campaignId: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(
                campaignId = campaignId,
                isLoading = true,
                errorMessage = null
            )
            try {
                val memories = withContext(ioDispatcher) {
                    memoryStorage.loadMemories(campaignId)
                }
                val npcIds = memories.mapNotNull { it.npc_id }.distinct()
                val names = npcIds.mapNotNull { id ->
                    withContext(ioDispatcher) {
                        npcStorage.load(campaignId, id)?.let { id to it.name }
                    }
                }.toMap()

                _uiState.value = _uiState.value.copy(
                    memories = memories,
                    npcNames = names,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load memories: ${e.message}"
                )
            }
        }
    }

    /** Display name for a memory's NPC badge; raw id when the NPC is gone. */
    fun npcNameFor(entry: MemoryEntry): String? =
        entry.npc_id?.let { id -> _uiState.value.npcNames[id] ?: id }

    fun deleteMemory(entry: MemoryEntry) {
        val campaignId = _uiState.value.campaignId
        if (campaignId.isBlank()) return
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    memoryStorage.deleteMemory(campaignId, entry)
                }
                loadMemories(campaignId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete memory: ${e.message}"
                )
            }
        }
    }

    fun requestClearAll() {
        _uiState.value = _uiState.value.copy(showClearConfirm = true)
    }

    fun cancelClearAll() {
        _uiState.value = _uiState.value.copy(showClearConfirm = false)
    }

    fun confirmClearAll() {
        val campaignId = _uiState.value.campaignId
        if (campaignId.isBlank()) {
            _uiState.value = _uiState.value.copy(showClearConfirm = false)
            return
        }
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    memoryStorage.clearMemories(campaignId)
                }
                _uiState.value = _uiState.value.copy(showClearConfirm = false)
                loadMemories(campaignId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showClearConfirm = false,
                    errorMessage = "Failed to clear memories: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
