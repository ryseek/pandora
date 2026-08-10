package com.pandora.mobile.linux

import android.content.Context
import com.pandora.mobile.LinuxSessionService
import java.io.BufferedWriter
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
)

data class CodexModel(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
)

sealed interface CodexChatState {
    data class Starting(val detail: String) : CodexChatState
    data class Ready(val model: String) : CodexChatState
    data class Running(val model: String) : CodexChatState
    data class Failed(val detail: String) : CodexChatState
    data object Closed : CodexChatState
}

/**
 * A native Codex agent session backed by `codex app-server` over newline-delimited JSON-RPC.
 * Codex owns the thread, context, tools, and workspace mutations; Android only renders events.
 */
class CodexChatSession(
    context: Context,
    private val resumeThreadId: String? = null,
) {
    private val appContext = context.applicationContext
    private val installer = RootfsInstaller(appContext)
    private val registry = PandoraChatRegistry(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val nextRequestId = AtomicInteger(4)
    private val writerLock = Any()
    private val turnRequestIds = mutableSetOf<Int>()
    private val assistantMessageIds = mutableMapOf<String, String>()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _state = MutableStateFlow<CodexChatState>(CodexChatState.Starting("Preparing Codex…"))
    val state: StateFlow<CodexChatState> = _state.asStateFlow()

    private val _models = MutableStateFlow<List<CodexModel>>(emptyList())
    val models: StateFlow<List<CodexModel>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var threadId: String? = null
    @Volatile private var model: String = "Codex"

    init {
        LinuxSessionService.setChatActive(appContext, true)
        scope.launch { start() }
    }

    fun send(text: String): Boolean {
        val prompt = text.trim()
        val activeThread = threadId ?: return false
        if (prompt.isEmpty() || _state.value !is CodexChatState.Ready) return false

        _messages.value = _messages.value + ChatMessage(role = ChatRole.USER, text = prompt)
        _state.value = CodexChatState.Running(model)
        val requestId = nextRequestId.getAndIncrement()
        synchronized(turnRequestIds) { turnRequestIds += requestId }
        val params = JSONObject()
            .put("threadId", activeThread)
            .put(
                "input",
                JSONArray().put(JSONObject().put("type", "text").put("text", prompt)),
            )
        _selectedModel.value?.let { params.put("model", it) }
        return if (sendRequest(requestId, "turn/start", params)) {
            true
        } else {
            synchronized(turnRequestIds) { turnRequestIds -= requestId }
            fail("Could not send the message to Codex")
            false
        }
    }

    fun selectModel(model: String) {
        if (_models.value.none { it.model == model }) return
        _selectedModel.value = model
        this.model = model
        if (_state.value is CodexChatState.Ready) _state.value = CodexChatState.Ready(model)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { writer?.close() }
        process?.destroy()
        process = null
        writer = null
        _state.value = CodexChatState.Closed
        scope.cancel()
        LinuxSessionService.setChatActive(appContext, false)
    }

    private fun start() {
        try {
            installer.installIfNeeded { detail ->
                if (!closed.get()) _state.value = CodexChatState.Starting(detail)
            }
            check(!closed.get()) { "Chat was closed" }
            check(File(installer.workspace, ".local/bin/codex").exists()) {
                "Codex CLI is not installed yet. Open a Linux session while online to retry installation."
            }

            val command = installer.containerCommand("/root/.local/bin/codex", "app-server")
            val child = installer.startContainerProcess(command, mergeError = false)
            process = child
            writer = child.outputStream.bufferedWriter()

            thread(name = "codex-chat-stderr", isDaemon = true) {
                runCatching {
                    child.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) android.util.Log.w(TAG, line)
                        }
                    }
                }
            }
            thread(name = "codex-chat-events", isDaemon = true) {
                try {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach(::handleLine)
                    }
                    if (!closed.get() && _state.value !is CodexChatState.Failed) {
                        fail("Codex stopped unexpectedly")
                    }
                } catch (error: Throwable) {
                    if (!closed.get() && _state.value !is CodexChatState.Failed) {
                        fail(error.message ?: "Codex connection failed")
                    }
                }
            }

            val clientInfo = JSONObject()
                .put("name", "pandora_android")
                .put("title", "Pandora Android")
                .put("version", "0.1.0")
            sendRequest(1, "initialize", JSONObject().put("clientInfo", clientInfo))
        } catch (error: Throwable) {
            if (!closed.get()) fail(error.message ?: "Could not start Codex")
        }
    }

    private fun handleLine(line: String) {
        val message = runCatching { JSONObject(line) }.getOrNull() ?: return
        when {
            message.has("id") && (message.has("result") || message.has("error")) -> handleResponse(message)
            message.has("id") && message.has("method") -> handleServerRequest(message)
            message.has("method") -> handleNotification(message.optString("method"), message.optJSONObject("params"))
        }
    }

    private fun handleResponse(message: JSONObject) {
        val id = message.optInt("id", -1)
        message.optJSONObject("error")?.let { error ->
            val detail = error.optString("message", "Codex request failed")
            if (id == 1 || id == 2) {
                fail(detail)
            } else if (synchronized(turnRequestIds) { turnRequestIds.remove(id) }) {
                addSystemMessage(detail)
                if (!closed.get()) _state.value = CodexChatState.Ready(model)
            }
            return
        }

        when (id) {
            1 -> {
                sendNotification("initialized", JSONObject())
                val params = JSONObject()
                    .put("cwd", "/root")
                    .put("sandbox", "danger-full-access")
                    .put("approvalPolicy", "never")
                if (resumeThreadId == null) {
                    params.put("threadSource", "pandora_android")
                    sendRequest(2, "thread/start", params)
                } else {
                    params.put("threadId", resumeThreadId)
                    sendRequest(2, "thread/resume", params)
                }
                sendRequest(3, "model/list", JSONObject().put("limit", 100))
            }
            2 -> {
                val result = message.optJSONObject("result") ?: return fail("Codex returned no thread")
                threadId = result.optJSONObject("thread")?.optString("id").orEmpty().ifBlank { null }
                if (threadId == null) return fail("Codex returned an invalid thread")
                if (resumeThreadId == null) registry.add(threadId!!)
                model = result.optString("model", "Codex").ifBlank { "Codex" }
                _selectedModel.value = model
                loadHistory(result.optJSONObject("thread"))
                _state.value = CodexChatState.Ready(model)
            }
            3 -> {
                val data = message.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
                _models.value = buildList {
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        val modelId = item.optString("model")
                        if (modelId.isBlank()) continue
                        add(
                            CodexModel(
                                id = item.optString("id", modelId),
                                model = modelId,
                                displayName = item.optString("displayName", modelId),
                                description = item.optString("description"),
                                isDefault = item.optBoolean("isDefault"),
                            ),
                        )
                    }
                }
            }
            else -> synchronized(turnRequestIds) { turnRequestIds.remove(id) }
        }
    }

    private fun loadHistory(thread: JSONObject?) {
        val turns = thread?.optJSONArray("turns") ?: return
        val history = mutableListOf<ChatMessage>()
        for (turnIndex in 0 until turns.length()) {
            val items = turns.optJSONObject(turnIndex)?.optJSONArray("items") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val itemId = item.optString("id", UUID.randomUUID().toString())
                when (item.optString("type")) {
                    "userMessage" -> {
                        val content = item.optJSONArray("content") ?: JSONArray()
                        val text = buildString {
                            for (contentIndex in 0 until content.length()) {
                                val input = content.optJSONObject(contentIndex) ?: continue
                                if (input.optString("type") == "text") append(input.optString("text"))
                            }
                        }
                        if (text.isNotBlank()) history += ChatMessage(itemId, ChatRole.USER, text)
                    }
                    "agentMessage" -> {
                        val text = item.optString("text")
                        if (text.isNotBlank()) {
                            assistantMessageIds[itemId] = itemId
                            history += ChatMessage(itemId, ChatRole.ASSISTANT, text)
                        }
                    }
                }
            }
        }
        _messages.value = history
    }

    private fun handleNotification(method: String, params: JSONObject?) {
        if (params == null) return
        when (method) {
            "item/agentMessage/delta" -> appendAgentDelta(
                itemId = params.optString("itemId"),
                delta = params.optString("delta"),
            )
            "turn/completed" -> {
                val turn = params.optJSONObject("turn")
                val status = turn?.optString("status").orEmpty()
                val error = turn?.optJSONObject("error")?.optString("message").orEmpty()
                if (status == "failed" && error.isNotBlank()) {
                    addSystemMessage(error)
                }
                if (!closed.get()) _state.value = CodexChatState.Ready(model)
            }
            "error" -> {
                val detail = params.optString("message", "Codex reported an error")
                addSystemMessage(detail)
            }
        }
    }

    /** Do not leave the harness blocked if a future tool requests interaction unexpectedly. */
    private fun handleServerRequest(message: JSONObject) {
        val id = message.get("id")
        val method = message.optString("method")
        val result = when (method) {
            "item/commandExecution/requestApproval", "item/fileChange/requestApproval" ->
                JSONObject().put("decision", "decline")
            "item/tool/requestUserInput" -> JSONObject().put("answers", JSONObject())
            "mcpServer/elicitation/request" -> JSONObject().put("action", "decline")
            "item/permissions/requestApproval" -> JSONObject().put("permissions", JSONObject())
            else -> {
                sendError(id, -32601, "Pandora does not implement $method")
                addSystemMessage("Codex requested unsupported interaction: $method")
                return
            }
        }
        sendResponse(id, result)
        addSystemMessage("Codex requested unsupported interaction: $method")
    }

    private fun appendAgentDelta(itemId: String, delta: String) {
        if (itemId.isBlank() || delta.isEmpty()) return
        val messageId = assistantMessageIds.getOrPut(itemId) { UUID.randomUUID().toString() }
        val current = _messages.value.toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            current[index] = current[index].copy(text = current[index].text + delta)
        } else {
            current += ChatMessage(id = messageId, role = ChatRole.ASSISTANT, text = delta)
        }
        _messages.value = current
    }

    private fun addSystemMessage(detail: String) {
        if (detail.isBlank()) return
        _messages.value = _messages.value + ChatMessage(role = ChatRole.SYSTEM, text = detail)
    }

    private fun fail(detail: String) {
        addSystemMessage(detail)
        _state.value = CodexChatState.Failed(detail)
        runCatching { writer?.close() }
        process?.destroy()
        writer = null
        process = null
        LinuxSessionService.setChatActive(appContext, false)
    }

    private fun sendRequest(id: Int, method: String, params: JSONObject): Boolean =
        writeJson(JSONObject().put("id", id).put("method", method).put("params", params))

    private fun sendNotification(method: String, params: JSONObject): Boolean =
        writeJson(JSONObject().put("method", method).put("params", params))

    private fun sendResponse(id: Any, result: JSONObject): Boolean =
        writeJson(JSONObject().put("id", id).put("result", result))

    private fun sendError(id: Any, code: Int, detail: String): Boolean = writeJson(
        JSONObject()
            .put("id", id)
            .put("error", JSONObject().put("code", code).put("message", detail)),
    )

    private fun writeJson(json: JSONObject): Boolean = synchronized(writerLock) {
        val output = writer ?: return@synchronized false
        runCatching {
            output.appendLine(json.toString())
            output.flush()
        }.isSuccess
    }

    private companion object {
        const val TAG = "CodexChatSession"
    }
}
