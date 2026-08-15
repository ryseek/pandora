package com.pandora.mobile

import android.content.Context
import com.pandora.mobile.linux.CodexChatSession
import com.pandora.mobile.linux.CodexChatState
import com.pandora.mobile.linux.RootfsInstaller
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns every live Codex connection so changing screens never stops unrelated tasks. */
class CodexChatSessionManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sessions = MutableStateFlow<List<CodexChatSession>>(emptyList())
    val sessions: StateFlow<List<CodexChatSession>> = _sessions.asStateFlow()
    private val observationJobs = mutableMapOf<String, Job>()
    private val notifications = AgentNotificationController(appContext)
    private var staleProcessesCleaned = false

    @Synchronized
    fun create(threadId: String? = null, cwd: String = "/root"): CodexChatSession {
        if (threadId != null) {
            _sessions.value.firstOrNull {
                it.activeThreadId == threadId &&
                    it.state.value !is CodexChatState.Closed &&
                    it.state.value !is CodexChatState.Failed
            }?.let { return it }

            val replaced = _sessions.value.filter { it.activeThreadId == threadId }
            if (replaced.isNotEmpty()) {
                _sessions.value = _sessions.value - replaced.toSet()
                replaced.forEach {
                    observationJobs.remove(it.id)?.cancel()
                    it.close()
                }
            }
        }

        cleanStaleProcessesOnce()
        val session = CodexChatSession(
            context = appContext,
            id = UUID.randomUUID().toString(),
            resumeThreadId = threadId,
            cwd = cwd,
        )
        _sessions.value = _sessions.value + session
        observe(session)
        refreshForegroundService()
        return session
    }

    fun find(id: String?): CodexChatSession? = _sessions.value.firstOrNull { it.id == id }

    fun findThread(threadId: String): CodexChatSession? =
        _sessions.value.firstOrNull { it.activeThreadId == threadId }

    @Synchronized
    fun stop(id: String) {
        val session = find(id) ?: return
        _sessions.value = _sessions.value.filterNot { it.id == id }
        observationJobs.remove(id)?.cancel()
        session.close()
        refreshForegroundService()
    }

    fun stopThread(threadId: String) {
        findThread(threadId)?.let { stop(it.id) }
    }

    @Synchronized
    fun stopAll() {
        val current = _sessions.value
        _sessions.value = emptyList()
        observationJobs.values.forEach { it.cancel() }
        observationJobs.clear()
        current.forEach(CodexChatSession::close)
        refreshForegroundService()
    }

    private fun observe(session: CodexChatSession) {
        observationJobs[session.id] = scope.launch {
            var previous: CodexChatState = session.state.value
            session.state.collect { current ->
                refreshForegroundService()
                if (previous is CodexChatState.Running && current is CodexChatState.Ready) {
                    notifications.send("Codex is ready", "Your task finished and is ready to review.")
                }
                previous = current
            }
        }
    }

    @Synchronized
    private fun cleanStaleProcessesOnce() {
        if (staleProcessesCleaned) return
        RootfsInstaller(appContext).terminateStaleCodexAppServers()
        staleProcessesCleaned = true
    }

    private fun refreshForegroundService() {
        val active = _sessions.value.any {
            it.state.value !is CodexChatState.Closed && it.state.value !is CodexChatState.Failed
        }
        LinuxSessionService.setChatActive(appContext, active)
    }
}
