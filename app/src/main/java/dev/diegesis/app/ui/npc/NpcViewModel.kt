package dev.diegesis.app.ui.npc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.importer.CharacterCardImporter
import dev.diegesis.app.data.model.Npc
import dev.diegesis.app.data.model.NpcAgency
import dev.diegesis.app.data.storage.NpcStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class NpcUiState(
    val campaignId: String = "",
    val npcs: List<Npc> = emptyList(),
    val editingNpc: Npc? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showImportDialog: Boolean = false
)

class NpcViewModel(
    private val storage: NpcStorage,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(NpcUiState())
    val uiState: StateFlow<NpcUiState> = _uiState.asStateFlow()

    fun loadNpcs(campaignId: String) {
        scope.launch {
            _uiState.value = _uiState.value.copy(
                campaignId = campaignId,
                isLoading = true,
                errorMessage = null
            )
            try {
                val npcIds = withContext(ioDispatcher) {
                    storage.list(campaignId)
                }
                val npcs = npcIds.mapNotNull { id ->
                    withContext(ioDispatcher) {
                        storage.load(campaignId, id)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    npcs = npcs,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load NPCs: ${e.message}"
                )
            }
        }
    }

    fun createNewNpc() {
        val npc = Npc(
            id = UUID.randomUUID().toString(),
            name = "",
            description = "",
            personality = "",
            voiceExamples = emptyList(),
            agency = NpcAgency(),
            trackers = emptyMap()
        )
        _uiState.value = _uiState.value.copy(editingNpc = npc)
    }

    fun editNpc(npc: Npc) {
        _uiState.value = _uiState.value.copy(editingNpc = npc)
    }

    fun updateEditingNpc(npc: Npc) {
        _uiState.value = _uiState.value.copy(editingNpc = npc)
    }

    fun saveNpc() {
        val npc = _uiState.value.editingNpc ?: return
        if (npc.name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "NPC name cannot be empty")
            return
        }

        scope.launch {
            try {
                withContext(ioDispatcher) {
                    storage.save(_uiState.value.campaignId, npc)
                }
                _uiState.value = _uiState.value.copy(editingNpc = null)
                loadNpcs(_uiState.value.campaignId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save NPC: ${e.message}"
                )
            }
        }
    }

    fun deleteNpc(npcId: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    storage.delete(_uiState.value.campaignId, npcId)
                }
                loadNpcs(_uiState.value.campaignId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete NPC: ${e.message}"
                )
            }
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingNpc = null)
    }

    fun showImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = true)
    }

    fun hideImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = false)
    }

    fun importCharacterCard(jsonString: String) {
        scope.launch {
            try {
                val npcId = UUID.randomUUID().toString()
                val npc = withContext(ioDispatcher) {
                    CharacterCardImporter.fromJson(jsonString, npcId)
                }
                _uiState.value = _uiState.value.copy(
                    editingNpc = npc,
                    showImportDialog = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to import character card: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
