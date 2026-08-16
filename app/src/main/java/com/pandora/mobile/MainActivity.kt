package com.pandora.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pandora.mobile.linux.CodexLimitWindow
import com.pandora.mobile.linux.CodexLimitsState
import com.pandora.mobile.linux.ChatAttachment
import com.pandora.mobile.linux.ChatAttachmentKind
import com.pandora.mobile.linux.ChatMessage
import com.pandora.mobile.linux.ChatRole
import com.pandora.mobile.linux.SystemMessageTone
import com.pandora.mobile.linux.CodexChatSession
import com.pandora.mobile.linux.CodexChatState
import com.pandora.mobile.linux.isGeneralChatWorkingDirectory
import com.pandora.mobile.linux.isReservedChatWorkspacePath
import com.pandora.mobile.linux.CodexThreadCatalog
import com.pandora.mobile.linux.CodexThreadSummary
import com.pandora.mobile.linux.CodexUsageReader
import com.pandora.mobile.linux.ArchiveCatalog
import com.pandora.mobile.linux.agentDisplayText
import com.pandora.mobile.linux.PtyTerminalSession
import com.pandora.mobile.linux.PandoraProject
import com.pandora.mobile.linux.ProjectCatalog
import com.pandora.mobile.linux.RootfsInstaller
import com.pandora.mobile.linux.ZmxSessionCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
private val SoftSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val AccentSurface: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Terminal = Color(0xFF111210)
private val TerminalText = Color(0xFFD8E4D1)

private enum class Screen { Onboarding, Home, Chat, Container, Settings, Voice, CustomProvider, Skills, AdbSetup, Archive }

private sealed interface OnboardingState {
    data object Welcome : OnboardingState
    data class Installing(val detail: String) : OnboardingState
    data object Ready : OnboardingState
    data class Failed(val detail: String) : OnboardingState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PandoraApp() }
    }
}

