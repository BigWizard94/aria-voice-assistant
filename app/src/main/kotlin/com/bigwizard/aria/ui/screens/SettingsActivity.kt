package com.bigwizard.aria.ui.screens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigwizard.aria.BuildConfig
import com.bigwizard.aria.R
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.service.AriaVoiceInteractionService
import kotlinx.coroutines.launch
import java.util.Locale
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.ui.theme.*

class SettingsActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            AriaTheme(darkTheme = settings.theme.isDark()) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)

    // Keyed on the AI config only, so toggling an unrelated switch (which re-emits
    // settings) no longer wipes text the user is still editing.
    val aiConfig = settings.aiConfig
    var baseUrl     by remember(aiConfig) { mutableStateOf(aiConfig.baseUrl) }
    var apiKey      by remember(aiConfig) { mutableStateOf(aiConfig.apiKey) }
    var modelName   by remember(aiConfig) { mutableStateOf(aiConfig.modelName) }
    var systemPrompt by remember(aiConfig) { mutableStateOf(aiConfig.systemPrompt) }
    var maxTokens   by remember(aiConfig) { mutableStateOf(aiConfig.maxTokens.toString()) }
    var showKey     by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

    fun editedConfig() = aiConfig.copy(
        baseUrl      = baseUrl.trim(),
        apiKey       = apiKey.trim(),
        modelName    = modelName.trim(),
        systemPrompt = systemPrompt,
        maxTokens    = maxTokens.toIntOrNull()?.coerceIn(16, 8192) ?: aiConfig.maxTokens
    )

    DisposableEffect(Unit) {
        onDispose { viewModel.clearConnectionTestResult() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveAiConfig(editedConfig())
                            scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                        }
                    ) {
                        Text(stringResource(R.string.settings_save), color = AriaVioletLight, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            // ── AI Configuration ──────────────────────────────────────────────
            // ── Default assistant ─────────────────────────────────────────────
            DefaultAssistantCard()

            Spacer(Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_ai_section), icon = Icons.Outlined.Psychology) {

                // Preset selector
                OutlinedButton(
                    onClick = { showPresets = !showPresets },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AriaVioletLight),
                    border = BorderStroke(1.dp, AriaViolet.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Preset Endpoint")
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (showPresets) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }

                if (showPresets) {
                    Spacer(Modifier.height(8.dp))
                    AiEngine.PRESET_ENDPOINTS.forEach { preset ->
                        if (preset.baseUrl.isNotBlank()) {
                            Card(
                                onClick = {
                                    baseUrl   = preset.baseUrl
                                    modelName = preset.defaultModel
                                    showPresets = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(preset.name, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(preset.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                SettingsTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = stringResource(R.string.settings_base_url),
                    placeholder = DEFAULT_AI_BASE_URL,
                    keyboardType = KeyboardType.Uri
                )
                if (baseUrl.trim().startsWith("http://") && apiKey.isNotBlank()) {
                    Text(
                        stringResource(R.string.settings_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = AriaWarning,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (showKey) R.string.settings_hide_key else R.string.settings_show_key
                                )
                            )
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                SettingsTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = stringResource(R.string.settings_model_name),
                    placeholder = DEFAULT_AI_MODEL
                )

                Spacer(Modifier.height(12.dp))

                SettingsTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = "Max Tokens",
                    placeholder = "512",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.settings_system_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6
                )

                Spacer(Modifier.height(12.dp))

                // Test connection
                Button(
                    onClick = {
                        viewModel.testAiConnection(editedConfig())
                    },
                    enabled = testResult !is ConnectionTestResult.Testing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AriaViolet)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_test_connection))
                }

                testResult?.let { result ->
                    val (text, ok) = when (result) {
                        is ConnectionTestResult.Testing -> "Testing…" to true
                        is ConnectionTestResult.Success -> "✅ Connected! Response: ${result.reply}" to true
                        is ConnectionTestResult.Failure -> "❌ Failed: ${result.message}" to false
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ok) AriaSuccess.copy(alpha = 0.1f)
                                             else AriaError.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) AriaSuccess else AriaError
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Voice Settings ────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_voice_section), icon = Icons.Outlined.RecordVoiceOver) {
                var voiceSpeed by remember(settings.voiceSpeed) { mutableFloatStateOf(settings.voiceSpeed) }
                var voicePitch by remember(settings.voicePitch) { mutableFloatStateOf(settings.voicePitch) }

                Text("Speech Speed: ${String.format(Locale.US, "%.1f", voiceSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = voiceSpeed,
                    onValueChange = { voiceSpeed = it },
                    // Previously the sliders were never persisted.
                    onValueChangeFinished = { viewModel.saveAppSettings(settings.copy(voiceSpeed = voiceSpeed)) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AriaViolet, activeTrackColor = AriaViolet)
                )

                Spacer(Modifier.height(8.dp))

                Text("Voice Pitch: ${String.format(Locale.US, "%.1f", voicePitch)}x",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = voicePitch,
                    onValueChange = { voicePitch = it },
                    onValueChangeFinished = { viewModel.saveAppSettings(settings.copy(voicePitch = voicePitch)) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AriaViolet, activeTrackColor = AriaViolet)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── App Settings ──────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_app_section), icon = Icons.Outlined.Tune) {
                SettingsToggle(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on mic tap",
                    checked = settings.hapticFeedback,
                    onCheckedChange = {
                        viewModel.saveAppSettings(settings.copy(hapticFeedback = it))
                    }
                )
                SettingsToggle(
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    checked = settings.theme == AppTheme.DARK,
                    onCheckedChange = {
                        viewModel.saveAppSettings(
                            settings.copy(theme = if (it) AppTheme.DARK else AppTheme.LIGHT)
                        )
                    }
                )
                SettingsToggle(
                    title = "Follow-up Listening",
                    subtitle = "Listen again automatically after Aria answers",
                    checked = settings.autoListen,
                    onCheckedChange = {
                        viewModel.saveAppSettings(settings.copy(autoListen = it))
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_about_section), icon = Icons.Outlined.Info) {
                AboutRow("Version", BuildConfig.VERSION_NAME)
                AboutRow("License", "Apache 2.0")
                AboutRow("Developer", "Chase Lucas | Bigwizard Media")
                AboutRow("GitHub", "github.com/BigWizard94/aria-voice-assistant")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Aria is 100% open source. No tracking, no telemetry, no cloud dependency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable Components ───────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null,
                tint = AriaViolet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = AriaVioletLight)
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

/** Shows whether Aria is the default assistant and links to the system picker. */
@Composable
fun DefaultAssistantCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefault by remember { mutableStateOf(AriaVoiceInteractionService.isDefaultAssistant(context)) }
    // Re-check when the user comes back from the system picker.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = AriaVoiceInteractionService.isDefaultAssistant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(
        onClick = { openDefaultAssistantSettings(context) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) AriaSuccess.copy(alpha = 0.1f)
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDefault) Icons.Filled.CheckCircle else Icons.Outlined.Assistant,
                contentDescription = null,
                tint = if (isDefault) AriaSuccess else AriaViolet
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.settings_default_assistant),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(
                        if (isDefault) R.string.settings_default_assistant_on
                        else R.string.settings_default_assistant_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Opens Settings → Default apps (where "Digital assistant app" lives), with fallbacks. */
fun openDefaultAssistantSettings(context: android.content.Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // try the next one
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AriaViolet)
        )
    }
}

@Composable
fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}