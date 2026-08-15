package dev.diegesis.app.ui.campaign

import dev.diegesis.app.data.model.Campaign
import dev.diegesis.app.data.storage.CampaignStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CampaignListViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var tempDir: File
    private lateinit var storage: CampaignStorage
    private lateinit var viewModel: CampaignListViewModel

    @Before
    fun setup() {
        tempDir = tmp.newFolder("campaign_list_test")
        storage = CampaignStorage(tempDir)
        viewModel = CampaignListViewModel(
            storage = storage,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `loadCampaigns returns empty list when no campaigns exist`() = runBlocking {
        val state = viewModel.uiState.value
        assertTrue(state.campaigns.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadCampaigns returns campaigns sorted by updatedAt descending`() = runBlocking {
        val campaign1 = Campaign(
            id = "id1",
            title = "Campaign 1",
            premise = "Premise 1",
            sessionPlan = "",
            updatedAt = 1000L
        )
        val campaign2 = Campaign(
            id = "id2",
            title = "Campaign 2",
            premise = "Premise 2",
            sessionPlan = "",
            updatedAt = 2000L
        )
        storage.save(campaign1)
        storage.save(campaign2)

        viewModel.loadCampaigns()

        val state = viewModel.uiState.value
        assertEquals(2, state.campaigns.size)
        assertEquals("id2", state.campaigns[0].id) // Most recent first
        assertEquals("id1", state.campaigns[1].id)
    }

    @Test
    fun `deleteCampaign removes campaign and reloads list`() = runBlocking {
        val campaign = Campaign(
            id = "test-id",
            title = "Test Campaign",
            premise = "Test premise",
            sessionPlan = ""
        )
        storage.save(campaign)
        viewModel.loadCampaigns()

        assertEquals(1, viewModel.uiState.value.campaigns.size)

        viewModel.deleteCampaign("test-id")

        assertTrue(viewModel.uiState.value.campaigns.isEmpty())
    }

    @Test
    fun `clearError removes error message`() = runBlocking {
        viewModel.deleteCampaign("nonexistent-id")
        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
