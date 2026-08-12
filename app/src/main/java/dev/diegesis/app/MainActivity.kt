package dev.diegesis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.diegesis.app.ui.theme.DiegesisColors
import dev.diegesis.app.ui.theme.DiegesisDarkColorScheme
import dev.diegesis.ai.core.MessageRole
import dev.diegesis.ai.provider.Model
import dev.diegesis.ai.provider.ProviderSetting
import dev.diegesis.ai.provider.TextGenerationParams
import dev.diegesis.ai.provider.providers.OpenAIProvider
import dev.diegesis.ai.ui.UIMessage
import dev.diegesis.ai.ui.UIMessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runtime-only config, read from filesDir/hello-config.json if present.
 * Never committed; hello-config.json is in .gitignore.
 */
@Serializable
private data class HelloConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelId: String = "",
)

private val configJson = Json { ignoreUnknownKeys = true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DiegesisDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DiegesisColors.Bg,
                ) {
                    HelloStreamScreen(configFile = File(filesDir, "hello-config.json"))
                }
            }
        }
    }
}

@Composable
private fun HelloStreamScreen(configFile: File) {
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(configFile) {
        val config = withContext(Dispatchers.IO) {
            runCatching {
                if (configFile.exists()) {
                    configJson.decodeFromString<HelloConfig>(configFile.readText())
                } else null
            }.getOrNull()
        }
        config?.let {
            if (it.baseUrl.isNotBlank()) baseUrl = it.baseUrl
            if (it.apiKey.isNotBlank()) apiKey = it.apiKey
            if (it.modelId.isNotBlank()) modelId = it.modelId
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiegesisColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Diegesis — hello stream",
            color = DiegesisColors.Text,
            fontSize = 16.sp,
        )

        ConfigField(value = baseUrl, onValueChange = { baseUrl = it }, label = "Base URL")
        ConfigField(value = apiKey, onValueChange = { apiKey = it }, label = "API key")
        ConfigField(value = modelId, onValueChange = { modelId = it }, label = "Model id")

        Button(
            onClick = {
                if (streaming) return@Button
                streaming = true
                output = ""
                scope.launch {
                    runCatching {
                        streamHello(
                            baseUrl = baseUrl.trim().trimEnd('/'),
                            apiKey = apiKey.trim(),
                            modelId = modelId.trim(),
                            onDelta = { delta -> output += delta },
                        )
                    }.onFailure { e ->
                        output += "\n\n[error] ${e.message}"
                    }
                    streaming = false
                }
            },
            enabled = !streaming && modelId.isNotBlank() && baseUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = DiegesisColors.Surface2,
                contentColor = DiegesisColors.Text,
                disabledContainerColor = DiegesisColors.Surface,
                disabledContentColor = DiegesisColors.TextFaint,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (streaming) "Streaming…" else "Send hello")
        }

        Text(
            text = output.ifEmpty { "Streamed tokens appear here." },
            color = if (output.isEmpty()) DiegesisColors.TextFaint else DiegesisColors.Text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(DiegesisColors.Surface)
                .padding(12.dp),
        )
    }
}

@Composable
private fun ConfigField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = DiegesisColors.TextDim) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DiegesisColors.Text,
            unfocusedTextColor = DiegesisColors.Text,
            focusedContainerColor = DiegesisColors.Surface,
            unfocusedContainerColor = DiegesisColors.Surface,
            focusedBorderColor = DiegesisColors.TextDim,
            unfocusedBorderColor = DiegesisColors.Border,
            cursorColor = DiegesisColors.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
}

/** OpenAI-compatible streaming smoke test through the ported :ai module. */
private suspend fun streamHello(
    baseUrl: String,
    apiKey: String,
    modelId: String,
    onDelta: (String) -> Unit,
) {
    val provider = OpenAIProvider(client = httpClient)
    val setting = ProviderSetting.OpenAI(
        apiKey = apiKey,
        baseUrl = baseUrl,
    )
    val messages = listOf(
        UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("Say hello to Diegesis in one short sentence.")),
        )
    )
    provider.streamText(
        providerSetting = setting,
        messages = messages,
        params = TextGenerationParams(model = Model(modelId = modelId, displayName = modelId)),
    ).collect { chunk ->
        val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
        delta.parts.filterIsInstance<UIMessagePart.Text>().forEach { part ->
            if (part.text.isNotEmpty()) onDelta(part.text)
        }
    }
}
