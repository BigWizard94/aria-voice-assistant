package com.bigwizard.aria.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigwizard.aria.data.model.AssistantState
import com.bigwizard.aria.data.model.Message
import com.bigwizard.aria.ui.components.AriaOrb
import com.bigwizard.aria.ui.components.MessageBubble
import com.bigwizard.aria.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            viewModel.startAndBindService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()

            AriaTheme(
                darkTheme = when (settings.theme) {
                    com.bigwizard.aria.data.model.AppTheme.DARK   -> true
                    com.bigwizard.aria.data.model.AppTheme.LIGHT  -> false
                    else -> isSystemInDarkTheme()
                }
            ) {
                if (!onboardingDone) {
                    OnboardingScreen(
                        onComplete = { config ->
                            viewModel.saveAiConfig(config)
                            viewModel.completeOnboarding()
                        }
                    )
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS
            )
        )
    }

    @Composable
    private fun isSystemInDarkTheme(): Boolean {
        return androidx.compose.foundation.isSystemInDarkTheme()
    }
}

// ── Main Screen ───────────────────────────────────────────────────────────[...]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val partialText by viewModel.partialText.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Load messages for current session
    LaunchedEffect(Unit) {
        viewModel.loadMessages(viewModel.currentSessionId)
    }

    Scaffold(
        topBar = {
            AriaTopBar(
                onSettingsClick = { /* Navigate to settings */ },
                onHistoryClick  = { /* Navigate to history */ },
                onNewSession    = { viewModel.startNewSession() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Message List ──────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            EmptyConversationHint()
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
                        ) {
                            MessageBubble(message = message)
                        }
                    }
                    // Partial text (live STT)
                    if (partialText.isNotBlank()) {
                        item {
                            PartialTextBubble(text = partialText)
                        }
                    }
                }

                // ── Orb + Status ──────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Status label
                    AnimatedContent(
                        targetState = getStatusLabel(assistantState),
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "status"
                    ) { label ->
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = getStatusColor(assistantState),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // The Orb
                    AriaOrb(
                        state    = assistantState,
                        size     = 180.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Mic Button ────────────────────────────────────────────
                    MicButton(
                        state = assistantState,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (assistantState) {
                                is AssistantState.Listening -> viewModel.stopListening()
                                is AssistantState.Speaking  -> viewModel.stopListening()
                                else                        -> viewModel.startListening()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────[...]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AriaTopBar(
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNewSession: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AriaViolet, AriaCyanDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text  = "Aria",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "Open Voice Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.AddComment, contentDescription = "New conversation")
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Outlined.History, contentDescription = "History")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ── Mic Button ──────────────────────────────────────────────────────────[...]

@Composable
fun MicButton(
    state: AssistantState,
    onClick: () -> Unit
) {
    val isListening = state is AssistantState.Listening
    val isSpeaking  = state is AssistantState.Speaking

    val buttonColor = when (state) {
        is AssistantState.Listening  -> AriaCyan
        is AssistantState.Processing -> AriaVioletLight
        is AssistantState.Speaking   -> AriaCyan
        is AssistantState.Error      -> AriaError
        else                         -> AriaViolet
    }

    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "micScale"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        containerColor = buttonColor,
        contentColor   = if (state is AssistantState.Listening) AriaDarkBg else Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    ) {
        Icon(
            imageVector = when {
                isListening -> Icons.Filled.MicOff
                isSpeaking  -> Icons.Filled.VolumeUp
                else        -> Icons.Filled.Mic
            },
            contentDescription = "Microphone",
            modifier = Modifier.size(32.dp)
        )
    }
}

// ── Empty State ───────────────────────────────────────────────────────────[...]

@Composable
fun EmptyConversationHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = "👋 Hi! I'm Aria",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "Your private, open-source voice assistant.\nTap the mic and start talking!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Quick suggestion chips
        val suggestions = listOf(
            "What's the weather?",
            "Set a timer for 5 minutes",
            "Tell me a joke",
            "Call Mom"
        )
        suggestions.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                row.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { },
                        label = {
                            Text(
                                text  = suggestion,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Partial Text Bubble ───────────────────────────────────────────────────────

@Composable
fun PartialTextBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                .background(AriaViolet.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text  = text,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────[...]

fun getStatusLabel(state: AssistantState): String = when (state) {
    is AssistantState.Idle       -> "Tap to speak"
    is AssistantState.Listening  -> "Listening..."
    is AssistantState.Processing -> "Thinking..."
    is AssistantState.Speaking   -> "Speaking"
    is AssistantState.Error      -> "Error"
}

@Composable
fun getStatusColor(state: AssistantState): Color = when (state) {
    is AssistantState.Listening  -> AriaCyan
    is AssistantState.Processing -> AriaVioletLight
    is AssistantState.Speaking   -> AriaCyan
    is AssistantState.Error      -> AriaError
    else                         -> MaterialTheme.colorScheme.onSurfaceVariant
}
