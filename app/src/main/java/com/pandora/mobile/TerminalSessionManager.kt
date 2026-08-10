package com.pandora.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.openminis.app.ui.terminal.emulator.TerminalEmulator
import com.pandora.mobile.linux.PtyTerminalSession
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManagedTerminalSession internal constructor(
    context: Context,
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    private val initialCommand: String?,
    private val onStateChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pty = PtyTerminalSession(appContext)
    val emulator = TerminalEmulator()
    val state: StateFlow<PtyTerminalSession.State> = pty.state
    private val _status = MutableStateFlow("Preparing Linux…")
    val status: StateFlow<String> = _status.asStateFlow()
    private var initialCommandSent = false
    private var loginUrlOpened = false
    private val loginOutput = StringBuilder()

    init {
        emulator.onResponse = pty::send
        scope.launch {
            pty.output.collect { bytes ->
                emulator.feed(bytes)
                inspectLoginOutput(bytes)
            }
        }
        scope.launch {
            pty.state.collect { current ->
                onStateChanged()
                if (current == PtyTerminalSession.State.RUNNING && initialCommand != null && !initialCommandSent) {
                    initialCommandSent = true
                    delay(350)
                    pty.send((initialCommand + "\r").toByteArray())
                }
            }
        }
        pty.start { _status.value = it }
    }

    fun send(bytes: ByteArray) = pty.send(bytes)

    fun resize(columns: Int, rows: Int) = pty.resize(columns, rows)

    fun stop() {
        pty.stop()
        scope.cancel()
    }

    private fun inspectLoginOutput(bytes: ByteArray) {
        if (initialCommand?.startsWith("codex login") != true || loginUrlOpened) return
        loginOutput.append(bytes.toString(Charsets.UTF_8))
        if (loginOutput.length > 16_384) loginOutput.delete(0, loginOutput.length - 16_384)
        val url = LOGIN_URL.findAll(loginOutput)
            .map { it.value.trimEnd('.', ',', ')', ']', '\u001B') }
            .firstOrNull { it.contains("openai.com") || it.contains("chatgpt.com") }
            ?: return
        loginUrlOpened = true
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        val LOGIN_URL = Regex("""https://[^\s\u001B]+""")
    }
}

class TerminalSessionManager(private val context: Context) {
    private val _sessions = MutableStateFlow<List<ManagedTerminalSession>>(emptyList())
    val sessions: StateFlow<List<ManagedTerminalSession>> = _sessions.asStateFlow()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()
    private var nextSessionNumber = 1

    fun create(initialCommand: String? = null): ManagedTerminalSession {
        lateinit var managed: ManagedTerminalSession
        managed = ManagedTerminalSession(
            context = context,
            id = UUID.randomUUID().toString(),
            title = "Linux session ${nextSessionNumber++}",
            createdAtMillis = System.currentTimeMillis(),
            initialCommand = initialCommand,
            onStateChanged = ::refreshForegroundService,
        )
        _sessions.value = _sessions.value + managed
        refreshForegroundService()
        return managed
    }

    fun find(id: String?): ManagedTerminalSession? = _sessions.value.firstOrNull { it.id == id }

    fun stop(id: String) {
        val session = find(id) ?: return
        _sessions.value = _sessions.value - session
        session.stop()
        refreshForegroundService()
    }

    fun stopAll() {
        val current = _sessions.value
        _sessions.value = emptyList()
        current.forEach { it.stop() }
        refreshForegroundService()
    }

    private fun refreshForegroundService() {
        _revision.value += 1
        val running = _sessions.value.any {
            it.state.value == PtyTerminalSession.State.PREPARING ||
                it.state.value == PtyTerminalSession.State.RUNNING
        }
        if (running) LinuxSessionService.start(context) else LinuxSessionService.stop(context)
    }
}
