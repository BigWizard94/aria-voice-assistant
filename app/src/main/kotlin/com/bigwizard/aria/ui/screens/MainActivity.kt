package com.bigwizard.aria.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bigwizard.aria.R
import com.bigwizard.aria.data.model.AppTheme
import com.bigwizard.aria.data.model.AssistantState
import com.bigwizard.aria.data.model.Message
import com.bigwizard.aria.ui.components.AriaOrb
import com.bigwizard.aria.ui.components.MessageBubble
import com.bigwizard.aria.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Listening was requested (mic tap / assistant invocation) before RECORD_AUDIO was granted. */
    private var listenAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Whatever was granted, (re)connect: the service only goes foreground with the mic permission.
        viewModel.connectToService()
        if (hasMicPermission()) {
            if (listenAfterPermission) viewModel.startListening()
        } else if (listenAfterPermission) {
            viewModel.reportMicPermissionDenied()
        }
        listenAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until we know whether to show onboarding (avoids a flash of it).
        splash.setKeepOnScreenCondition { viewModel.onboardingDone.value == null }

        requestStartupPermissions()
        if (savedInstanceState == null) handleAssistIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()

            AriaTheme(darkTheme = settings.theme.isDark()) {
                when (onboardingDone) {
                    null  -> Unit // splash screen still showing
                    false -> OnboardingScreen(
                        onComplete = { config -> viewModel.completeOnboarding(config) }
                    )
                    true  -> MainScreen(
                        viewModel = viewModel,
                        onMicRequested = ::onMicRequested,
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onOpenHistory  = { startActivity(Intent(this, HistoryActivity::class.java)) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistIntent(intent)
    }

    /** Long-press home / assist gesture (directly or via AriaSession) starts listening. */
    private fun handleAssistIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_ASSIST ||
            intent.getBooleanExtra(EXTRA_START_LISTENING, false)
        ) {
            onMicRequested()
        }
    }

    private fun onMicRequested() {
        if (hasMicPermission()) {
            viewModel.startListening()
        } else {
            listenAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun requestStartupPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.connectToService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_START_LISTENING = "com.bigwizard.aria.extra.START_LISTENING"
    }
}

@Composable
fun AppTheme.isDark(): Boolean = when (this) {
    AppTheme.DARK   -> true
    AppTheme.LIGHT  -> false
    AppTheme.SYSTEM -> isSystemInDarkTheme()
}

// ── Main Screen ───────────────────────────────────────────────────────────[...]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onMicRequested: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val partialText by viewModel.partialText.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message / live transcript
    LaunchedEffect(messages.size, partialText.isNotBlank()) {
        val last = messages.size + (if (partialText.isNotBlank()) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Scaffold(
        topBar = {
            AriaTopBar(
                onSettingsClick = onOpenSettings,
                onHistoryClick  = onOpenHistory,
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
                    if (messages.isEmpty() && partialText.isBlank()) {
                        item(key = "hint") {
                            EmptyConversationHint(onSuggestion = { viewModel.sendText(it) })
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                    // Partial text (live STT)
                    if (partialText.isNotBlank()) {
                        item(key = "partial") {
                            PartialTextBubble(text = partialText)
                        }
                    }
                }

                // ── Orb + Status ──────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp, start = 24.dp, end = 24.dp)
                ) {
                    val statusLabel = statusLabel(assistantState)
                    // Status label (announced to TalkBack as it changes)
                    AnimatedContent(
                        targetState = statusLabel,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "status"
                    ) { label ->
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = getStatusColor(assistantState),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    (assistantState as? AssistantState.Preparing)?.let { preparing ->
                        val progress = preparing.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.width(180.dp).padding(bottom = 8.dp),
                                color = AriaViolet
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.width(180.dp).padding(bottom = 8.dp),
                                color = AriaViolet
                            )
                        }
                    }

                    // The Orb (decorative; the status text carries the meaning)
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
                            if (settings.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            when (assistantState) {
                                is AssistantState.Listening,
                                is AssistantState.Speaking  -> viewModel.stopListening()
                                else                        -> onMicRequested()
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
                        text  = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = stringResource(R.string.app_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.cd_new_session))
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.cd_history))
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
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
    val description = stringResource(
        when {
            isListening -> R.string.cd_mic_stop
            isSpeaking  -> R.string.cd_stop_speaking
            else        -> R.string.cd_mic_start
        }
    )

    val buttonColor = when (state) {
        is AssistantState.Listening  -> AriaCyan
        is AssistantState.Processing,
        is AssistantState.Preparing  -> AriaVioletLight
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
                isSpeaking  -> Icons.AutoMirrored.Filled.VolumeUp
                else        -> Icons.Filled.Mic
            },
            contentDescription = description,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ── Empty State ───────────────────────────────────────────────────────────[...]

@Composable
fun EmptyConversationHint(onSuggestion: (String) -> Unit = {}) {
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
                        onClick = { onSuggestion(suggestion) },
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

@Composable
fun statusLabel(state: AssistantState): String = when (state) {
    is AssistantState.Idle       -> stringResource(R.string.state_idle)
    is AssistantState.Preparing  -> state.message
    is AssistantState.Listening  -> stringResource(R.string.state_listening)
    is AssistantState.Processing -> stringResource(R.string.state_processing)
    is AssistantState.Speaking   -> stringResource(R.string.state_speaking)
    // Show the actual reason (model download failed, mic denied, …) instead of just "Error".
    is AssistantState.Error      -> state.message
}

@Composable
fun getStatusColor(state: AssistantState): Color = when (state) {
    is AssistantState.Listening  -> AriaCyan
    is AssistantState.Processing,
    is AssistantState.Preparing  -> AriaVioletLight
    is AssistantState.Speaking   -> AriaCyan
    is AssistantState.Error      -> AriaError
    else                         -> MaterialTheme.colorScheme.onSurfaceVariant
}
