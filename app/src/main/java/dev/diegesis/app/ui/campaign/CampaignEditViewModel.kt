package dev.diegesis.app.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.storage.CampaignStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CampaignEditUiState(
    val title: String = "",
    val premise: String = "",
    val location: String = "",
    val playerPersona: String = "",
    val sessionPlan: String = "",
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val errorMessage: String? = null
)

class CampaignEditViewModel(
    private val storage: CampaignStorage,
    private val campaignId: String,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CampaignEditUiState())
    val uiState: StateFlow<CampaignEditUiState> = _uiState.asStateFlow()

    init {
        loadCampaign()
    }

    private fun loadCampaign() {
        scope.launch {
            try {
                val campaign = withContext(ioDispatcher) {
                    storage.load(campaignId)
                }
                if (campaign == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notFound = true,
                        errorMessage = "Campaign not found"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        title = campaign.title,
                        premise = campaign.premise,
                        location = campaign.sceneState.location,
                        playerPersona = campaign.playerPersona,
                        sessionPlan = campaign.sessionPlan,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    notFound = true,
                    errorMessage = "Failed to load campaign: ${e.message}"
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updatePremise(premise: String) {
        _uiState.value = _uiState.value.copy(premise = premise)
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(location = location)
    }

    fun updatePlayerPersona(persona: String) {
        _uiState.value = _uiState.value.copy(playerPersona = persona)
    }

    fun updateSessionPlan(plan: String) {
        _uiState.value = _uiState.value.copy(sessionPlan = plan)
    }

    fun saveCampaign(onSaved: (String) -> Unit) {
        val state = _uiState.value
        if (state.notFound) {
            _uiState.value = state.copy(errorMessage = "Campaign not found")
            return
        }
        if (state.title.isBlank() || state.premise.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Title and premise are required")
            return
        }

        scope.launch {
            try {
                val existing = withContext(ioDispatcher) {
                    storage.load(campaignId)
                }
                if (existing == null) {
                    _uiState.value = _uiState.value.copy(
                        notFound = true,
                        errorMessage = "Campaign not found"
                    )
                    return@launch
                }

                // Same id; sessions, turns, memories, NPCs live in the campaign
                // directory and are untouched. sceneState keeps presentNpcIds,
                // createdAt / model selections are preserved via copy().
                val updated = existing.copy(
                    title = state.title,
                    premise = state.premise,
                    playerPersona = state.playerPersona,
                    sessionPlan = state.sessionPlan,
                    sceneState = existing.sceneState.copy(location = state.location),
                    updatedAt = System.currentTimeMillis()
                )

                withContext(ioDispatcher) {
                    storage.save(updated)
                }

                onSaved(campaignId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save campaign: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
