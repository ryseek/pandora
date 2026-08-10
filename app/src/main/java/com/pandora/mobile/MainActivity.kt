package com.pandora.mobile

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandora.mobile.linux.CodexLimitWindow
import com.pandora.mobile.linux.CodexLimitsState
import com.pandora.mobile.linux.ChatMessage
import com.pandora.mobile.linux.ChatRole
import com.pandora.mobile.linux.CodexChatSession
import com.pandora.mobile.linux.CodexChatState
import com.pandora.mobile.linux.CodexThreadCatalog
import com.pandora.mobile.linux.CodexThreadSummary
import com.pandora.mobile.linux.CodexUsageReader
import com.pandora.mobile.linux.PtyTerminalSession
import com.pandora.mobile.linux.ZmxSessionCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF20211F)
private val Paper = Color(0xFFF7F7F4)
private val Muted = Color(0xFF73756E)
private val Line = Color(0xFFE3E3DD)
private val Accent = Color(0xFF6D5CE7)
private val Terminal = Color(0xFF111210)
private val TerminalText = Color(0xFFD8E4D1)

private enum class Screen { Home, Chat, Container, Settings }

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
    var screen by remember { mutableStateOf(Screen.Home) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    MaterialTheme {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "screen"
            ) { target ->
                when (target) {
                    Screen.Home -> HomeScreen(
                        sessions = sessions,
                        revision = sessionRevision,
                        onNewChat = {
                            chatManager.create()
                            screen = Screen.Chat
                        },
                        onOpenChat = { threadId ->
                            chatManager.create(threadId)
                            screen = Screen.Chat
                        },
                        onNewTerminal = {
                            selectedSessionId = sessionManager.create().id
                            screen = Screen.Container
                        },
                        onOpenTerminal = {
                            selectedSessionId = it.id
                            screen = Screen.Container
                        },
                        onOpenPersistentTerminal = { name ->
                            selectedSessionId = sessionManager.attach(name).id
                            screen = Screen.Container
                        },
                        onSettings = { screen = Screen.Settings },
                    )
                    Screen.Chat -> {
                        val chat = chatManager.current
                        if (chat == null) {
                            LaunchedEffect(Unit) { screen = Screen.Home }
                        } else {
                            ChatScreen(session = chat, onBack = { screen = Screen.Home })
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
                            )
                        }
                    }
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Home },
                        onCodexLogin = {
                            // PRoot shares Android's network namespace, so Chrome can return
                            // directly to Codex's localhost callback. This avoids the device-code
                            // polling request that is unreliable on Android.
                            selectedSessionId = sessionManager.create("codex login").id
                            screen = Screen.Container
                        },
                        onStopAllForRepair = { sessionManager.stopAll() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    sessions: List<ManagedTerminalSession>,
    @Suppress("UNUSED_PARAMETER") revision: Long,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewTerminal: () -> Unit,
    onOpenTerminal: (ManagedTerminalSession) -> Unit,
    onOpenPersistentTerminal: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var limitState by remember { mutableStateOf<CodexLimitsState>(CodexLimitsState.Loading) }
    var limitRefresh by remember { mutableStateOf(0) }
    var chats by remember { mutableStateOf<List<CodexThreadSummary>>(emptyList()) }
    var persistentTerminals by remember { mutableStateOf<List<String>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }
    LaunchedEffect(limitRefresh, sessions.size) {
        limitState = CodexLimitsState.Loading
        catalogLoading = true
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                CodexUsageReader(context).read(),
                runCatching { CodexThreadCatalog(context).read() }.getOrDefault(emptyList()),
                runCatching { ZmxSessionCatalog(context).read() }.getOrDefault(emptyList()),
            )
        }
        limitState = loaded.first
        chats = loaded.second
        persistentTerminals = loaded.third
        catalogLoading = false
    }

    val entries = remember(chats, sessions, persistentTerminals, revision) {
        buildList<WorkspaceEntry> {
            chats.forEach { add(WorkspaceEntry.Chat(it)) }
            sessions.forEach { add(WorkspaceEntry.TerminalSession(it)) }
            val attachedNames = sessions.mapTo(mutableSetOf()) { it.persistentSessionName }
            persistentTerminals.filterNot(attachedNames::contains).forEach {
                add(WorkspaceEntry.PersistentTerminal(it))
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFFECEBE7), CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = Ink, fontSize = 15.sp) }
            Spacer(Modifier.weight(1f))
            Text("Pandora", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Ink, CircleShape)
                    .clickable(onClick = onNewTerminal),
                contentAlignment = Alignment.Center,
            ) { Text(">_", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }

        Spacer(Modifier.height(18.dp))
        if (entries.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("✦", color = Accent, fontSize = 25.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (catalogLoading) "Loading your workspace…" else "Your workspace is ready",
                    color = Ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text("Chats and terminals will appear here.", color = Muted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    WorkspaceEntryRow(
                        entry = entry,
                        onOpenChat = onOpenChat,
                        onOpenTerminal = onOpenTerminal,
                        onOpenPersistentTerminal = onOpenPersistentTerminal,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        CodexLimitsFooter(state = limitState, onRefresh = { limitRefresh++ })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                modifier = Modifier
                    .background(Ink, RoundedCornerShape(24.dp))
                    .clickable(onClick = onNewChat)
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✦", color = Color(0xFFBEB3FF), fontSize = 15.sp)
                Spacer(Modifier.width(9.dp))
                Text("New chat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
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

@Composable
private fun WorkspaceEntryRow(
    entry: WorkspaceEntry,
    onOpenChat: (String) -> Unit,
    onOpenTerminal: (ManagedTerminalSession) -> Unit,
    onOpenPersistentTerminal: (String) -> Unit,
) {
    val isChat = entry is WorkspaceEntry.Chat
    val title = when (entry) {
        is WorkspaceEntry.Chat -> entry.chat.title
        is WorkspaceEntry.TerminalSession -> entry.session.title
        is WorkspaceEntry.PersistentTerminal -> entry.name.removePrefix("pandora-").let { "Terminal $it" }
    }
    val subtitle = when (entry) {
        is WorkspaceEntry.Chat -> entry.chat.preview.ifBlank { "Codex conversation" }
        is WorkspaceEntry.TerminalSession -> when (entry.session.state.value) {
            PtyTerminalSession.State.PREPARING -> "Starting Linux…"
            PtyTerminalSession.State.RUNNING -> "Linux terminal · running"
            PtyTerminalSession.State.STOPPED -> "Linux terminal · ended"
            PtyTerminalSession.State.FAILED -> "Linux terminal · failed"
        }
        is WorkspaceEntry.PersistentTerminal -> "Linux terminal · detached · tap to reconnect"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (entry) {
                    is WorkspaceEntry.Chat -> onOpenChat(entry.chat.id)
                    is WorkspaceEntry.TerminalSession -> onOpenTerminal(entry.session)
                    is WorkspaceEntry.PersistentTerminal -> onOpenPersistentTerminal(entry.name)
                }
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(if (isChat) Color(0xFFE9E6FA) else Color(0xFFE8F0E8), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isChat) "✦" else ">_",
                color = if (isChat) Accent else Color(0xFF3A8450),
                fontSize = if (isChat) 17.sp else 11.sp,
                fontFamily = if (isChat) FontFamily.Default else FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        if (entry.updatedAtMillis > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(formatRelativeTime(entry.updatedAtMillis), color = Color(0xFFA1A29D), fontSize = 11.sp)
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
private fun CodexLimitsFooter(state: CodexLimitsState, onRefresh: () -> Unit) {
    val (color, label) = when (state) {
        CodexLimitsState.Loading -> Accent to "Checking Codex limits…"
        CodexLimitsState.SignedOut -> Muted to "Sign in to see Codex limits"
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
        modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Muted, fontSize = 13.sp, maxLines = 1)
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
            .background(Color(0xFFECEBE7), RoundedCornerShape(100.dp))
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
private fun ChatScreen(session: CodexChatSession, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var draft by remember { mutableStateOf("") }
    val messages by session.messages.collectAsState()
    val state by session.state.collectAsState()
    val models by session.models.collectAsState()
    val selectedModel by session.selectedModel.collectAsState()
    var modelMenuOpen by remember { mutableStateOf(false) }
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
        if (session.send(draft)) draft = ""
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(Color(0xFFEAEAE5), CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", color = Ink, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Codex chat", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    Text(
                        "$detail  ▾",
                        modifier = Modifier.clickable(enabled = models.isNotEmpty()) { modelMenuOpen = true },
                        color = if (models.isNotEmpty()) Accent else Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
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
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(52.dp).background(Color(0xFFE9E6FA), RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("✦", color = Accent, fontSize = 22.sp) }
                Spacer(Modifier.height(18.dp))
                Text("Start with an idea", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state) {
                        is CodexChatState.Starting -> "Starting the native Codex agent harness…"
                        is CodexChatState.Failed -> (state as CodexChatState.Failed).detail
                        else -> "Codex can inspect, run, and edit files in your local workspace."
                    },
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message -> ChatMessageRow(message) }
            }
        }
        Row(
            modifier = Modifier.padding(16.dp).border(1.dp, Line, RoundedCornerShape(19.dp)).padding(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = ready,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            if (ready) "Message Codex…" else "Codex is not ready…",
                            color = Color(0xFFA2A39E),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .background(if (ready && draft.isNotBlank()) Accent else Color(0xFFDADAD4), CircleShape)
                    .clickable(enabled = ready && draft.isNotBlank(), onClick = { submit() }),
                contentAlignment = Alignment.Center
            ) { Text("↑", color = Color.White, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    when (message.role) {
        ChatRole.SYSTEM -> Text(
            message.text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            color = Color(0xFFD18B27),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        ChatRole.USER, ChatRole.ASSISTANT -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.role == ChatRole.USER) Arrangement.End else Arrangement.Start,
        ) {
            Text(
                message.text,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(
                        if (message.role == ChatRole.USER) Ink else Color(0xFFECEBE7),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                color = if (message.role == ChatRole.USER) Color.White else Ink,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ContainerScreen(
    session: ManagedTerminalSession,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    TerminalScreen(
        session = session,
        onBack = onBack,
        onStop = onStop,
    )
}
