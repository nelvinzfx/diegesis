package dev.diegesis.app.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.storage.CampaignStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CampaignListUiState(
    val campaigns: List<Campaign> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CampaignListViewModel(
    private val storage: CampaignStorage,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CampaignListUiState())
    val uiState: StateFlow<CampaignListUiState> = _uiState.asStateFlow()

    init {
        loadCampaigns()
    }

    fun loadCampaigns() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val campaignIds = withContext(Dispatchers.IO) {
                    storage.list()
                }
                val campaigns = campaignIds.mapNotNull { id ->
                    withContext(Dispatchers.IO) {
                        storage.load(id)
                    }
                }.sortedByDescending { it.updatedAt }

                _uiState.value = _uiState.value.copy(
                    campaigns = campaigns,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load campaigns: ${e.message}"
                )
            }
        }
    }

    fun deleteCampaign(campaignId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    storage.delete(campaignId)
                }
                loadCampaigns()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete campaign: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
