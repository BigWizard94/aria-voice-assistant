package com.bigwizard.aria.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.ai.EndpointPreset
import com.bigwizard.aria.data.model.AiConfig
import com.bigwizard.aria.ui.theme.*

/**
 * OnboardingScreen — First-run setup wizard.
 *
 * Pages:
 *  1. Welcome & intro
 *  2. Choose AI endpoint (preset or custom)
 *  3. Enter API key
 *  4. Set as default assistant guide
 *  5. Ready!
 */
@Composable
fun OnboardingScreen(
    onComplete: (AiConfig) -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableStateOf(AiEngine.PRESET_ENDPOINTS[0]) }
    var customBaseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }

    val totalPages = 5

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AriaDarkBg, AriaDarkSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentPage + 1f) / totalPages },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = AriaViolet,
                trackColor = AriaViolet.copy(alpha = 0.2f)
            )

            // Page content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_page"
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ChooseEndpointPage(
                        selectedPreset = selectedPreset,
                        onPresetSelected = { preset ->
                            selectedPreset = preset
                            modelName = preset.defaultModel
                        }
                    )
                    2 -> ApiKeyPage(
                        preset   = selectedPreset,
                        apiKey   = apiKey,
                        onApiKeyChange = { apiKey = it },
                        customUrl = customBaseUrl,
                        onCustomUrlChange = { customBaseUrl = it },
                        modelName = modelName,
                        onModelNameChange = { modelName = it }
                    )
                    3 -> DefaultAssistantPage()
                    4 -> ReadyPage()
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { currentPage-- },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, AriaViolet.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(totalPages) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentPage) 10.dp else 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == currentPage) AriaViolet
                                    else AriaViolet.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Next / Finish button
                Button(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                        } else {
                            val finalUrl = if (selectedPreset.name == "Custom Endpoint")
                                customBaseUrl else selectedPreset.baseUrl
                            val finalModel = modelName.ifBlank { selectedPreset.defaultModel }
                            onComplete(
                                AiConfig(
                                    baseUrl   = finalUrl,
                                    apiKey    = apiKey,
                                    modelName = finalModel
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AriaViolet)
                ) {
                    Text(if (currentPage == totalPages - 1) "Let's Go!" else "Next")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (currentPage == totalPages - 1) Icons.Filled.RocketLaunch
                        else Icons.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

// ── Page 1: Welcome ───────────────────────────────────────────────────────────

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.radialGradient(colors = listOf(AriaViolet, AriaCyanDark))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text  = "Meet Aria",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text  = "Your private, open-source\nvoice assistant",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // Feature highlights
        val features = listOf(
            Triple(Icons.Outlined.Lock, "100% Private", "No data ever leaves your device"),
            Triple(Icons.Outlined.WifiOff, "Works Offline", "Speech recognition runs locally"),
            Triple(Icons.Outlined.Key, "Your AI Key", "Use any OpenAI-compatible model"),
            Triple(Icons.Outlined.Android, "Replace Google", "Set as your default assistant")
        )

        features.forEach { (icon, title, desc) ->
            FeatureRow(icon = icon, title = title, description = desc)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AriaViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AriaVioletLight, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f))
        }
    }
}

// ── Page 2: Choose Endpoint ───────────────────────────────────────────────────

@Composable
fun ChooseEndpointPage(
    selectedPreset: EndpointPreset,
    onPresetSelected: (EndpointPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Choose Your AI Brain", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Aria works with any OpenAI-compatible API.\nGroq is free and ultra-fast — perfect to start!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        AiEngine.PRESET_ENDPOINTS.forEach { preset ->
            EndpointCard(
                preset = preset,
                isSelected = preset.name == selectedPreset.name,
                onClick = { onPresetSelected(preset) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun EndpointCard(
    preset: EndpointPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AriaViolet.copy(alpha = 0.25f)
                             else AriaDarkCard
        ),
        border = if (isSelected) BorderStroke(2.dp, AriaViolet) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = AriaViolet)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(preset.description, style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
                if (preset.defaultModel.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Default: ${preset.defaultModel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AriaCyan.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── Page 3: API Key ───────────────────────────────────────────────────────────

@Composable
fun ApiKeyPage(
    preset: EndpointPreset,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    customUrl: String,
    onCustomUrlChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    val isCustom = preset.name == "Custom Endpoint"
    val isLocal  = preset.baseUrl.contains("localhost") || preset.baseUrl.contains("127.0.0.1")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Connect Your AI", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Selected: ${preset.name}", style = MaterialTheme.typography.bodyMedium,
            color = AriaVioletLight)
        Spacer(Modifier.height(24.dp))

        // Custom URL field
        if (isCustom) {
            OutlinedTextField(
                value = customUrl,
                onValueChange = onCustomUrlChange,
                label = { Text("Base URL") },
                placeholder = { Text("https://your-api.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                colors = ariaTextFieldColors(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
        }

        // API Key field (skip for local endpoints)
        if (!isLocal) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = ariaTextFieldColors(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            // Get key hint
            Card(
                colors = CardDefaults.cardColors(containerColor = AriaCyan.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = AriaCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (preset.name) {
                            "Groq (Free & Ultra-Fast)" -> "Get a free key at console.groq.com"
                            "OpenRouter (100+ Models)" -> "Get a key at openrouter.ai/keys"
                            "OpenAI"                   -> "Get a key at platform.openai.com"
                            "Together AI"              -> "Get a key at api.together.xyz"
                            else -> "Enter your API key for ${preset.name}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AriaCyan.copy(alpha = 0.9f)
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = AriaSuccess.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = AriaSuccess, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No API key needed for local endpoints!",
                        style = MaterialTheme.typography.bodySmall, color = AriaSuccess)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Model name
        OutlinedTextField(
            value = modelName,
            onValueChange = onModelNameChange,
            label = { Text("Model Name") },
            placeholder = { Text(preset.defaultModel.ifBlank { "model-name" }) },
            modifier = Modifier.fillMaxWidth(),
            colors = ariaTextFieldColors(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        Text("Your API key is stored only on your device and never shared.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center)
    }
}

// ── Page 4: Default Assistant ─────────────────────────────────────────────────

@Composable
fun DefaultAssistantPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Android, contentDescription = null,
            tint = AriaVioletLight, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("Replace Google Assistant", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("To use Aria when you long-press the home button:",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        val steps = listOf(
            "1" to "Open Settings on your phone",
            "2" to "Go to Apps → Default Apps",
            "3" to "Tap Digital Assistant App",
            "4" to "Select Aria from the list",
            "5" to "Long-press home button to test!"
        )

        steps.forEach { (num, step) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AriaViolet),
                    contentAlignment = Alignment.Center
                ) {
                    Text(num, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("You can skip this and do it later in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
    }
}

// ── Page 5: Ready ─────────────────────────────────────────────────────────────

@Composable
fun ReadyPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚀", fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text("Aria is Ready!", style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Tap the mic and say anything.\nAria is listening.",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        val tips = listOf(
            "💡 Try: \"Set a timer for 10 minutes\"",
            "💡 Try: \"Call Mom\"",
            "💡 Try: \"What's the capital of Japan?\"",
            "💡 Try: \"Open YouTube\"",
            "💡 Try: \"Tell me a joke\""
        )
        tips.forEach { tip ->
            Text(tip, style = MaterialTheme.typography.bodyMedium,
                color = AriaCyan.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
fun ariaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AriaViolet,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor    = AriaViolet,
    unfocusedLabelColor  = Color.White.copy(alpha = 0.5f),
    cursorColor          = AriaViolet,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White.copy(alpha = 0.8f)
)