package dev.diegesis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.diegesis.app.data.storage.CampaignStorage
import dev.diegesis.app.data.storage.MemoryStorage
import dev.diegesis.app.data.storage.NpcStorage
import dev.diegesis.app.data.storage.SettingsStorage
import dev.diegesis.app.data.storage.TurnStorage
import dev.diegesis.app.engine.PipelineOrchestrator
import dev.diegesis.app.engine.ai.DefaultAiCaller
import dev.diegesis.app.ui.campaign.CampaignCreateScreen
import dev.diegesis.app.ui.campaign.CampaignCreateViewModel
import dev.diegesis.app.ui.campaign.CampaignListScreen
import dev.diegesis.app.ui.campaign.CampaignListViewModel
import dev.diegesis.app.ui.memories.MemoriesScreen
import dev.diegesis.app.ui.memories.MemoriesViewModel
import dev.diegesis.app.ui.npc.NpcManagementScreen
import dev.diegesis.app.ui.npc.NpcViewModel
import dev.diegesis.app.ui.settings.SettingsScreen
import dev.diegesis.app.ui.settings.SettingsViewModel
import dev.diegesis.app.ui.story.StoryScreen
import dev.diegesis.app.ui.story.StoryViewModel
import dev.diegesis.app.ui.theme.DiegesisColors
import dev.diegesis.app.ui.theme.DiegesisDarkColorScheme
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

sealed class Screen {
    object CampaignList : Screen()
    object CampaignCreate : Screen()
    data class Story(val campaignId: String) : Screen()
    data class NpcManagement(val campaignId: String) : Screen()
    data class Memories(val campaignId: String) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DiegesisDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DiegesisColors.Bg,
                ) {
                    DiegesisApp(filesDir = filesDir, httpClient = httpClient)
                }
            }
        }
    }
}

@Composable
fun DiegesisApp(
    filesDir: File,
    httpClient: OkHttpClient
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.CampaignList) }

    // Storage instances
    val campaignStorage = remember { CampaignStorage(filesDir) }
    val npcStorage = remember { NpcStorage(filesDir) }
    val settingsStorage = remember { SettingsStorage(filesDir) }
    val turnStorage = remember { TurnStorage(filesDir) }
    val memoryStorage = remember { MemoryStorage(filesDir) }

    // Settings are reactive: reloaded from disk on every navigation, and the
    // caller is rebuilt whenever they change. No restart required.
    var settings by remember { mutableStateOf(settingsStorage.load()) }
    LaunchedEffect(currentScreen) {
        settings = settingsStorage.load()
    }
    val aiCaller = remember(settings) {
        DefaultAiCaller(
            thinkProvider = settings.thinkModel.provider,
            thinkModel = settings.thinkModel.model,
            writeProvider = settings.writeModel.provider,
            writeModel = settings.writeModel.model,
            openaiBaseUrl = settings.openaiBaseUrl,
            openaiApiKey = settings.openaiApiKey,
            anthropicApiKey = settings.anthropicApiKey,
            language = settings.language,
            thinkMaxTokens = settings.thinkMaxTokens,
            writeMaxTokens = settings.writeMaxTokens,
            client = httpClient
        )
    }

    // System back button handling
    BackHandler(enabled = currentScreen !is Screen.CampaignList) {
        currentScreen = when (val screen = currentScreen) {
            is Screen.Story -> Screen.CampaignList
            is Screen.NpcManagement -> Screen.Story(screen.campaignId)
            is Screen.Memories -> Screen.Story(screen.campaignId)
            is Screen.CampaignCreate -> Screen.CampaignList
            is Screen.Settings -> Screen.CampaignList
            is Screen.CampaignList -> Screen.CampaignList
        }
    }

    when (val screen = currentScreen) {
        is Screen.CampaignList -> {
            val viewModel = remember {
                CampaignListViewModel(campaignStorage)
            }
            CampaignListScreen(
                viewModel = viewModel,
                onOpenCampaign = { campaignId ->
                    currentScreen = Screen.Story(campaignId)
                },
                onCreateCampaign = {
                    currentScreen = Screen.CampaignCreate
                },
                onOpenSettings = {
                    currentScreen = Screen.Settings
                }
            )
        }

        is Screen.CampaignCreate -> {
            val viewModel = remember(aiCaller) {
                CampaignCreateViewModel(campaignStorage, aiCaller)
            }
            CampaignCreateScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.CampaignList },
                onCampaignCreated = { campaignId ->
                    currentScreen = Screen.Story(campaignId)
                }
            )
        }

        is Screen.Story -> {
            val viewModel = remember(screen.campaignId, aiCaller) {
                val orchestrator = PipelineOrchestrator(
                    aiCaller = aiCaller,
                    campaignStorage = campaignStorage,
                    npcStorage = npcStorage,
                    turnStorage = turnStorage,
                    memoryStorage = memoryStorage,
                    contextWindowTokens = settings.contextWindowTokens,
                    writeMaxTokens = settings.writeMaxTokens
                )
                StoryViewModel(
                    campaignId = screen.campaignId,
                    orchestrator = orchestrator,
                    campaignStorage = campaignStorage,
                    turnStorage = turnStorage
                )
            }
            // StoryViewModel is constructed by hand inside remember {}, so it
            // is never attached to a ViewModelStore and nothing cancels its
            // coroutine scope when this branch leaves composition or the
            // aiCaller key churns after a settings reload. Without this,
            // an in-flight generation keeps running as an orphan and races a
            // freshly created ViewModel for the same turn index (which used
            // to surface as extra swipeable variants on one turn).
            DisposableEffect(viewModel) {
                onDispose { viewModel.stopGeneration() }
            }
            StoryScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.CampaignList },
                onOpenNpcs = { currentScreen = Screen.NpcManagement(screen.campaignId) },
                onOpenMemories = { currentScreen = Screen.Memories(screen.campaignId) }
            )
        }

        is Screen.NpcManagement -> {
            val viewModel = remember(screen.campaignId) {
                NpcViewModel(npcStorage)
            }
            NpcManagementScreen(
                viewModel = viewModel,
                campaignId = screen.campaignId,
                onBack = { currentScreen = Screen.Story(screen.campaignId) }
            )
        }

        is Screen.Memories -> {
            val viewModel = remember(screen.campaignId) {
                MemoriesViewModel(memoryStorage, npcStorage)
            }
            MemoriesScreen(
                viewModel = viewModel,
                campaignId = screen.campaignId,
                onBack = { currentScreen = Screen.Story(screen.campaignId) }
            )
        }

        is Screen.Settings -> {
            val viewModel = remember {
                SettingsViewModel(settingsStorage)
            }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.CampaignList }
            )
        }
    }
}
