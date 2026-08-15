package dev.diegesis.app.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.model.SceneState
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.engine.ai.AiCaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CampaignCreateUiState(
    val title: String = "",
    val premise: String = "",
    val initialLocation: String = "Beginning",
    val playerPersona: String = "",
    val sessionPlan: String = "",
    val isGeneratingPlan: Boolean = false,
    val errorMessage: String? = null
)

class CampaignCreateViewModel(
    private val storage: CampaignStorage,
    private val aiCaller: AiCaller,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(CampaignCreateUiState())
    val uiState: StateFlow<CampaignCreateUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updatePremise(premise: String) {
        _uiState.value = _uiState.value.copy(premise = premise)
    }

    fun updateInitialLocation(location: String) {
        _uiState.value = _uiState.value.copy(initialLocation = location)
    }

    fun updatePlayerPersona(persona: String) {
        _uiState.value = _uiState.value.copy(playerPersona = persona)
    }

    fun updateSessionPlan(plan: String) {
        _uiState.value = _uiState.value.copy(sessionPlan = plan)
    }

    fun generateSessionPlan() {
        val state = _uiState.value
        if (state.premise.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Premise cannot be empty")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPlan = true, errorMessage = null)
            try {
                val systemPrompt = """
You are a tabletop RPG session planner. Given a campaign premise, generate a structured 3-act story arc outline in markdown format.

Rules:
- Organize as: Act 1 (setup), Act 2 (escalation), Act 3 (climax and resolution)
- Each act should have 2-3 key beats or scenes
- End each beat on maximum conflict or a cliffhanger
- Keep it flexible—this is a guide, not a railroad
- Use markdown headers (## Act 1, ### Beat 1, etc.)
- Total length: 200-400 words
""".trim()

                val userPrompt = """
Campaign premise:
${state.premise}

${if (state.playerPersona.isNotBlank()) "Player persona: ${state.playerPersona}\n" else ""}
Generate a session plan with a 3-act structure.
""".trim()

                val plan = StringBuilder()
                aiCaller.streamThink(systemPrompt, userPrompt).collect { delta ->
                    plan.append(delta)
                    _uiState.value = _uiState.value.copy(sessionPlan = plan.toString())
                }

                _uiState.value = _uiState.value.copy(isGeneratingPlan = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingPlan = false,
                    errorMessage = "Failed to generate plan: ${e.message}"
                )
            }
        }
    }

    fun createCampaign(onCreated: (String) -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank() || state.premise.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Title and premise are required")
            return
        }

        scope.launch {
            try {
                val campaignId = UUID.randomUUID().toString()
                val campaign = Campaign(
                    id = campaignId,
                    title = state.title,
                    premise = state.premise,
                    sessionPlan = state.sessionPlan,
                    playerPersona = state.playerPersona,
                    sceneState = SceneState(location = state.initialLocation)
                )

                withContext(ioDispatcher) {
                    storage.save(campaign)
                }

                onCreated(campaignId)
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    errorMessage = "Failed to create campaign: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