@Composable
private fun PandoraApp() {
    val context = LocalContext.current.applicationContext as PandoraApplication
    val sessionManager = context.terminalSessions
    val chatManager = context.chatSessions
    val sessions by sessionManager.sessions.collectAsState()
    val sessionRevision by sessionManager.revision.collectAsState()
    val chatSessions by chatManager.sessions.collectAsState()
    val adbState by context.adbPlugin.state.collectAsState()
    var screen by rememberSaveable {
        mutableStateOf(
            if (AppSettings.onboardingCompleted(context)) Screen.Home else Screen.Onboarding,
        )
    }
    var settingsReturnScreen by rememberSaveable { mutableStateOf(Screen.Home) }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChatSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var themePreference by remember { mutableStateOf(AppSettings.theme(context)) }
    PandoraTheme(themePreference) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expandedWorkspace = maxWidth >= 840.dp
                val selectedChat = chatManager.find(selectedChatSessionId)
                val homePane: @Composable (Modifier, Boolean) -> Unit = { modifier, sidebar ->
                    HomeScreen(
                        sessions = sessions,
                        revision = sessionRevision,
                        chatSessions = chatSessions,
                        selectedChatSessionId = selectedChatSessionId,
                        sidebarMode = sidebar,
                        modifier = modifier,
                        onNewChat = { cwd ->
                            selectedChatSessionId = chatManager.create(cwd = cwd).id
                            if (!expandedWorkspace) screen = Screen.Chat
                        },
                        onOpenChat = { threadId, cwd ->
                            selectedChatSessionId = chatManager.create(threadId, cwd).id
                            if (!expandedWorkspace) screen = Screen.Chat
                        },
                        onStopChat = chatManager::stopThread,
                        onNewTerminal = {
                            selectedSessionId = sessionManager.create().id
                            screen = Screen.Container
                        },
                        onOpenTerminal = {
                            selectedSessionId = it.id
                            screen = Screen.Container
                        },
                        onRestartTerminal = { session ->
                            sessionManager.restart(session.id)?.let { restarted ->
                                selectedSessionId = restarted.id
                                screen = Screen.Container
                            }
                        },
                        onRenameTerminal = { session, title -> sessionManager.rename(session.id, title) },
                        onDeleteTerminal = { session -> sessionManager.delete(session.id) },
                        onRenamePersistentTerminal = { name, title ->
                            val session = sessionManager.attach(name)
                            sessionManager.rename(session.id, title)
                        },
                        onDeletePersistentTerminal = sessionManager::deletePersistent,
                        onOpenPersistentTerminal = { name ->
                            selectedSessionId = sessionManager.attach(name).id
                            screen = Screen.Container
                        },
                        onSettings = {
                            settingsReturnScreen = screen
                            screen = Screen.Settings
                        },
                        onCodexLogin = {
                            selectedSessionId = sessionManager.create("codex login").id
                            screen = Screen.Container
                        },
                    )
                }
                val chatPane: @Composable (CodexChatSession, Boolean) -> Unit = { chat, showBackButton ->
                    androidx.compose.runtime.key(chat.id) {
                        ChatScreen(
                            session = chat,
                            showBackButton = showBackButton,
                            onBack = {
                                selectedChatSessionId = null
                                screen = Screen.Home
                            },
                            onNewChat = {
                                val cwd = chat.workingDirectory.takeUnless {
                                    it == "/root" || isGeneralChatWorkingDirectory(it)
                                }
                                selectedChatSessionId = chatManager.create(cwd = cwd).id
                            },
                            onSettings = {
                                settingsReturnScreen = screen
                                screen = Screen.Settings
                            },
                        )
                    }
                }
                val twoPaneWorkspace: @Composable () -> Unit = {
                    Row(Modifier.fillMaxSize()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(0.28f).fillMaxSize(),
                        ) {
                            homePane(Modifier.fillMaxSize(), true)
                        }
                        Box(Modifier.fillMaxHeight().width(1.dp).background(Line))
                        Box(Modifier.weight(0.72f).fillMaxSize()) {
                            if (selectedChat == null) {
                                EmptyWorkspaceDetail()
                            } else {
                                chatPane(selectedChat, false)
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "screen"
                ) { target ->
                    when (target) {
                    Screen.Onboarding -> OnboardingScreen(
                        onSignIn = {
                            ModelProviderSettings.useCodex(context)
                            RootfsInstaller(context).refreshModelProviderConfig()
                            AppSettings.setOnboardingCompleted(context, true)
                            if (java.io.File(context.filesDir, "linux-workspace/.codex/auth.json").exists()) {
                                screen = Screen.Home
                            } else {
                                selectedSessionId = sessionManager.create("codex login").id
                                screen = Screen.Container
                            }
                        },
                        onUseCustomProvider = { provider, apiKey ->
                            runCatching {
                                if (apiKey.isNotBlank()) ModelProviderSecretStore.saveApiKey(context, apiKey)
                                ModelProviderSettings.useCustomProvider(context, provider)
                                RootfsInstaller(context).refreshModelProviderConfig()
                                AppSettings.setOnboardingCompleted(context, true)
                                screen = Screen.Home
                            }.exceptionOrNull()?.message
                        },
                        onExplore = {
                            AppSettings.setOnboardingCompleted(context, true)
                            screen = Screen.Home
                        },
                    )
                    Screen.Home -> if (expandedWorkspace) {
                        twoPaneWorkspace()
                    } else {
                        homePane(Modifier.fillMaxSize(), false)
                    }
                    Screen.Chat -> {
                        if (expandedWorkspace) {
                            twoPaneWorkspace()
                        } else if (selectedChat == null) {
                            LaunchedEffect(Unit) { screen = Screen.Home }
                        } else {
                            chatPane(selectedChat, true)
                        }
                    }
                    Screen.Container -> {
                        val session = sessionManager.find(selectedSessionId)
                        if (session == null) {
                            LaunchedEffect(Unit) { screen = Screen.Home }
                        } else {
                            ContainerScreen(
                                session = session,
                                onBack = {
                                    selectedSessionId = null
                                    screen = Screen.Home
                                },
                                onStop = {
                                    sessionManager.stop(session.id)
                                    selectedSessionId = null
                                    screen = Screen.Home
                                },
                                onRestart = {
                                    val restarted = sessionManager.restart(session.id)
                                    if (restarted != null) selectedSessionId = restarted.id
                                },
                            )
                        }
                    }
                    Screen.Settings -> SettingsScreen(
                        themePreference = themePreference,
                        onThemePreferenceChanged = {
                            AppSettings.setTheme(context, it)
                            themePreference = it
                        },
                        onBack = { screen = settingsReturnScreen },
                        onArchive = { screen = Screen.Archive },
                        onPlugins = { screen = Screen.Skills },
                        onVoice = { screen = Screen.Voice },
                        onCodexLogin = {
                            // PRoot shares Android's network namespace, so Chrome can return
                            // directly to Codex's localhost callback. This avoids the device-code
                            // polling request that is unreliable on Android.
                            selectedSessionId = sessionManager.create("codex login").id
                            screen = Screen.Container
                        },
                        onModelProviderChanged = { mode ->
                            chatManager.stopAll()
                            when (mode) {
                                ModelProviderMode.CODEX -> ModelProviderSettings.useCodex(context)
                                ModelProviderMode.CUSTOM -> {
                                    ModelProviderSettings.savedCustomProvider(context)?.let { provider ->
                                        ModelProviderSettings.useCustomProvider(context, provider)
                                    }
                                }
                            }
                            RootfsInstaller(context).refreshModelProviderConfig()
                        },
                        onChangeModelProvider = {
                            screen = Screen.CustomProvider
                        },
                        onStopAllForRepair = {
                            chatManager.stopAll()
                            sessionManager.stopAll()
                        },
                    )
                    Screen.Voice -> VoiceSettingsScreen(onBack = { screen = Screen.Settings })
                    Screen.CustomProvider -> CustomProviderOnboarding(
                        existingProvider = ModelProviderSettings.savedCustomProvider(context),
                        hasSavedApiKey = ModelProviderSecretStore.hasApiKey(context),
                        onBack = { screen = Screen.Settings },
                        onContinue = { provider, apiKey ->
                            runCatching {
                                chatManager.stopAll()
                                if (apiKey.isNotBlank()) ModelProviderSecretStore.saveApiKey(context, apiKey)
                                ModelProviderSettings.useCustomProvider(context, provider)
                                RootfsInstaller(context).refreshModelProviderConfig()
                                screen = Screen.Settings
                            }.exceptionOrNull()?.message
                        },
                    )
                    Screen.Skills -> SkillsScreen(
                        state = adbState,
                        onBack = { screen = Screen.Settings },
                        onOpenSetup = { screen = Screen.AdbSetup },
                        onEnabledChange = context.adbPlugin::setEnabled,
                    )
                    Screen.AdbSetup -> AdbSetupScreen(
                        state = adbState,
                        onBack = { screen = Screen.Skills },
                        onBeginPairing = context.adbPlugin::beginPairing,
                        onRetry = context.adbPlugin::retry,
                    )
                    Screen.Archive -> ArchiveScreen(onBack = { screen = Screen.Settings })
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    onSignIn: () -> Unit,
    onUseCustomProvider: (CustomModelProvider, String) -> String?,
    onExplore: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val installer = remember(context) { RootfsInstaller(context) }
    val scope = rememberCoroutineScope()
    var state by remember(installer) {
        mutableStateOf<OnboardingState>(
            if (installer.isSetupComplete()) OnboardingState.Ready else OnboardingState.Welcome,
        )
    }
    var setupRunning by remember { mutableStateOf(false) }

    fun startSetup() {
        if (setupRunning) return
        setupRunning = true
        state = OnboardingState.Installing("Preparing your workspace…")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    installer.installIfNeeded { detail ->
                        scope.launch { state = OnboardingState.Installing(detail) }
                    }
                }
            }
            setupRunning = false
            state = when {
                result.isFailure -> OnboardingState.Failed(
                    result.exceptionOrNull()?.message ?: "Pandora could not finish setup.",
                )
                installer.isSetupComplete() -> OnboardingState.Ready
                else -> OnboardingState.Failed(
                    "Pandora could not download every required tool. Check your connection and try again.",
                )
            }
        }
    }

    when (val current = state) {
        OnboardingState.Welcome -> OnboardingWelcome(
            continuing = installer.rootfs.exists(),
            onStart = ::startSetup,
        )
        is OnboardingState.Installing -> OnboardingSetupProgress(current.detail)
        OnboardingState.Ready -> OnboardingReady(
            onSignIn = onSignIn,
            onUseCustomProvider = onUseCustomProvider,
            onExplore = onExplore,
        )
        is OnboardingState.Failed -> OnboardingFailure(current.detail, onRetry = ::startSetup)
    }
}

@Composable
private fun OnboardingWelcome(continuing: Boolean, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        OnboardingBrand()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(34.dp))
            OnboardingHeroMark()
            Spacer(Modifier.height(28.dp))
            Text(
                "Your coding workspace, right here.",
                color = Ink,
                fontSize = 32.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Pandora brings Codex, your projects, and a real terminal together on this device.",
                color = Muted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
            Spacer(Modifier.height(30.dp))
            OnboardingValueRow(
                icon = Icons.Rounded.Folder,
                title = "Work with real projects",
                detail = "Open a folder, start fresh, or clone a GitHub repository.",
            )
            OnboardingValueRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "Build with Codex",
                detail = "Ask questions, plan changes, and let the agent edit and run code.",
            )
            OnboardingValueRow(
                icon = Icons.Rounded.Terminal,
                title = "Use a full terminal",
                detail = "Your command-line tools and sessions are always close by.",
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SoftSurface, RoundedCornerShape(14.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(11.dp))
                Text(
                    "Workspace files are stored privately in Pandora's app storage.",
                    color = Ink,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
        }
        Text(
            "One-time setup · usually 2–5 minutes · internet required",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        OnboardingPrimaryAction(
            label = if (continuing) "Continue setup" else "Set up Pandora",
            onClick = onStart,
        )
    }
}

@Composable
private fun OnboardingSetupProgress(detail: String) {
    val steps = listOf(
        "Create your private workspace",
        "Add developer tools",
        "Install Codex and terminals",
        "Finish and open Pandora",
    )
    val stage = when {
        detail.contains("SSL", ignoreCase = true) || detail.contains("utilities", ignoreCase = true) -> 1
        detail.contains("terminal", ignoreCase = true) || detail.contains("Codex", ignoreCase = true) -> 2
        detail.contains("container", ignoreCase = true) || detail.contains("Starting", ignoreCase = true) -> 3
        else -> 0
    }
    val friendlyDetail = when {
        detail.contains("Debian", ignoreCase = true) -> "Creating a private workspace on this device"
        detail.contains("SSL", ignoreCase = true) || detail.contains("utilities", ignoreCase = true) ->
            "Adding Git, Node.js, and essential command-line tools"
        detail.contains("terminal", ignoreCase = true) -> "Enabling terminal sessions that survive navigation"
        detail.contains("Codex", ignoreCase = true) -> "Installing the Codex agent"
        detail.contains("container", ignoreCase = true) || detail.contains("Starting", ignoreCase = true) ->
            "Running final checks"
        else -> "Preparing secure local storage"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        OnboardingBrand()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                "Building your workspace",
                color = Ink,
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Keep Pandora open and connected while the first-time setup finishes.",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(30.dp))
            Text(
                "Step ${stage + 1} of ${steps.size}",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(SoftSurface, CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((stage + 0.45f) / steps.size)
                        .height(6.dp)
                        .background(Accent, CircleShape),
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                friendlyDetail,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = friendlyDetail
                },
            ) {
                steps.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.dp).background(
                                when {
                                    index < stage -> Accent
                                    index == stage -> AccentSurface
                                    else -> SoftSurface
                                },
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                index < stage -> Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                index == stage -> CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Text(
                            label,
                            color = if (index <= stage) Ink else Muted,
                            fontSize = 14.sp,
                            fontWeight = if (index == stage) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Text(
            "Everything is installed inside Pandora. Android itself is not modified.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun OnboardingReady(
    onSignIn: () -> Unit,
    onUseCustomProvider: (CustomModelProvider, String) -> String?,
    onExplore: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val savedCustomProvider = remember(context) { ModelProviderSettings.savedCustomProvider(context) }
    val hasSavedApiKey = remember(context) { ModelProviderSecretStore.hasApiKey(context) }
    var configuringCustomProvider by remember { mutableStateOf(false) }
    if (configuringCustomProvider) {
        CustomProviderOnboarding(
            existingProvider = savedCustomProvider,
            hasSavedApiKey = hasSavedApiKey,
            onBack = { configuringCustomProvider = false },
            onContinue = onUseCustomProvider,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        OnboardingBrand()
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(76.dp).background(Accent, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Your workspace is ready.",
                color = Ink,
                fontSize = 32.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Choose what powers your coding agent. You can use Codex with ChatGPT or bring an OpenAI-compatible model.",
                color = Muted,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
            Spacer(Modifier.height(28.dp))
            OnboardingProviderChoice(
                title = "Use Codex",
                detail = "Sign in with ChatGPT and use your Codex plan.",
                icon = Icons.Rounded.AutoAwesome,
                primary = true,
                onClick = onSignIn,
            )
            Spacer(Modifier.height(12.dp))
            OnboardingProviderChoice(
                title = "Use Codex OSS",
                detail = "Connect your own OpenAI-compatible endpoint and models.",
                icon = Icons.Rounded.Key,
                primary = false,
                onClick = { configuringCustomProvider = true },
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    "ChatGPT sign-in happens in your browser. Bring-your-own keys are encrypted on this device.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        TextButton(
            onClick = onExplore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Explore Pandora first", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun OnboardingProviderChoice(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val background = if (primary) Ink else Paper
    val foreground = if (primary) Paper else Ink
    val supporting = if (primary) Paper.copy(alpha = 0.74f) else Muted
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .then(if (primary) Modifier.background(background, RoundedCornerShape(16.dp)) else Modifier.border(1.dp, Line, RoundedCornerShape(16.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(
                if (primary) foreground.copy(alpha = 0.13f) else AccentSurface,
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) foreground else Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = supporting, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun CustomProviderOnboarding(
    existingProvider: CustomModelProvider?,
    hasSavedApiKey: Boolean,
    onBack: () -> Unit,
    onContinue: (CustomModelProvider, String) -> String?,
) {
    var baseUrl by remember(existingProvider) { mutableStateOf(existingProvider?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var modelIds by remember(existingProvider) {
        mutableStateOf(existingProvider?.modelIds?.joinToString("\n").orEmpty())
    }
    var submitted by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val parsedModels = ModelProviderSettings.parseModelIds(modelIds)
    val urlValid = ModelProviderSettings.isSupportedUrl(baseUrl.trim())
    val apiKeyValid = apiKey.isNotBlank() || hasSavedApiKey

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Spacer(Modifier.width(4.dp))
            OnboardingBrand()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(26.dp))
            Text(
                "Bring your own model",
                color = Ink,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Codex OSS will use your provider for every agent request. The endpoint must support the OpenAI Responses API.",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saveError = null },
                label = { Text("OpenAI-compatible URL") },
                placeholder = { Text("https://provider.example.com/v1") },
                singleLine = true,
                isError = submitted && !urlValid,
                supportingText = if (submitted && !urlValid) {
                    { Text("Enter a complete HTTP or HTTPS URL.") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saveError = null },
                label = { Text("API key") },
                placeholder = { Text(if (hasSavedApiKey) "Saved key" else "sk-…") },
                singleLine = true,
                isError = submitted && !apiKeyValid,
                supportingText = if (submitted && !apiKeyValid) {
                    { Text("Enter the key issued by your provider.") }
                } else if (hasSavedApiKey && apiKey.isBlank()) {
                    { Text("Leave blank to keep the saved key.") }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = modelIds,
                onValueChange = { modelIds = it; saveError = null },
                label = { Text("Model identifiers") },
                placeholder = { Text("openai/gpt-oss-120b\nopenai/gpt-oss-20b") },
                minLines = 2,
                maxLines = 4,
                isError = submitted && parsedModels.isEmpty(),
                supportingText = {
                    Text(
                        if (submitted && parsedModels.isEmpty()) "Add at least one model identifier."
                        else "One per line or separated by commas. The first is the default.",
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().background(SoftSurface, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Your key is encrypted with Android Keystore and passed to Codex only as an environment variable.",
                    color = Ink,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            saveError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
        OnboardingPrimaryAction(
            label = "Use these models",
            onClick = {
                submitted = true
                if (!urlValid || !apiKeyValid || parsedModels.isEmpty()) return@OnboardingPrimaryAction
                saveError = onContinue(
                    CustomModelProvider(baseUrl.trim().trimEnd('/'), parsedModels),
                    apiKey,
                )
            },
        )
    }
}

@Composable
private fun OnboardingFailure(detail: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        OnboardingBrand()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(68.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(31.dp),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text("Setup paused", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(detail, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Your progress is saved. Connect to the internet and continue when you're ready.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        OnboardingPrimaryAction(label = "Try setup again", onClick = onRetry)
    }
}

@Composable
private fun OnboardingBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).background(Ink, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Paper, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Pandora", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OnboardingHeroMark() {
    Box(Modifier.size(90.dp)) {
        Box(
            Modifier.size(82.dp).background(Terminal, RoundedCornerShape(25.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = TerminalText, modifier = Modifier.size(39.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(36.dp)
                .background(Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OnboardingValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).background(AccentSurface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OnboardingPrimaryAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(Ink, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(9.dp))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Paper,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun HomeScreen(
    sessions: List<ManagedTerminalSession>,
    @Suppress("UNUSED_PARAMETER") revision: Long,
    chatSessions: List<CodexChatSession>,
    selectedChatSessionId: String? = null,
    sidebarMode: Boolean = false,
    modifier: Modifier = Modifier,
    onNewChat: (String?) -> Unit,
    onOpenChat: (String, String) -> Unit,
    onStopChat: (String) -> Unit,
    onNewTerminal: () -> Unit,
    onOpenTerminal: (ManagedTerminalSession) -> Unit,
    onRestartTerminal: (ManagedTerminalSession) -> Unit,
    onRenameTerminal: (ManagedTerminalSession, String) -> Unit,
    onDeleteTerminal: (ManagedTerminalSession) -> Unit,
    onRenamePersistentTerminal: (String, String) -> Unit,
    onDeletePersistentTerminal: (String) -> Unit,
    onOpenPersistentTerminal: (String) -> Unit,
    onSettings: () -> Unit,
    onCodexLogin: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val customProvider = remember(context) { ModelProviderSettings.customProvider(context) }
    val chatCatalog = remember(context) { CodexThreadCatalog(context) }
    val projectCatalog = remember(context) { ProjectCatalog(context) }
    val archiveCatalog = remember(context) { ArchiveCatalog(context) }
    var limitState by remember { mutableStateOf<CodexLimitsState>(CodexLimitsState.Loading) }
    var limitRefresh by remember { mutableStateOf(0) }
    var chats by remember(chatCatalog) { mutableStateOf(chatCatalog.readCached()) }
    var registeredProjects by remember(projectCatalog) { mutableStateOf(projectCatalog.readRegistered()) }
    var archive by remember(archiveCatalog) { mutableStateOf(archiveCatalog.read()) }
    var persistentTerminals by remember { mutableStateOf<List<String>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(chats.isEmpty()) }
    var renameEntry by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var removeEntry by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var deleteChat by remember { mutableStateOf<CodexThreadSummary?>(null) }
    var renameProject by remember { mutableStateOf<PandoraProject?>(null) }
    var archiveProject by remember { mutableStateOf<PandoraProject?>(null) }
    var deleteProject by remember { mutableStateOf<PandoraProject?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var addProjectStep by remember { mutableStateOf<AddProjectStep?>(null) }
    var emptyProjectsPromptDismissed by remember(context) {
        mutableStateOf(AppSettings.emptyProjectsPromptDismissed(context))
    }
    var collapsedProjects by remember(context) { mutableStateOf(AppSettings.collapsedProjectPaths(context)) }
    val actionScope = rememberCoroutineScope()
    LaunchedEffect(limitRefresh, sessions.size) {
        limitState = CodexLimitsState.Loading
        catalogLoading = chats.isEmpty()
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                val usage = async {
                    if (customProvider == null) CodexUsageReader(context).read()
                    else CodexLimitsState.Unavailable
                }
                val chatThreads = async { runCatching { chatCatalog.read() }.getOrNull() }
                val terminals = async { runCatching { ZmxSessionCatalog(context).read() }.getOrNull() }
                Triple(usage.await(), chatThreads.await(), terminals.await())
            }
        }
        limitState = loaded.first
        loaded.second?.let { chats = it }
        loaded.third?.let { persistentTerminals = it }
        registeredProjects = withContext(Dispatchers.IO) { projectCatalog.readRegistered() }
        archive = withContext(Dispatchers.IO) { archiveCatalog.read() }
        catalogLoading = false
    }

    val activeChats = remember(chats, archive) {
        chats.filterNot { it.id in archive.chatIds || it.cwd in archive.projectPaths }
    }
    val entries = remember(activeChats, sessions, persistentTerminals, revision) {
        buildList<WorkspaceEntry> {
            activeChats.forEach { add(WorkspaceEntry.Chat(it)) }
            sessions.forEach { add(WorkspaceEntry.TerminalSession(it)) }
            val attachedNames = sessions.mapTo(mutableSetOf()) { it.persistentSessionName }
            persistentTerminals.filterNot(attachedNames::contains).forEach {
                add(WorkspaceEntry.PersistentTerminal(it))
            }
        }.sortedByDescending { it.updatedAtMillis }
    }
    val projects = remember(activeChats, registeredProjects, archive) {
        val visibleRegistered = registeredProjects.filterNot { it.path in archive.projectPaths }
        val registeredByPath = visibleRegistered.associateBy { it.path }
        (
            visibleRegistered.map { it.path } +
                activeChats.map { it.cwd }.filter { it != "/root" && !isReservedChatWorkspacePath(it) }
        )
            .distinct()
            .filter { it.startsWith("/root/") }
            .map { registeredByPath[it] ?: PandoraProject(it) }
            .sortedWith(
                compareByDescending<PandoraProject> { it.pinned }
                    .thenByDescending { project ->
                        activeChats.filter { it.cwd == project.path }.maxOfOrNull { it.updatedAtMillis } ?: 0L
                    }
                    .thenBy { it.name.lowercase() },
            )
    }
    val projectPaths = remember(projects) { projects.map { it.path } }
    val projectChats = remember(activeChats, projectPaths) {
        projectPaths.associateWith { path ->
            activeChats.filter { it.cwd == path }.map(WorkspaceEntry::Chat)
        }
    }
    val otherEntries = remember(entries, projectPaths) {
        entries.filterNot { entry -> entry is WorkspaceEntry.Chat && entry.chat.cwd in projectPaths }
    }

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (sidebarMode) 14.dp else 18.dp, vertical = 14.dp)
    ) {
        if (sidebarMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pandora",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Muted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onNewTerminal, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Terminal, contentDescription = "New terminal", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(Ink, RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Start a new general chat", onClick = { onNewChat(null) })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("New chat", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SoftSurface, CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Pandora", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Ink, CircleShape)
                        .clickable(onClick = onNewTerminal),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = "New terminal", tint = Paper, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item("projects-heading") {
                WorkspaceSectionHeading(
                    label = "PROJECTS",
                    actionContentDescription = "Add project",
                    onAction = { addProjectStep = AddProjectStep.Menu },
                )
            }
            if (projects.isEmpty() && (catalogLoading || !emptyProjectsPromptDismissed)) {
                item("projects-empty") {
                    EmptyProjectsRow(
                        loading = catalogLoading,
                        compact = sidebarMode,
                        onAddProject = { addProjectStep = AddProjectStep.Menu },
                        onDismiss = {
                            emptyProjectsPromptDismissed = true
                            AppSettings.dismissEmptyProjectsPrompt(context)
                        },
                    )
                }
            }
            projects.forEach { project ->
                val path = project.path
                val projectEntries = projectChats[path].orEmpty()
                val expanded = path !in collapsedProjects
                item("project:$path") {
                    ProjectHeader(
                        project = project,
                        chatCount = projectEntries.size,
                        expanded = expanded,
                        onToggle = {
                            collapsedProjects = if (expanded) collapsedProjects + path else collapsedProjects - path
                            AppSettings.setProjectExpanded(context, path, !expanded)
                        },
                        onNewChat = { onNewChat(path) },
                        onRename = { renameProject = project; actionError = null },
                        onPin = {
                            actionScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { projectCatalog.setPinned(path, !project.pinned) }
                                }
                                result.onSuccess { updated ->
                                    registeredProjects = (registeredProjects.filterNot { it.path == path } + updated)
                                }.onFailure { actionError = it.message ?: "Could not update this project" }
                            }
                        },
                        onArchive = { archiveProject = project; actionError = null },
                        onDelete = { deleteProject = project; actionError = null },
                    )
                }
                if (expanded) {
                    items(projectEntries, key = { it.key }) { entry ->
                        WorkspaceEntryRow(
                            entry = entry,
                            chatSessions = chatSessions,
                            selectedChatSessionId = selectedChatSessionId,
                            dense = sidebarMode,
                            nested = true,
                            onOpenChat = onOpenChat,
                            onStopChat = onStopChat,
                            onOpenTerminal = onOpenTerminal,
                            onRestartTerminal = onRestartTerminal,
                            onOpenPersistentTerminal = onOpenPersistentTerminal,
                            onRename = {
                                actionError = null
                                renameEntry = entry
                            },
                            onArchive = {
                                actionError = null
                                removeEntry = entry
                            },
                            onDelete = {
                                actionError = null
                                deleteChat = entry.chat
                            },
                        )
                    }
                }
                item("project-line:$path") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 7.dp).height(1.dp).background(Line))
                }
            }

            if (otherEntries.isNotEmpty()) {
                item("other-heading") { WorkspaceSectionHeading(label = "CHATS") }
                items(otherEntries, key = { it.key }) { entry ->
                    WorkspaceEntryRow(
                        entry = entry,
                        chatSessions = chatSessions,
                        selectedChatSessionId = selectedChatSessionId,
                        dense = sidebarMode,
                        onOpenChat = onOpenChat,
                        onStopChat = onStopChat,
                        onOpenTerminal = onOpenTerminal,
                        onRestartTerminal = onRestartTerminal,
                        onOpenPersistentTerminal = onOpenPersistentTerminal,
                        onRename = {
                            actionError = null
                            renameEntry = entry
                        },
                        onArchive = if (entry is WorkspaceEntry.Chat) ({
                            actionError = null
                            removeEntry = entry
                        }) else null,
                        onDelete = {
                            actionError = null
                            if (entry is WorkspaceEntry.Chat) deleteChat = entry.chat else removeEntry = entry
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (customProvider == null) {
                    CodexLimitsFooter(
                        state = limitState,
                        onRefresh = { limitRefresh++ },
                        onSignIn = onCodexLogin,
                    )
                } else {
                    Row(
                        Modifier.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(Color(0xFF55A76A), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Codex OSS · ${customProvider.defaultModel}",
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!sidebarMode) {
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .background(Ink, RoundedCornerShape(24.dp))
                        .clickable(onClickLabel = "Start a new general chat", onClick = { onNewChat(null) })
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text("New chat", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    addProjectStep?.let { initialStep ->
        AddProjectDialog(
            catalog = projectCatalog,
            initialStep = initialStep,
            registeredPaths = projectPaths.toSet(),
            onDismiss = { addProjectStep = null },
            onProjectReady = { project ->
                registeredProjects = (registeredProjects + project).distinctBy { it.path }
                addProjectStep = null
                onNewChat(project.path)
            },
        )
    }

    renameProject?.let { project ->
        RenameProjectDialog(
            project = project,
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    renameProject = null
                    actionError = null
                }
            },
            onRename = { proposedName ->
                actionScope.launch {
                    actionInFlight = true
                    actionError = null
                    val result = withContext(Dispatchers.IO) {
                        runCatching { projectCatalog.rename(project.path, proposedName) }
                    }
                    actionInFlight = false
                    result.onSuccess { updated ->
                        registeredProjects = registeredProjects.filterNot { it.path == project.path } + updated
                        renameProject = null
                    }.onFailure {
                        actionError = it.message ?: "Could not rename this project"
                    }
                }
            },
        )
    }

    archiveProject?.let { project ->
        ArchiveProjectDialog(
            project = project,
            chatCount = chats.count { it.cwd == project.path },
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    archiveProject = null
                    actionError = null
                }
            },
            onArchive = {
                actionScope.launch {
                    actionInFlight = true
                    actionError = null
                    val projectChatIds = chats.filter { it.cwd == project.path }.map { it.id }
                    projectChatIds.forEach(onStopChat)
                    val result = withContext(Dispatchers.IO) {
                        runCatching { archiveCatalog.setProjectArchived(project.path, true) }
                    }
                    actionInFlight = false
                    result.onSuccess { updated ->
                        archive = updated
                        archiveProject = null
                    }.onFailure {
                        actionError = it.message ?: "Could not archive this project"
                    }
                }
            },
        )
    }

    deleteProject?.let { project ->
        DeleteProjectDialog(
            project = project,
            chatCount = chats.count { it.cwd == project.path },
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    deleteProject = null
                    actionError = null
                }
            },
            onDelete = {
                actionScope.launch {
                    actionInFlight = true
                    actionError = null
                    val projectChatIds = chats.filter { it.cwd == project.path }.map { it.id }
                    projectChatIds.forEach(onStopChat)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            projectChatIds.forEach(chatCatalog::delete)
                            projectCatalog.remove(project.path)
                            archiveCatalog.setProjectArchived(project.path, false)
                            archiveCatalog.removeChats(projectChatIds)
                        }
                    }
                    actionInFlight = false
                    result.onSuccess { updated ->
                        archive = updated
                        chats = chats.filterNot { it.id in projectChatIds }
                        registeredProjects = registeredProjects.filterNot { it.path == project.path }
                        deleteProject = null
                    }.onFailure {
                        actionError = it.message ?: "Could not delete this project"
                    }
                }
            },
        )
    }

    renameEntry?.let { entry ->
        RenameEntryDialog(
            entry = entry,
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    renameEntry = null
                    actionError = null
                }
            },
            onRename = { proposedTitle ->
                when (entry) {
                    is WorkspaceEntry.Chat -> actionScope.launch {
                        actionInFlight = true
                        actionError = null
                        val result = withContext(Dispatchers.IO) {
                            runCatching { chatCatalog.rename(entry.chat.id, proposedTitle) }
                        }
                        actionInFlight = false
                        result.onSuccess {
                            chats = chats.map {
                                if (it.id == entry.chat.id) it.copy(title = proposedTitle.trim().take(64)) else it
                            }
                            renameEntry = null
                        }.onFailure {
                            actionError = it.message ?: "Could not rename this chat. Try again."
                        }
                    }
                    is WorkspaceEntry.TerminalSession -> {
                        onRenameTerminal(entry.session, proposedTitle)
                        renameEntry = null
                    }
                    is WorkspaceEntry.PersistentTerminal -> {
                        onRenamePersistentTerminal(entry.name, proposedTitle)
                        persistentTerminals = persistentTerminals.filterNot { it == entry.name }
                        renameEntry = null
                    }
                }
            },
        )
    }

    removeEntry?.let { entry ->
        RemoveEntryDialog(
            entry = entry,
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    removeEntry = null
                    actionError = null
                }
            },
            onRemove = {
                when (entry) {
                    is WorkspaceEntry.Chat -> actionScope.launch {
                        actionInFlight = true
                        actionError = null
                        onStopChat(entry.chat.id)
                        val result = withContext(Dispatchers.IO) {
                            runCatching { archiveCatalog.setChatArchived(entry.chat.id, true) }
                        }
                        actionInFlight = false
                        result.onSuccess { updated ->
                            archive = updated
                            removeEntry = null
                        }.onFailure {
                            actionError = it.message ?: "Could not archive this chat. Try again."
                        }
                    }
                    is WorkspaceEntry.TerminalSession -> {
                        onDeleteTerminal(entry.session)
                        removeEntry = null
                    }
                    is WorkspaceEntry.PersistentTerminal -> {
                        onDeletePersistentTerminal(entry.name)
                        persistentTerminals = persistentTerminals.filterNot { it == entry.name }
                        removeEntry = null
                    }
                }
            },
        )
    }

    deleteChat?.let { chat ->
        DeleteChatDialog(
            chat = chat,
            loading = actionInFlight,
            error = actionError,
            onDismiss = {
                if (!actionInFlight) {
                    deleteChat = null
                    actionError = null
                }
            },
            onDelete = {
                actionScope.launch {
                    actionInFlight = true
                    actionError = null
                    onStopChat(chat.id)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            chatCatalog.delete(chat.id)
                            archiveCatalog.setChatArchived(chat.id, false)
                        }
                    }
                    actionInFlight = false
                    result.onSuccess { updated ->
                        archive = updated
                        chats = chats.filterNot { it.id == chat.id }
                        deleteChat = null
                    }.onFailure {
                        actionError = it.message ?: "Could not delete this chat. Try again."
                    }
                }
            },
        )
    }
}

private enum class AddProjectStep { Menu, ChooseFolder, CreateFolder, CloneRepository }

@Composable
private fun WorkspaceSectionHeading(
    label: String,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (onAction != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SoftSurface, CircleShape)
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = actionContentDescription,
                    tint = Ink,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectHeader(
    project: PandoraProject,
    chatCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onToggle,
                    onClickLabel = if (expanded) "Collapse ${project.name}" else "Expand ${project.name}",
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = "Project actions",
                )
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.name,
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (project.pinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = Accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${project.displayPath} · $chatCount ${if (chatCount == 1) "chat" else "chats"}",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (expanded) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(AccentSurface, CircleShape)
                        .clickable(
                            onClickLabel = "Start a new chat in ${project.name}",
                            onClick = onNewChat,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "New chat in ${project.name}",
                        tint = Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).size(1.dp)) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.width(184.dp),
                offset = DpOffset((-8).dp, (-30).dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = SoftSurface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                ProjectMenuItem(Icons.Rounded.Edit, "Rename") {
                    menuExpanded = false
                    onRename()
                }
                ProjectMenuItem(Icons.Rounded.PushPin, if (project.pinned) "Unpin" else "Pin") {
                    menuExpanded = false
                    onPin()
                }
                ProjectMenuItem(Icons.Rounded.Archive, "Archive") {
                    menuExpanded = false
                    onArchive()
                }
                ProjectMenuItem(Icons.Rounded.Delete, "Delete", destructive = true) {
                    menuExpanded = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun ProjectMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else Ink
    DropdownMenuItem(
        text = { Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
    )
}

@Composable
private fun EmptyProjectsRow(
    loading: Boolean,
    compact: Boolean = false,
    onAddProject: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (loading) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Opening your workspace…", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Loading projects and recent chats", color = Muted, fontSize = 12.sp)
            }
        }
    } else if (compact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "Add a project", onClick = onAddProject)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(AccentSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Add your first project", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("Choose or clone a folder", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SoftSurface, RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Add a project", onClick = onAddProject),
        ) {
            Column(Modifier.padding(18.dp)) {
                Box(
                    Modifier.size(42.dp).background(AccentSurface, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = Accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Bring in your first project", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Choose a folder, create a clean workspace, or clone a GitHub repository. Chats stay organized with the code they belong to.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(end = 18.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add a project", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clickable(onClickLabel = "Dismiss project suggestion", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddProjectDialog(
    catalog: ProjectCatalog,
    initialStep: AddProjectStep,
    registeredPaths: Set<String>,
    onDismiss: () -> Unit,
    onProjectReady: (PandoraProject) -> Unit,
) {
    var step by remember(initialStep) { mutableStateOf(initialStep) }
    var value by remember(step) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val folders = remember(step, registeredPaths) {
        if (step == AddProjectStep.ChooseFolder) {
            catalog.discoverFolders().filterNot { it.path in registeredPaths }
        } else {
            emptyList()
        }
    }

    fun submit(block: () -> PandoraProject) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            loading = false
            result.onSuccess(onProjectReady).onFailure {
                error = it.message ?: "Could not add this project"
            }
        }
    }

    val title = when (step) {
        AddProjectStep.Menu -> "Add project"
        AddProjectStep.ChooseFolder -> "Choose a folder"
        AddProjectStep.CreateFolder -> "Create a folder"
        AddProjectStep.CloneRepository -> "Clone repository"
    }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                when (step) {
                    AddProjectStep.Menu -> {
                        ProjectChoiceRow(
                            icon = Icons.Rounded.Folder,
                            title = "Choose folder",
                            subtitle = "Use an existing folder in /root/projects",
                            onClick = { step = AddProjectStep.ChooseFolder },
                        )
                        ProjectChoiceRow(
                            icon = Icons.Rounded.Add,
                            title = "Create new folder",
                            subtitle = "Start a new local project",
                            onClick = { step = AddProjectStep.CreateFolder },
                        )
                        ProjectChoiceRow(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            title = "Clone repository",
                            subtitle = "Clone a Git repository into /root/projects",
                            onClick = { step = AddProjectStep.CloneRepository },
                        )
                    }
                    AddProjectStep.ChooseFolder -> {
                        if (folders.isEmpty()) {
                            Text("No ungrouped folders found in /root/projects.", color = Muted, fontSize = 13.sp)
                        } else {
                            folders.forEach { project ->
                                ProjectChoiceRow(
                                    icon = Icons.Rounded.Folder,
                                    title = project.name,
                                    subtitle = project.displayPath,
                                    enabled = !loading,
                                    onClick = { submit { catalog.register(project.path) } },
                                )
                            }
                        }
                    }
                    AddProjectStep.CreateFolder, AddProjectStep.CloneRepository -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it; error = null },
                            enabled = !loading,
                            singleLine = true,
                            label = {
                                Text(if (step == AddProjectStep.CreateFolder) "Folder name" else "Repository URL")
                            },
                            placeholder = {
                                Text(if (step == AddProjectStep.CreateFolder) "my-project" else "https://github.com/…")
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (value.isNotBlank()) submit {
                                    if (step == AddProjectStep.CreateFolder) catalog.createFolder(value)
                                    else catalog.cloneRepository(value)
                                }
                            }),
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (step == AddProjectStep.CloneRepository) "Cloning repository…" else "Adding project…",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == AddProjectStep.CreateFolder || step == AddProjectStep.CloneRepository) {
                TextButton(
                    enabled = value.isNotBlank() && !loading,
                    onClick = {
                        submit {
                            if (step == AddProjectStep.CreateFolder) catalog.createFolder(value)
                            else catalog.cloneRepository(value)
                        }
                    },
                ) { Text(if (step == AddProjectStep.CreateFolder) "Create" else "Clone") }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !loading,
                onClick = { if (step == initialStep || step == AddProjectStep.Menu) onDismiss() else step = AddProjectStep.Menu },
            ) { Text(if (step == initialStep || step == AddProjectStep.Menu) "Cancel" else "Back") }
        },
    )
}

@Composable
private fun ProjectChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(SoftSurface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) Ink else Muted, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Ink else Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RenameProjectDialog(
    project: PandoraProject,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(project.path) { mutableStateOf(project.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 64) name = it },
                    enabled = !loading,
                    singleLine = true,
                    label = { Text("Project name") },
                    supportingText = {
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                        else Text("The folder name and path stay unchanged.")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Renaming…" else "Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

@Composable
private fun ArchiveProjectDialog(
    project: PandoraProject,
    chatCount: Int,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archive ${project.name}?") },
        text = {
            Column {
                Text(
                    if (chatCount == 0) {
                        "This hides the project from Home. You can restore it anytime from Settings → Archive."
                    } else {
                        "This hides the project and its $chatCount ${if (chatCount == 1) "chat" else "chats"} from Home. Nothing is deleted."
                    },
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onArchive, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (loading) "Archiving…" else "Archive",
                    color = if (loading) Muted else Accent,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteProjectDialog(
    project: PandoraProject,
    chatCount: Int,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${project.name}?") },
        text = {
            Column {
                Text(
                    if (chatCount == 0) {
                        "This removes the project from Pandora. The directory and its files stay untouched."
                    } else {
                        "This permanently deletes $chatCount ${if (chatCount == 1) "chat" else "chats"} and removes the project from Pandora. The directory and its files stay untouched."
                    },
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (loading) "Deleting…" else "Delete",
                    color = if (loading) Muted else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

private sealed interface WorkspaceEntry {
    val key: String
    val updatedAtMillis: Long

    data class Chat(val chat: CodexThreadSummary) : WorkspaceEntry {
        override val key = "chat:${chat.id}"
        override val updatedAtMillis = chat.updatedAtMillis
    }

    data class TerminalSession(val session: ManagedTerminalSession) : WorkspaceEntry {
        override val key = "terminal:${session.id}"
        override val updatedAtMillis = session.createdAtMillis
    }

    data class PersistentTerminal(val name: String) : WorkspaceEntry {
        override val key = "zmx:$name"
        override val updatedAtMillis = 0L
    }
}

private fun WorkspaceEntry.displayTitle(): String = when (this) {
    is WorkspaceEntry.Chat -> chat.title
    is WorkspaceEntry.TerminalSession -> session.title
    is WorkspaceEntry.PersistentTerminal -> name.removePrefix("pandora-").let { "Terminal $it" }
}

private fun WorkspaceEntry.typeLabel(): String = if (this is WorkspaceEntry.Chat) "chat" else "terminal"

@Composable
private fun RenameEntryDialog(
    entry: WorkspaceEntry,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember(entry.key) { mutableStateOf(entry.displayTitle()) }
    val valid = title.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${entry.typeLabel()}") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 64) title = it },
                    enabled = !loading,
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = {
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                        else Text("${title.length}/64")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(title.trim()) }, enabled = valid && !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Renaming…" else "Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

@Composable
private fun RemoveEntryDialog(
    entry: WorkspaceEntry,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val terminalState = (entry as? WorkspaceEntry.TerminalSession)
        ?.session
        ?.state
        ?.collectAsState()
        ?.value
    val detail = when (entry) {
        is WorkspaceEntry.Chat -> "This hides the conversation from Home. You can restore it anytime from Settings → Archive."
        is WorkspaceEntry.TerminalSession -> when (terminalState) {
            PtyTerminalSession.State.PREPARING, PtyTerminalSession.State.RUNNING ->
                "This ends the running shell and removes its terminal output. Files in /root remain saved."
            PtyTerminalSession.State.STOPPED, PtyTerminalSession.State.FAILED ->
                "This removes the stopped session and its terminal output. Files in /root remain saved."
            null -> "This removes the terminal session. Files in /root remain saved."
        }
        is WorkspaceEntry.PersistentTerminal ->
            "This ends the detached shell and removes it from Pandora. Files in /root remain saved."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry is WorkspaceEntry.Chat) "Archive chat?" else "Delete terminal?") },
        text = {
            Column {
                Text(detail)
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRemove, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (loading) {
                        if (entry is WorkspaceEntry.Chat) "Archiving…" else "Deleting…"
                    } else {
                        if (entry is WorkspaceEntry.Chat) "Archive" else "Delete"
                    },
                    color = if (loading) Muted else if (entry is WorkspaceEntry.Chat) Accent else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteChatDialog(
    chat: CodexThreadSummary,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${chat.title}?") },
        text = {
            Column {
                Text("This permanently deletes the conversation. This cannot be undone.")
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (loading) "Deleting…" else "Delete",
                    color = if (loading) Muted else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceEntryRow(
    entry: WorkspaceEntry,
    chatSessions: List<CodexChatSession>,
    selectedChatSessionId: String? = null,
    dense: Boolean = false,
    nested: Boolean = false,
    onOpenChat: (String, String) -> Unit,
    onStopChat: (String) -> Unit,
    onOpenTerminal: (ManagedTerminalSession) -> Unit,
    onRestartTerminal: (ManagedTerminalSession) -> Unit,
    onOpenPersistentTerminal: (String) -> Unit,
    onRename: () -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isChat = entry is WorkspaceEntry.Chat
    val terminalState = (entry as? WorkspaceEntry.TerminalSession)
        ?.session
        ?.state
        ?.collectAsState()
        ?.value
    val chatSession = (entry as? WorkspaceEntry.Chat)?.let { chatEntry ->
        chatSessions.firstOrNull { it.activeThreadId == chatEntry.chat.id }
    }
    val chatState = chatSession?.state?.collectAsState()?.value
    val selected = selectedChatSessionId?.let { it == chatSession?.id } == true
    val title = when (entry) {
        is WorkspaceEntry.Chat -> entry.chat.title
        is WorkspaceEntry.TerminalSession -> entry.session.title
        is WorkspaceEntry.PersistentTerminal -> entry.name.removePrefix("pandora-").let { "Terminal $it" }
    }
    val subtitle = when (entry) {
        is WorkspaceEntry.Chat -> when (val current = chatState) {
            is CodexChatState.Starting -> "Starting · ${current.detail}"
            is CodexChatState.Running -> "Working"
            is CodexChatState.Ready -> "Ready"
            is CodexChatState.Failed -> "Failed · ${current.detail}"
            CodexChatState.Closed -> "Stopped"
            null -> "Stopped"
        }
        is WorkspaceEntry.TerminalSession -> when (terminalState) {
            PtyTerminalSession.State.PREPARING -> "Active · starting Linux…"
            PtyTerminalSession.State.RUNNING -> "Active"
            PtyTerminalSession.State.STOPPED -> "Stopped"
            PtyTerminalSession.State.FAILED -> "Stopped"
            null -> "Stopped"
        }
        is WorkspaceEntry.PersistentTerminal -> "Active · detached · tap to reconnect"
    }
    val terminalActive = when (entry) {
        is WorkspaceEntry.Chat -> when (chatState) {
            is CodexChatState.Starting, is CodexChatState.Ready, is CodexChatState.Running -> true
            is CodexChatState.Failed, CodexChatState.Closed, null -> false
        }
        is WorkspaceEntry.PersistentTerminal -> true
        is WorkspaceEntry.TerminalSession -> when (terminalState) {
            PtyTerminalSession.State.PREPARING, PtyTerminalSession.State.RUNNING -> true
            PtyTerminalSession.State.STOPPED, PtyTerminalSession.State.FAILED, null -> false
        }
    }
    val chatIsActive = chatState is CodexChatState.Starting ||
        chatState is CodexChatState.Ready ||
        chatState is CodexChatState.Running
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                if (menuExpanded || selected) AccentSurface else Color.Transparent,
                RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when (entry) {
                            is WorkspaceEntry.Chat -> onOpenChat(entry.chat.id, entry.chat.cwd)
                            is WorkspaceEntry.TerminalSession -> onOpenTerminal(entry.session)
                            is WorkspaceEntry.PersistentTerminal -> onOpenPersistentTerminal(entry.name)
                        }
                    },
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = "More actions",
                )
                .padding(
                    start = if (nested) 30.dp else 0.dp,
                    end = if (dense) 6.dp else 10.dp,
                    top = 11.dp,
                    bottom = 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            Modifier
                .size(if (dense) 38.dp else 42.dp)
                .background(
                    if (isChat) AccentSurface else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isChat) Icons.Rounded.AutoAwesome else Icons.Rounded.Terminal,
                contentDescription = null,
                tint = if (isChat) Accent else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(if (dense) 18.dp else 19.dp),
            )
        }
        Spacer(Modifier.width(if (dense) 10.dp else 12.dp))
        val stoppedSession = (entry as? WorkspaceEntry.TerminalSession)?.session?.takeIf {
            it.state.value == PtyTerminalSession.State.STOPPED ||
                it.state.value == PtyTerminalSession.State.FAILED
        }
        val relativeTime = if (
            stoppedSession == null &&
            !(entry is WorkspaceEntry.Chat && chatIsActive) &&
            entry.updatedAtMillis > 0L
        ) {
            formatRelativeTime(entry.updatedAtMillis)
        } else {
            null
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    color = Ink,
                    fontSize = if (dense) 14.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                relativeTime?.let { time ->
                    Spacer(Modifier.width(if (dense) 6.dp else 8.dp))
                    Text(
                        time,
                        color = Muted,
                        fontSize = if (dense) 10.sp else 11.sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = if (dense) 24.dp else 28.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).background(
                        if (terminalActive) Color(0xFF55A76A) else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = if (dense) 11.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
            if (entry is WorkspaceEntry.Chat && chatIsActive) {
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = { onStopChat(entry.chat.id) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = "Stop ${entry.chat.title}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp),
                    )
                }
            } else if (stoppedSession != null) {
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .background(SoftSurface, RoundedCornerShape(12.dp))
                        .clickable { onRestartTerminal(stoppedSession) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Start ${stoppedSession.title}",
                        tint = Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Start", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(1.dp),
        ) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.width(172.dp),
                offset = DpOffset((-8).dp, (-28).dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = SoftSurface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Rename", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    },
                    leadingIcon = {
                        Box(
                            Modifier.size(30.dp).background(AccentSurface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                )
                Spacer(Modifier.height(2.dp))
                if (entry is WorkspaceEntry.Chat && chatIsActive) {
                    DropdownMenuItem(
                        text = {
                            Text("Stop", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onStopChat(entry.chat.id)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (onArchive != null) {
                    DropdownMenuItem(
                        text = {
                            Text("Archive", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        },
                        leadingIcon = {
                            Box(
                                Modifier.size(30.dp).background(AccentSurface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Archive,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onArchive()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val elapsedMinutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        elapsedMinutes < 1 -> "now"
        elapsedMinutes < 60 -> "${elapsedMinutes}m"
        elapsedMinutes < 24 * 60 -> "${elapsedMinutes / 60}h"
        elapsedMinutes < 7 * 24 * 60 -> "${elapsedMinutes / (24 * 60)}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun CodexLimitsFooter(
    state: CodexLimitsState,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
) {
    val (color, label) = when (state) {
        CodexLimitsState.Loading -> Accent to "Checking Codex limits…"
        CodexLimitsState.SignedOut -> Accent to "Sign in to Codex"
        CodexLimitsState.Unavailable -> Color(0xFFD18B27) to "Codex limits unavailable · tap to retry"
        is CodexLimitsState.Available -> {
            val lowest = minOf(state.primary.remainingPercent, state.secondary?.remainingPercent ?: 100)
            val statusColor = when {
                lowest <= 10 -> Color(0xFFD84B4B)
                lowest <= 25 -> Color(0xFFD18B27)
                else -> Color(0xFF55A76A)
            }
            val windows = listOfNotNull(state.primary, state.secondary)
                .joinToString(" · ") { "${it.remainingPercent}% ${formatLimitWindow(it)}" }
            statusColor to "Codex left · $windows"
        }
    }
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = if (state is CodexLimitsState.SignedOut) onSignIn else onRefresh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatLimitWindow(window: CodexLimitWindow): String = when {
    window.durationMinutes >= 7 * 24 * 60 && window.durationMinutes % (7 * 24 * 60) == 0 -> {
        val weeks = window.durationMinutes / (7 * 24 * 60)
        if (weeks == 1) "wk" else "${weeks}wk"
    }
    window.durationMinutes >= 24 * 60 && window.durationMinutes % (24 * 60) == 0 -> {
        "${window.durationMinutes / (24 * 60)}d"
    }
    window.durationMinutes >= 60 && window.durationMinutes % 60 == 0 -> "${window.durationMinutes / 60}h"
    else -> "${window.durationMinutes}m"
}

@Composable
private fun SessionsScreen(
    sessions: List<ManagedTerminalSession>,
    @Suppress("UNUSED_PARAMETER")
    revision: Long,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (ManagedTerminalSession) -> Unit,
) {
    val background = Color(0xFF111211)
    val rowLine = Color(0xFF292A28)
    val active = sessions.filter {
        it.state.value == PtyTerminalSession.State.RUNNING || it.state.value == PtyTerminalSession.State.PREPARING
    }
    val ended = sessions - active.toSet()
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(Color(0xFF242523), CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", color = Color.White, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Linux sessions", color = Color(0xFFF2F3EF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (active.isEmpty()) "No active sessions" else "${active.size} active · ${sessions.size} retained",
                    color = Color(0xFF858781),
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color(0xFF2C2D2A), RoundedCornerShape(10.dp))
                    .clickable(onClick = onNewSession),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Color(0xFFBEB3FF), fontSize = 24.sp) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(rowLine))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (sessions.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(">_", color = Color(0xFF8070F2), fontSize = 28.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(12.dp))
                        Text("No Linux sessions yet", color = Color(0xFFE7E8E4), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("Tap + to start an independent terminal.", color = Color(0xFF777973), fontSize = 13.sp)
                    }
                }
            }

            if (active.isNotEmpty()) {
                item { SessionSectionLabel("ACTIVE", active.size) }
                items(active, key = { it.id }) { session ->
                    SessionCard(session = session, onClick = { onOpenSession(session) })
                }
            }
            if (ended.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    SessionSectionLabel("ENDED", ended.size)
                }
                items(ended, key = { it.id }) { session ->
                    SessionCard(session = session, onClick = { onOpenSession(session) })
                }
            }
        }
    }
}

@Composable
private fun SessionSectionLabel(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌄", color = Color(0xFF777973), fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFF858781), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Text(count.toString(), color = Color(0xFF666862), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SessionCard(session: ManagedTerminalSession, onClick: () -> Unit) {
    val state by session.state.collectAsState()
    val status = when (state) {
        PtyTerminalSession.State.PREPARING -> "starting"
        PtyTerminalSession.State.RUNNING -> "running"
        PtyTerminalSession.State.STOPPED -> "ended"
        PtyTerminalSession.State.FAILED -> "failed"
    }
    val time = remember(session.createdAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.createdAtMillis))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 6.dp).size(8.dp).background(
                when (state) {
                    PtyTerminalSession.State.PREPARING -> Color(0xFFF0C23B)
                    PtyTerminalSession.State.RUNNING -> Color(0xFF25C58A)
                    PtyTerminalSession.State.FAILED -> Color(0xFFF2535B)
                    PtyTerminalSession.State.STOPPED -> Color(0xFF41433F)
                },
                CircleShape,
            ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title, color = Color(0xFFE9EAE6), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("local  ~/", color = Color(0xFF777973), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(3.dp))
            Text("$status · started $time", color = Color(0xFF666862), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Text("›", color = Color(0xFF777973), fontSize = 20.sp)
    }
    Box(Modifier.fillMaxWidth().padding(start = 27.dp).height(1.dp).background(Color(0xFF292A28)))
}

@Composable
private fun StatusPill() {
    Row(
        modifier = Modifier
            .background(SoftSurface, RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(Color(0xFF55A76A), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("Local", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionCard(
    symbol: String,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val background = if (primary) Ink else Color.Transparent
    val foreground = if (primary) Color.White else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(22.dp))
            .border(if (primary) 0.dp else 1.dp, Line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(19.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(47.dp)
                .background(if (primary) Color(0xFF353633) else Color(0xFFECEBE7), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = if (primary) Color(0xFFBEB3FF) else Accent, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = if (primary) Color(0xFFB7B8B3) else Muted, fontSize = 13.sp)
        }
        Text("→", color = if (primary) Color.White else Muted, fontSize = 20.sp)
    }
}

@Composable
private fun Header(title: String, detail: String? = null, onBack: () -> Unit, dark: Boolean = false) {
    val color = if (dark) Color.White else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (dark) Color(0xFF292A27) else Color(0xFFEAEAE5), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = color, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, color = if (dark) Color(0xFF8E918A) else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyWorkspaceDetail() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(AccentSurface, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Choose a chat", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Select a conversation from the sidebar, or start a new one.",
            color = Muted,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun ChatScreen(
    session: CodexChatSession,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    showBackButton: Boolean = true,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val application = context.applicationContext as PandoraApplication
    val speech = application.onDeviceSpeech
    val speechModels = application.speechModels
    val dictation by speech.dictation.collectAsState()
    val dictationDiagnostics by speech.diagnostics.collectAsState()
    val playback by speech.playback.collectAsState()
    var draft by remember { mutableStateOf("") }
    var dictationPrefix by remember { mutableStateOf("") }
    var missingSpeechKind by remember { mutableStateOf<SpeechModelKind?>(null) }
    val pendingAttachments = remember { mutableStateListOf<ChatAttachment>() }
    var attachmentMenuOpen by remember { mutableStateOf(false) }
    var importingAttachment by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var previewAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    val chatScope = rememberCoroutineScope()
    val messages by session.messages.collectAsState()
    val state by session.state.collectAsState()
    val interrupting by session.interrupting.collectAsState()
    val models by session.models.collectAsState()
    val selectedModel by session.selectedModel.collectAsState()
    var modelMenuOpen by remember { mutableStateOf(false) }
    var voiceModeEnabled by rememberSaveable { mutableStateOf(false) }
    var pendingVoiceModePermission by remember { mutableStateOf(false) }
    var pendingVoicePrompt by remember { mutableStateOf<String?>(null) }
    var lastAutoSpokenId by remember {
        mutableStateOf(messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id)
    }
    val listState = rememberLazyListState()
    val ready = state is CodexChatState.Ready
    val detail = when (val current = state) {
        is CodexChatState.Starting -> current.detail
        is CodexChatState.Ready -> current.model
        is CodexChatState.Running -> "${current.model} · working…"
        is CodexChatState.Failed -> "Connection failed"
        CodexChatState.Closed -> "Closed"
    }
    fun submit() {
        if (
            dictation is DictationState.Loading ||
            dictation is DictationState.Listening ||
            dictation is DictationState.Transcribing
        ) {
            speech.cancelDictation()
        }
        val command = parseSlashCommand(draft)
        if (command != null) {
            if (command is SlashCommandParseResult.Invalid) {
                session.showCommandError(command.message)
                return
            }
            command as SlashCommandParseResult.Command
            if (pendingAttachments.isNotEmpty()) {
                session.showCommandError("Slash commands cannot be sent with attachments. Remove them and try again.")
                return
            }
            when (command.id) {
                SlashCommandId.COMPACT -> if (!session.compact()) return
                SlashCommandId.REVIEW -> if (!session.reviewUncommittedChanges()) return
                SlashCommandId.STATUS -> session.showStatus()
                SlashCommandId.MODEL -> {
                    val query = command.argument
                    if (query == null) {
                        if (models.isEmpty()) {
                            session.showCommandError("The model list is not available yet. Try again in a moment.")
                            return
                        }
                        modelMenuOpen = true
                    } else {
                        val selected = models.firstOrNull {
                            it.model.equals(query, ignoreCase = true) ||
                                it.id.equals(query, ignoreCase = true) ||
                                it.displayName.equals(query, ignoreCase = true)
                        }
                        if (selected == null) {
                            session.showCommandError("Model '$query' is not available. Use /model to choose one.")
                            return
                        }
                        session.selectModel(selected.model)
                        session.showCommandInfo("Model changed to ${selected.displayName}.")
                    }
                }
                SlashCommandId.NEW -> onNewChat()
            }
            draft = ""
            dictationPrefix = ""
            return
        }
        if (session.send(draft, pendingAttachments.toList())) {
            draft = ""
            dictationPrefix = ""
            pendingAttachments.clear()
        }
    }
    fun importAttachment(uri: android.net.Uri?) {
        if (uri == null) return
        attachmentMenuOpen = false
        attachmentError = null
        importingAttachment = true
        chatScope.launch {
            runCatching { ChatAttachmentStore.import(context, uri, session.id) }
                .onSuccess(pendingAttachments::add)
                .onFailure { attachmentError = it.message ?: "Could not add this attachment" }
            importingAttachment = false
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::importAttachment)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::importAttachment)
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingVoiceModePermission) {
                pendingVoiceModePermission = false
                voiceModeEnabled = true
                speech.startDictation(finishOnEndpoint = true)
            } else {
                speech.startDictation()
            }
        } else {
            pendingVoiceModePermission = false
            speech.microphonePermissionDenied()
        }
    }
    fun beginDictation() {
        val model = SpeechModels.find(AppSettings.speechToTextModel(context))
        if (model == null || !speechModels.isInstalled(model)) {
            missingSpeechKind = SpeechModelKind.SPEECH_TO_TEXT
            return
        }
        dictationPrefix = draft.trim()
        speech.clearDictation()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speech.startDictation()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun readAloud(text: String) {
        val model = SpeechModels.find(AppSettings.textToSpeechModel(context))
        if (model == null || !speechModels.isInstalled(model)) {
            missingSpeechKind = SpeechModelKind.TEXT_TO_SPEECH
        } else if (playback is SpeechPlaybackState.Loading || playback is SpeechPlaybackState.Speaking) {
            speech.stopSpeaking()
        } else {
            speech.speak(text)
        }
    }
    fun toggleVoiceMode() {
        if (voiceModeEnabled) {
            voiceModeEnabled = false
            pendingVoicePrompt = null
            speech.cancelDictation()
            speech.stopSpeaking()
            return
        }
        val dictationModel = SpeechModels.find(AppSettings.speechToTextModel(context))
        if (dictationModel == null || !speechModels.isInstalled(dictationModel)) {
            missingSpeechKind = SpeechModelKind.SPEECH_TO_TEXT
            return
        }
        val voiceModel = SpeechModels.find(AppSettings.textToSpeechModel(context))
        if (voiceModel == null || !speechModels.isInstalled(voiceModel)) {
            missingSpeechKind = SpeechModelKind.TEXT_TO_SPEECH
            return
        }
        speech.cancelDictation()
        draft = ""
        dictationPrefix = ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceModeEnabled = true
            speech.startDictation(finishOnEndpoint = true)
        } else {
            pendingVoiceModePermission = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(dictation) {
        if (voiceModeEnabled) return@LaunchedEffect
        val spoken = when (val current = dictation) {
            is DictationState.Listening -> current.text
            is DictationState.Finished -> current.text
            else -> null
        }
        if (spoken != null) {
            draft = when {
                dictationPrefix.isBlank() -> spoken
                spoken.isBlank() -> dictationPrefix
                else -> "$dictationPrefix $spoken"
            }
        }
        if (dictation is DictationState.Finished) speech.clearDictation()
    }
    LaunchedEffect(voiceModeEnabled, dictation, playback, state) {
        if (!voiceModeEnabled) return@LaunchedEffect
        when (val current = dictation) {
            is DictationState.Listening -> {
                if (
                    current.text.isNotBlank() &&
                    (playback is SpeechPlaybackState.Loading || playback is SpeechPlaybackState.Speaking)
                ) {
                    speech.stopSpeaking()
                }
            }
            is DictationState.Finished -> {
                val spoken = current.text.trim()
                speech.clearDictation()
                if (spoken.isNotBlank()) {
                    speech.stopSpeaking()
                    if (isVoiceStopCommand(spoken)) {
                        pendingVoicePrompt = null
                        if (state is CodexChatState.Running) session.interrupt()
                    } else if (state is CodexChatState.Ready) {
                        session.send(spoken)
                    } else {
                        pendingVoicePrompt = spoken
                        if (state is CodexChatState.Running) session.interrupt()
                    }
                }
            }
            else -> Unit
        }
    }
    LaunchedEffect(voiceModeEnabled, state, pendingVoicePrompt) {
        val pending = pendingVoicePrompt
        if (voiceModeEnabled && state is CodexChatState.Ready && !pending.isNullOrBlank()) {
            if (session.send(pending)) pendingVoicePrompt = null
        }
    }
    LaunchedEffect(voiceModeEnabled, state, playback, dictation) {
        if (!voiceModeEnabled || dictation !is DictationState.Idle) return@LaunchedEffect
        when (playback) {
            SpeechPlaybackState.Loading -> Unit
            SpeechPlaybackState.Speaking -> speech.startDictation(
                finishOnEndpoint = true,
                stopPlaybackFirst = false,
            )
            else -> speech.startDictation(finishOnEndpoint = true)
        }
    }
    LaunchedEffect(state, messages.size, voiceModeEnabled, pendingVoicePrompt) {
        val latest = messages.lastOrNull { it.role == ChatRole.ASSISTANT && it.text.isNotBlank() }
        if (state is CodexChatState.Ready && latest != null && latest.id != lastAutoSpokenId) {
            lastAutoSpokenId = latest.id
            if (
                pendingVoicePrompt == null &&
                (voiceModeEnabled || AppSettings.speakAssistantResponses(context))
            ) {
                readAloud(latest.text)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voiceModeEnabled = false
                pendingVoicePrompt = null
                speech.cancelDictation()
                speech.stopSpeaking()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speech.cancelDictation()
            speech.stopSpeaking()
        }
    }
    DisposableEffect(session.id) {
        onDispose {
            pendingAttachments.forEach { ChatAttachmentStore.deletePending(context, it) }
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                Box(
                    Modifier.size(40.dp).background(SoftSurface, CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Codex", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    Row(
                        modifier = Modifier.clickable(enabled = models.isNotEmpty()) { modelMenuOpen = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            detail,
                            color = if (models.isNotEmpty()) Accent else Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        if (models.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Choose model",
                                tint = Accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                        models.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            option.displayName,
                                            color = Ink,
                                            fontSize = 14.sp,
                                            fontWeight = if (option.model == selectedModel) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (option.description.isNotBlank()) {
                                            Text(option.description, color = Muted, fontSize = 11.sp, maxLines = 2)
                                        }
                                    }
                                },
                                onClick = {
                                    session.selectModel(option.model)
                                    modelMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            StatusPill()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        if (messages.isEmpty()) {
            if (state is CodexChatState.Starting) {
                InstallationProgress(
                    detail = (state as CodexChatState.Starting).detail,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                EmptyChatState(
                    error = (state as? CodexChatState.Failed)?.detail,
                    onSuggestion = { draft = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val startsAssistantGroup = message.role != ChatRole.ASSISTANT ||
                        index == 0 ||
                        messages[index - 1].role != ChatRole.ASSISTANT
                    ChatMessageRow(
                        message,
                        showAssistantIdentity = startsAssistantGroup,
                        speaking = playback is SpeechPlaybackState.Loading || playback is SpeechPlaybackState.Speaking,
                        onSpeak = { readAloud(message.text) },
                        onPreviewAttachment = { previewAttachment = it },
                        onOpenAttachment = { attachment ->
                            if (!ChatAttachmentStore.openWithAndroid(context, attachment)) {
                                attachmentError = if (attachment.isAndroidPackage()) {
                                    "Android could not open the package installer"
                                } else {
                                    "No Android app can open this file type"
                                }
                            }
                        },
                    )
                }
            }
        }
        if (state is CodexChatState.Running) {
            AgentWorkingIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
            )
        }
        if (voiceModeEnabled) {
            VoiceModePanel(
                label = when {
                    playback is SpeechPlaybackState.Loading -> "Preparing Codex’s voice…"
                    playback is SpeechPlaybackState.Speaking -> "Codex is speaking · talk to interrupt"
                    dictation is DictationState.Loading -> "Getting the microphone ready…"
                    dictation is DictationState.Transcribing -> "Finishing what you said…"
                    (dictation as? DictationState.Listening)?.text?.isNotBlank() == true ->
                        (dictation as DictationState.Listening).text
                    state is CodexChatState.Running -> "Codex is working · you can interrupt"
                    else -> "Listening · speak your request"
                },
                speaking = playback is SpeechPlaybackState.Loading || playback is SpeechPlaybackState.Speaking,
                onInterrupt = {
                    speech.cancelDictation()
                    speech.stopSpeaking()
                    pendingVoicePrompt = null
                    if (state is CodexChatState.Running) session.interrupt()
                },
                onEnd = ::toggleVoiceMode,
            )
        } else when (val current = dictation) {
            DictationState.Loading -> VoiceStatus("Loading dictation model…", active = true)
            DictationState.Transcribing -> VoiceStatus("Whisper is refining the transcript…", active = true)
            is DictationState.Listening -> VoiceStatus(
                buildString {
                    append("Listening · ${dictationDiagnostics.processor.name}")
                    dictationDiagnostics.realTimeFactor?.let {
                        append(" · ${String.format(Locale.US, "%.2f×", it)} realtime")
                    }
                    if (dictationDiagnostics.droppedAudioMillis > 0) {
                        append(" · ${dictationDiagnostics.droppedAudioMillis} ms dropped")
                    }
                },
                active = true,
            )
            is DictationState.Failed -> VoiceStatus(current.detail, active = false)
            else -> Unit
        }
        if (playback is SpeechPlaybackState.Failed) {
            VoiceStatus((playback as SpeechPlaybackState.Failed).detail, active = false)
        }
        attachmentError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .background(SoftSurface, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val commandSuggestions = slashCommandSuggestions(draft)
            if (commandSuggestions.isNotEmpty()) {
                SlashCommandPalette(
                    commands = commandSuggestions,
                    onSelect = { command ->
                        draft = if (command.id == SlashCommandId.MODEL) "/model " else command.usage
                    },
                )
            }
            if (pendingAttachments.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 5.dp, end = 5.dp, top = 3.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pendingAttachments.forEach { attachment ->
                        PendingAttachmentChip(
                            attachment = attachment,
                            onPreview = { previewAttachment = attachment },
                            onRemove = {
                                pendingAttachments.remove(attachment)
                                ChatAttachmentStore.deletePending(context, attachment)
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { attachmentMenuOpen = true },
                        enabled = ready && !importingAttachment,
                        modifier = Modifier.size(44.dp),
                    ) {
                        if (importingAttachment) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = "Add to conversation", tint = Accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    ComposerAddMenu(
                        expanded = attachmentMenuOpen,
                        onDismiss = { attachmentMenuOpen = false },
                        onPhoto = {
                            attachmentMenuOpen = false
                            photoPicker.launch(arrayOf("image/*"))
                        },
                        onFile = {
                            attachmentMenuOpen = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = ready,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 132.dp),
                    textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    when {
                                        ready -> "Message Codex…"
                                        state is CodexChatState.Running -> "Codex is working…"
                                        else -> "Codex is not ready…"
                                    },
                                    color = Color(0xFFA2A39E),
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                )
                            }
                            inner()
                        }
                    }
                )
                IconButton(
                    onClick = {
                        if (dictation is DictationState.Loading || dictation is DictationState.Listening) {
                            speech.stopDictation()
                        } else {
                            beginDictation()
                        }
                    },
                    enabled = !voiceModeEnabled && ready && dictation !is DictationState.Transcribing,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (dictation is DictationState.Transcribing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (dictation is DictationState.Loading || dictation is DictationState.Listening) Icons.Rounded.Stop else Icons.Rounded.MicNone,
                            contentDescription = if (dictation is DictationState.Loading || dictation is DictationState.Listening) "Stop dictation" else "Start dictation",
                            tint = when {
                                voiceModeEnabled -> Muted
                                dictation is DictationState.Loading || dictation is DictationState.Listening ->
                                    MaterialTheme.colorScheme.error
                                else -> Accent
                            },
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                val running = state is CodexChatState.Running
                val hasComposerContent = draft.isNotBlank() || pendingAttachments.isNotEmpty()
                val canSend = ready && hasComposerContent
                val actionEnabled = when {
                    running -> !interrupting
                    hasComposerContent -> canSend
                    else -> true
                }
                val actionLabel = when {
                    running -> "Stop Codex"
                    hasComposerContent -> "Send message"
                    voiceModeEnabled -> "End Voice Mode"
                    else -> "Start Voice Mode"
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .background(
                            when {
                                running && interrupting -> MaterialTheme.colorScheme.errorContainer
                                running -> MaterialTheme.colorScheme.error
                                hasComposerContent && !canSend -> Line
                                else -> Accent
                            },
                            CircleShape,
                        )
                        .clickable(
                            enabled = actionEnabled,
                            onClickLabel = actionLabel,
                            onClick = {
                                when {
                                    running -> session.interrupt()
                                    hasComposerContent -> submit()
                                    else -> toggleVoiceMode()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        when {
                            running -> Icons.Rounded.Stop
                            hasComposerContent -> Icons.AutoMirrored.Rounded.Send
                            else -> Icons.Rounded.GraphicEq
                        },
                        contentDescription = when {
                            running && interrupting -> "Stopping Codex"
                            running -> "Stop Codex"
                            hasComposerContent -> "Send message"
                            voiceModeEnabled -> "End Voice Mode"
                            else -> "Start Voice Mode"
                        },
                        tint = when {
                            running && interrupting -> MaterialTheme.colorScheme.onErrorContainer
                            running -> MaterialTheme.colorScheme.onError
                            hasComposerContent && !canSend -> Muted
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.size(if (running || hasComposerContent) 19.dp else 23.dp),
                    )
                }
            }
        }
    }

    if (missingSpeechKind != null) {
        val dictationMissing = missingSpeechKind == SpeechModelKind.SPEECH_TO_TEXT
        AlertDialog(
            onDismissRequest = { missingSpeechKind = null },
            title = { Text(if (dictationMissing) "Download dictation model?" else "Download a reading voice?") },
            text = {
                Text(
                    if (dictationMissing) {
                        "On-device dictation needs a one-time model download. The live default is about 55 MB and stays private on this phone."
                    } else {
                        "On-device reading needs a one-time voice download. The compact default is about 20 MB."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    missingSpeechKind = null
                    onSettings()
                }) { Text("Choose in Settings") }
            },
            dismissButton = {
                TextButton(onClick = { missingSpeechKind = null }) { Text("Not now") }
            },
        )
    }

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            onDismiss = { previewAttachment = null },
        )
    }
}

@Composable
private fun SlashCommandPalette(
    commands: List<SlashCommandDefinition>,
    onSelect: (SlashCommandDefinition) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 5.dp, end = 5.dp, top = 3.dp, bottom = 5.dp),
    ) {
        commands.forEachIndexed { index, command ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = "Use ${command.usage}",
                        onClick = { onSelect(command) },
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    command.usage,
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(min = 104.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    command.description,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index != commands.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

@Composable
private fun VoiceModePanel(
    label: String,
    speaking: Boolean,
    onInterrupt: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(AccentSurface, RoundedCornerShape(16.dp))
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = label
            }
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Accent, CircleShape)
                .clickable(
                    onClickLabel = if (speaking) "Interrupt speech" else "Stop current action",
                    onClick = onInterrupt,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (speaking) Icons.Rounded.Stop else Icons.Rounded.MicNone,
                contentDescription = if (speaking) "Interrupt speech" else "Listening",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Voice Mode", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
        }
        TextButton(onClick = onEnd) { Text("End") }
    }
}

@Composable
private fun VoiceStatus(label: String, active: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = label
            }
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = if (active) Accent else MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun AgentWorkingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = Accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "Codex is working…",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    showAssistantIdentity: Boolean = true,
    speaking: Boolean = false,
    onSpeak: () -> Unit = {},
    onPreviewAttachment: (ChatAttachment) -> Unit = {},
    onOpenAttachment: (ChatAttachment) -> Unit = {},
) {
    when (message.role) {
        ChatRole.SYSTEM -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (message.systemTone == SystemMessageTone.ERROR) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Text(
                message.text,
                color = if (message.systemTone == SystemMessageTone.ERROR) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        ChatRole.USER -> UserMessage(message, onPreviewAttachment)
        ChatRole.ASSISTANT -> AssistantMessage(
            message,
            showIdentity = showAssistantIdentity,
            speaking = speaking,
            onSpeak = onSpeak,
            onPreviewAttachment = onPreviewAttachment,
            onOpenAttachment = onOpenAttachment,
        )
    }
}

@Composable
private fun UserMessage(message: ChatMessage, onPreviewAttachment: (ChatAttachment) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(AccentSurface, RoundedCornerShape(18.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            message.attachments.forEachIndexed { index, attachment ->
                SentAttachmentPreview(attachment, onClick = { onPreviewAttachment(attachment) })
                if (index != message.attachments.lastIndex) Spacer(Modifier.height(5.dp))
            }
            if (message.attachments.isNotEmpty() && message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun SentAttachmentPreview(
    attachment: ChatAttachment,
    foreground: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val resolvedSize = remember(attachment.containerPath, attachment.sizeBytes) {
        attachment.sizeBytes ?: ChatAttachmentStore.resolve(context, attachment)?.length()
    }
    var thumbnail by remember(attachment.containerPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    if (attachment.kind == ChatAttachmentKind.IMAGE) {
        LaunchedEffect(attachment.containerPath) {
            thumbnail = withContext(Dispatchers.IO) { ChatAttachmentStore.decodePreview(context, attachment, 640) }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f), RoundedCornerShape(13.dp))
            .clickable(onClickLabel = "Preview ${attachment.name}", onClick = onClick),
    ) {
        if (attachment.kind == ChatAttachmentKind.IMAGE) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview of ${attachment.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(142.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (attachment.kind == ChatAttachmentKind.IMAGE) Icons.Rounded.Image else Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.name,
                    color = foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attachment.kind == ChatAttachmentKind.FILE) {
                    Text(formatAttachmentSize(resolvedSize), color = Accent, fontSize = 10.sp)
                }
            }
            Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AgentAttachmentGroup(
    attachments: List<ChatAttachment>,
    onPreview: (ChatAttachment) -> Unit,
    onOpen: (ChatAttachment) -> Unit,
) {
    val images = attachments.filter { it.kind == ChatAttachmentKind.IMAGE }
    val files = attachments.filter { it.kind == ChatAttachmentKind.FILE }
    Column(
        modifier = Modifier.widthIn(max = 340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { attachment ->
            SentAttachmentPreview(
                attachment = attachment,
                foreground = Ink,
                onClick = { onPreview(attachment) },
            )
        }
        if (files.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SoftSurface, RoundedCornerShape(14.dp)),
            ) {
                files.forEachIndexed { index, attachment ->
                    AgentFileRow(
                        attachment,
                        onClick = { onPreview(attachment) },
                        onInstall = { onOpen(attachment) },
                    )
                    if (index != files.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Line))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentFileRow(attachment: ChatAttachment, onClick: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    val resolvedSize = remember(attachment.containerPath, attachment.sizeBytes) {
        attachment.sizeBytes ?: ChatAttachmentStore.resolve(context, attachment)?.length()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClickLabel = "Preview ${attachment.name}", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(AccentSurface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(attachment.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatAttachmentSize(resolvedSize), color = Muted, fontSize = 10.sp)
        }
        if (attachment.isAndroidPackage()) {
            TextButton(onClick = onInstall) {
                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Install")
            }
        } else {
            Icon(Icons.Rounded.OpenInFull, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PendingAttachmentChip(attachment: ChatAttachment, onPreview: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .background(Paper, RoundedCornerShape(13.dp))
            .clickable(onClickLabel = "Preview ${attachment.name}", onClick = onPreview)
            .padding(start = 9.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (attachment.kind == ChatAttachmentKind.IMAGE) Icons.Rounded.Image else Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            attachment.name,
            color = Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove ${attachment.name}", tint = Muted, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(attachment: ChatAttachment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resolvedSize = remember(attachment.containerPath, attachment.sizeBytes) {
        attachment.sizeBytes ?: ChatAttachmentStore.resolve(context, attachment)?.length()
    }
    var image by remember(attachment.containerPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var textPreview by remember(attachment.containerPath) { mutableStateOf<String?>(null) }
    var loaded by remember(attachment.containerPath) { mutableStateOf(false) }
    var actionMessage by remember(attachment.containerPath) { mutableStateOf<String?>(null) }
    val actionScope = rememberCoroutineScope()
    val saveCopy = rememberLauncherForActivityResult(
        remember(attachment.mimeType) {
            ActivityResultContracts.CreateDocument(attachment.mimeType.ifBlank { "application/octet-stream" })
        },
    ) { destination ->
        if (destination != null) {
            actionScope.launch {
                runCatching { ChatAttachmentStore.saveCopy(context, attachment, destination) }
                    .onSuccess { actionMessage = "Saved to Android storage" }
                    .onFailure { actionMessage = it.message ?: "Could not save this attachment" }
            }
        }
    }
    LaunchedEffect(attachment.containerPath) {
        if (attachment.kind == ChatAttachmentKind.IMAGE) {
            image = withContext(Dispatchers.IO) { ChatAttachmentStore.decodePreview(context, attachment, 2048) }
        } else {
            textPreview = withContext(Dispatchers.IO) {
                runCatching { ChatAttachmentStore.readTextPreview(context, attachment) }.getOrNull()
            }
        }
        loaded = true
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        val dialogBackground = MaterialTheme.colorScheme.background
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            styleAttachmentDialogSystemBars(window, dialogView, dialogBackground)
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close preview", tint = Ink)
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attachment.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatAttachmentSize(resolvedSize), color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        if (!ChatAttachmentStore.openWithAndroid(context, attachment)) {
                            actionMessage = if (attachment.isAndroidPackage()) {
                                "Android could not open the package installer"
                            } else {
                                "No Android app can open this file type"
                            }
                        }
                    }) {
                        Icon(
                            if (attachment.isAndroidPackage()) Icons.Rounded.InstallMobile else Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = if (attachment.isAndroidPackage()) "Install APK" else "Open in Android",
                            tint = Accent,
                        )
                    }
                    IconButton(onClick = { saveCopy.launch(attachment.name) }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Save a copy", tint = Accent)
                    }
                }
                actionMessage?.let { message ->
                    Text(
                        message,
                        color = if (message.startsWith("Saved")) Accent else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                    when {
                        !loaded -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                        attachment.kind == ChatAttachmentKind.IMAGE && image != null -> Image(
                            bitmap = image!!.asImageBitmap(),
                            contentDescription = attachment.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        textPreview != null -> Text(
                            textPreview.orEmpty(),
                            color = Ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = Accent, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Preview unavailable", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text("This file type can still be sent to Codex.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun styleAttachmentDialogSystemBars(
    window: android.view.Window,
    view: android.view.View,
    background: Color,
) {
    window.statusBarColor = background.toArgb()
    window.navigationBarColor = background.toArgb()
    WindowCompat.getInsetsController(window, view).apply {
        val useDarkIcons = background.luminance() > 0.5f
        isAppearanceLightStatusBars = useDarkIcons
        isAppearanceLightNavigationBars = useDarkIcons
    }
}

private fun formatAttachmentSize(bytes: Long?): String = when {
    bytes == null -> "File"
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
}

@Composable
private fun ComposerAddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Column {
                    Text("Photo", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Add an image for Codex to inspect", color = Muted, fontSize = 11.sp)
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp)) },
            onClick = onPhoto,
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text("File", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Attach a document from this device", color = Muted, fontSize = 11.sp)
                }
            },
            leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp)) },
            onClick = onFile,
        )
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    showIdentity: Boolean,
    speaking: Boolean,
    onSpeak: () -> Unit,
    onPreviewAttachment: (ChatAttachment) -> Unit,
    onOpenAttachment: (ChatAttachment) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(message.id) { mutableStateOf(false) }
    val displayText = remember(message.text) { agentDisplayText(message.text) }
    Column(Modifier.fillMaxWidth()) {
        if (showIdentity) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(27.dp).background(AccentSurface, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("Codex", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (displayText.isNotBlank()) {
            RichMarkdown(
                markdown = displayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (showIdentity) 9.dp else 0.dp, end = 4.dp),
                color = Ink,
            )
        }
        if (message.attachments.isNotEmpty()) {
            Spacer(Modifier.height(if (displayText.isBlank() && !showIdentity) 0.dp else 10.dp))
            AgentAttachmentGroup(message.attachments, onPreviewAttachment, onOpenAttachment)
        }
        Row(Modifier.padding(top = 2.dp)) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(message.text))
                    copied = true
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy response",
                    tint = if (copied) Accent else Muted,
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (speaking) "Stop reading" else "Read response aloud",
                    tint = if (speaking) Accent else Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    error: String?,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(54.dp).background(AccentSurface, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (error == null) "What can I help you build?" else "Codex needs attention",
            color = Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "Ask about the workspace, plan a change, or start building.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        if (error == null) {
            Spacer(Modifier.height(24.dp))
            listOf(
                "Explain this project",
                "Find something to improve",
                "Help me implement a feature",
            ).forEach { suggestion ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestion(suggestion) }
                        .padding(horizontal = 4.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(suggestion, modifier = Modifier.weight(1f), color = Ink, fontSize = 14.sp)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
}

@Composable
private fun InstallationProgress(detail: String, modifier: Modifier = Modifier) {
    val stages = listOf(
        "Prepare workspace",
        "Install Debian Linux",
        "Add system utilities",
        "Configure terminal sessions",
        "Install Codex",
        "Start local agent",
    )
    val stage = when {
        detail.contains("workspace", ignoreCase = true) -> 0
        detail.contains("Debian", ignoreCase = true) -> 1
        detail.contains("SSL", ignoreCase = true) || detail.contains("utilities", ignoreCase = true) -> 2
        detail.contains("terminal", ignoreCase = true) -> 3
        detail.contains("Codex", ignoreCase = true) -> 4
        detail.contains("container", ignoreCase = true) -> 5
        else -> 0
    }
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(52.dp).background(AccentSurface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Setting up your local workspace", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pandora is preparing a private Linux environment on this device. You can leave this screen open while it finishes.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(22.dp))
        LinearProgressIndicator(
            progress = { (stage + 0.45f) / stages.size },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = Accent,
            trackColor = SoftSurface,
        )
        Spacer(Modifier.height(9.dp))
        Text(detail, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))
        stages.forEachIndexed { index, label ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).background(
                        when {
                            index < stage -> Accent
                            index == stage -> AccentSurface
                            else -> SoftSurface
                        },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index < stage) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    } else if (index == stage) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Accent, strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Text(label, color = if (index <= stage) Ink else Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ContainerScreen(
    session: ManagedTerminalSession,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    TerminalScreen(
        session = session,
        onBack = onBack,
        onStop = onStop,
        onRestart = onRestart,
    )
}
