package dev.diegesis.app.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.Turn
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.PipelineOrchestrator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    val campaign: Campaign? = null,
    val turns: List<Turn> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val selectedVariantIndices: Map<Int, Int> = emptyMap(), // turnIndex -> variantIndex
    val activeStageDetailsTurn: Int? = null,
    val errorMessage: String? = null
)

class StoryViewModel(
    private val campaignId: String,
    private val orchestrator: PipelineOrchestrator,
    private val campaignStorage: CampaignStorage,
    private val turnStorage: TurnStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        loadCampaign()
        loadTurns()
    }

    fun loadCampaign() {
        val campaign = campaignStorage.load(campaignId)
        _uiState.update { it.copy(campaign = campaign) }
    }

    fun loadTurns() {
        val indices = turnStorage.listTurnIndices(campaignId)
        val turns = indices.mapNotNull { turnStorage.loadTurn(campaignId, it) }
        _uiState.update { it.copy(turns = turns) }
    }

    fun sendPlayerInput(input: String) {
        val text = input.trim()
        if (text.isBlank() || _uiState.value.isStreaming) return

        _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = null) }

        activeGenerationJob = viewModelScope.launch {
            try {
                orchestrator.executeTurn(
                    campaignId = campaignId,
                    playerInput = text,
                    targetTurnIndex = null,
                    onChunk = { chunk ->
                        _uiState.update { it.copy(streamingText = it.streamingText + chunk) }
                    }
                )
                loadCampaign()
                loadTurns()
                _uiState.update { it.copy(isStreaming = false, streamingText = "") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = "Generation failed: ${e.message}"
                    )
                }
            } finally {
                activeGenerationJob = null
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { it.copy(isStreaming = false, streamingText = "") }
        loadTurns()
    }

    fun switchVariant(turnIndex: Int, variantIndex: Int) {
        _uiState.update {
            it.copy(selectedVariantIndices = it.selectedVariantIndices + (turnIndex to variantIndex))
        }
    }

    fun regenerateTurn(turnIndex: Int) {
        val turn = _uiState.value.turns.find { it.index == turnIndex } ?: return
        if (_uiState.value.isStreaming) return

        _uiState.update { it.copy(isStreaming = true, streamingText = "", errorMessage = null) }

        activeGenerationJob = viewModelScope.launch {
            try {
                orchestrator.executeTurn(
                    campaignId = campaignId,
                    playerInput = turn.playerInput,
                    targetTurnIndex = turnIndex,
                    onChunk = { chunk ->
                        _uiState.update { it.copy(streamingText = it.streamingText + chunk) }
                    }
                )
                loadCampaign()
                loadTurns()

                val updatedTurn = turnStorage.loadTurn(campaignId, turnIndex)
                val newVariantIndex = (updatedTurn?.variants?.size ?: 1) - 1
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        selectedVariantIndices = it.selectedVariantIndices + (turnIndex to newVariantIndex)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = "Regeneration failed: ${e.message}"
                    )
                }
            } finally {
                activeGenerationJob = null
            }
        }
    }

    fun deleteTurn(turnIndex: Int) {
        try {
            turnStorage.deleteTurn(campaignId, turnIndex)
            loadTurns()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Delete failed: ${e.message}") }
        }
    }

    fun editAndResend(turnIndex: Int, newInput: String) {
        deleteTurn(turnIndex)
        sendPlayerInput(newInput)
    }

    fun showStageDetails(turnIndex: Int?) {
        _uiState.update { it.copy(activeStageDetailsTurn = turnIndex) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
