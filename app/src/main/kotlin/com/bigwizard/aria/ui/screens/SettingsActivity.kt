package com.bigwizard.aria.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.ui.theme.*

class SettingsActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AriaTheme {
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

    var baseUrl     by remember(settings) { mutableStateOf(settings.aiConfig.baseUrl) }
    var apiKey      by remember(settings) { mutableStateOf(settings.aiConfig.apiKey) }
    var modelName   by remember(settings) { mutableStateOf(settings.aiConfig.modelName) }
    var systemPrompt by remember(settings) { mutableStateOf(settings.aiConfig.systemPrompt) }
    var maxTokens   by remember(settings) { mutableStateOf(settings.aiConfig.maxTokens.toString()) }
    var showKey     by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveAiConfig(
                                AiConfig(
                                    baseUrl      = baseUrl,
                                    apiKey       = apiKey,
                                    modelName    = modelName,
                                    systemPrompt = systemPrompt,
                                    maxTokens    = maxTokens.toIntOrNull() ?: 512
                                )
                            )
                        }
                    ) {
                        Text("Save", color = AriaVioletLight, fontWeight = FontWeight.Bold)
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
            SettingsSection(title = "AI Configuration", icon = Icons.Outlined.Psychology) {

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
                    label = "Base URL",
                    placeholder = "https://api.groq.com/openai/v1"
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle"
                            )
                        }
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                SettingsTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = "Model Name",
                    placeholder = "llama3-8b-8192"
                )

                Spacer(Modifier.height(12.dp))

                SettingsTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = "Max Tokens",
                    placeholder = "512"
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6
                )

                Spacer(Modifier.height(12.dp))

                // Test connection
                Button(
                    onClick = {
                        viewModel.testAiConnection(
                            AiConfig(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AriaViolet)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Connection")
                }

                testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.startsWith("✅"))
                                AriaSuccess.copy(alpha = 0.1f)
                            else AriaError.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.startsWith("✅")) AriaSuccess else AriaError
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Voice Settings ────────────────────────────────────────────────
            SettingsSection(title = "Voice", icon = Icons.Outlined.RecordVoiceOver) {
                var voiceSpeed by remember(settings) { mutableFloatStateOf(settings.voiceSpeed) }
                var voicePitch by remember(settings) { mutableFloatStateOf(settings.voicePitch) }

                Text("Speech Speed: ${String.format("%.1f", voiceSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = voiceSpeed,
                    onValueChange = { voiceSpeed = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AriaViolet, activeTrackColor = AriaViolet)
                )

                Spacer(Modifier.height(8.dp))

                Text("Voice Pitch: ${String.format("%.1f", voicePitch)}x",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = voicePitch,
                    onValueChange = { voicePitch = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AriaViolet, activeTrackColor = AriaViolet)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── App Settings ──────────────────────────────────────────────────
            SettingsSection(title = "App", icon = Icons.Outlined.Tune) {
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
                    title = "Read Notifications",
                    subtitle = "Aria reads incoming notifications aloud",
                    checked = settings.readNotifications,
                    onCheckedChange = {
                        viewModel.saveAppSettings(settings.copy(readNotifications = it))
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Outlined.Info) {
                AboutRow("Version", "1.0.0")
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
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
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