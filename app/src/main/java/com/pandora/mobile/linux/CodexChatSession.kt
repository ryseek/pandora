package com.pandora.mobile.linux

import android.content.Context
import com.pandora.mobile.BuildConfig
import com.pandora.mobile.ModelProviderSettings
import java.io.BufferedWriter
import java.io.File
import java.net.URLDecoder
import java.net.URLConnection
import java.nio.charset.StandardCharsets
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

enum class ChatAttachmentKind { IMAGE, FILE }

data class ChatAttachment(
    val kind: ChatAttachmentKind,
    val name: String,
    val containerPath: String,
    val mimeType: String = "",
    val sizeBytes: Long? = null,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
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
    val id: String,
    private val resumeThreadId: String? = null,
    private val cwd: String = "/root",
) {
    private val appContext = context.applicationContext
    private val customProvider = ModelProviderSettings.customProvider(appContext)
    private val installer = RootfsInstaller(appContext)
    private val attachmentIndex = ChatAttachmentIndex(installer.workspace)
    private val registry = PandoraChatRegistry(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val nextRequestId = AtomicInteger(4)
    private val writerLock = Any()
    private val turnRequestIds = mutableSetOf<Int>()
    private val turnRequestOrdinals = mutableMapOf<Int, Int>()
    private val assistantMessageIds = mutableMapOf<String, String>()
    @Volatile private var activeTurnId: String? = null
    @Volatile private var interruptRequested = false
    @Volatile private var interruptRequestId: Int? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _state = MutableStateFlow<CodexChatState>(CodexChatState.Starting("Preparing Codex…"))
    val state: StateFlow<CodexChatState> = _state.asStateFlow()

    private val _interrupting = MutableStateFlow(false)
    val interrupting: StateFlow<Boolean> = _interrupting.asStateFlow()

    private val _models = MutableStateFlow(
        customProvider?.modelIds?.mapIndexed { index, modelId ->
            CodexModel(
                id = modelId,
                model = modelId,
                displayName = modelId,
                description = "Configured OpenAI-compatible model",
                isDefault = index == 0,
            )
        }.orEmpty(),
    )
    val models: StateFlow<List<CodexModel>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var threadId: String? = null
    @Volatile private var model: String = customProvider?.defaultModel ?: "Codex"

    /** The requested or resolved thread identity, used to avoid opening a second writer. */
    val activeThreadId: String?
        get() = threadId ?: resumeThreadId

    init {
        scope.launch { start() }
    }

    fun send(text: String, attachments: List<ChatAttachment> = emptyList()): Boolean {
        val prompt = text.trim()
        val activeThread = threadId ?: return false
        if ((prompt.isEmpty() && attachments.isEmpty()) || _state.value !is CodexChatState.Ready) return false

        val userOrdinal = _messages.value.count { it.role == ChatRole.USER }
        _messages.value = _messages.value + ChatMessage(
            role = ChatRole.USER,
            text = prompt,
            attachments = attachments,
        )
        activeTurnId = null
        interruptRequested = false
        _interrupting.value = false
        _state.value = CodexChatState.Running(model)
        val requestId = nextRequestId.getAndIncrement()
        synchronized(turnRequestIds) {
            turnRequestIds += requestId
            turnRequestOrdinals[requestId] = userOrdinal
        }
        val params = JSONObject()
            .put("threadId", activeThread)
            .put("input", buildTurnInput(prompt, attachments))
        _selectedModel.value?.let { params.put("model", it) }
        runCatching { attachmentIndex.record(activeThread, userOrdinal, prompt, attachments) }
            .onFailure { android.util.Log.w(TAG, "Could not persist attachment metadata", it) }
        return if (sendRequest(requestId, "turn/start", params)) {
            true
        } else {
            synchronized(turnRequestIds) {
                turnRequestIds -= requestId
                turnRequestOrdinals -= requestId
            }
            fail("Could not send the message to Codex")
            false
        }
    }

    fun interrupt(): Boolean {
        val activeThread = threadId ?: return false
        if (_state.value !is CodexChatState.Running || _interrupting.value) return false
        _interrupting.value = true
        val turn = activeTurnId
        if (turn == null) {
            interruptRequested = true
            return true
        }
        return sendInterrupt(activeThread, turn)
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
        process?.let(installer::terminateProcessTree)
        process = null
        writer = null
        activeTurnId = null
        interruptRequested = false
        interruptRequestId = null
        _interrupting.value = false
        _state.value = CodexChatState.Closed
        scope.cancel()
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
                .put("version", BuildConfig.VERSION_NAME)
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
            } else if (id == interruptRequestId) {
                interruptRequestId = null
                interruptRequested = false
                _interrupting.value = false
                addSystemMessage("Could not stop Codex: $detail")
            } else if (synchronized(turnRequestIds) { turnRequestIds.remove(id) }) {
                synchronized(turnRequestIds) { turnRequestOrdinals.remove(id) }
                activeTurnId = null
                interruptRequested = false
                _interrupting.value = false
                addSystemMessage(detail)
                if (!closed.get()) _state.value = CodexChatState.Ready(model)
            }
            return
        }

        when (id) {
            1 -> {
                sendNotification("initialized", JSONObject())
                val params = JSONObject()
                    .put("cwd", cwd)
                    .put("sandbox", "danger-full-access")
                    .put("approvalPolicy", "never")
                    .put("developerInstructions", AGENT_ATTACHMENT_INSTRUCTIONS)
                customProvider?.defaultModel?.let { params.put("model", it) }
                if (resumeThreadId == null) {
                    params.put("threadSource", "pandora_android")
                    sendRequest(2, "thread/start", params)
                } else {
                    params.put("threadId", resumeThreadId)
                    sendRequest(2, "thread/resume", params)
                }
                if (customProvider == null) {
                    sendRequest(3, "model/list", JSONObject().put("limit", 100))
                }
            }
            2 -> {
                val result = message.optJSONObject("result") ?: return fail("Codex returned no thread")
                threadId = result.optJSONObject("thread")?.optString("id").orEmpty().ifBlank { null }
                if (threadId == null) return fail("Codex returned an invalid thread")
                if (resumeThreadId == null) registry.add(threadId!!)
                model = customProvider?.defaultModel
                    ?: result.optString("model", "Codex").ifBlank { "Codex" }
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
            else -> {
                val userOrdinal = synchronized(turnRequestIds) {
                    turnRequestIds.remove(id)
                    turnRequestOrdinals.remove(id)
                }
                val responseTurnId = message.optJSONObject("result")
                    ?.optJSONObject("turn")
                    ?.optString("id")
                    .orEmpty()
                if (responseTurnId.isNotBlank()) onTurnStarted(responseTurnId)
                val activeThread = threadId
                if (userOrdinal != null && activeThread != null && responseTurnId.isNotBlank()) {
                    runCatching { attachmentIndex.associateTurn(activeThread, userOrdinal, responseTurnId) }
                        .onFailure { android.util.Log.w(TAG, "Could not associate attachment turn", it) }
                }
            }
        }
    }

    private fun loadHistory(thread: JSONObject?) {
        val turns = thread?.optJSONArray("turns") ?: return
        val history = mutableListOf<ChatMessage>()
        val restoredThreadId = threadId ?: resumeThreadId
        var userOrdinal = 0
        var lastIndexedOrdinal: Int? = null
        val usedIndexedOrdinals = mutableSetOf<Int>()
        for (turnIndex in 0 until turns.length()) {
            val turn = turns.optJSONObject(turnIndex) ?: continue
            val turnId = turn.optString("id")
            val items = turn.optJSONArray("items") ?: continue
            for (itemIndex in 0 until items.length()) {
                val item = items.optJSONObject(itemIndex) ?: continue
                val itemId = item.optString("id", UUID.randomUUID().toString())
                when (item.optString("type")) {
                    "userMessage" -> {
                        val content = item.optJSONArray("content") ?: JSONArray()
                        val attachments = mutableListOf<ChatAttachment>()
                        val text = buildString {
                            for (contentIndex in 0 until content.length()) {
                                val input = content.optJSONObject(contentIndex) ?: continue
                                when (input.optString("type")) {
                                    "text" -> append(input.optString("text"))
                                    "localImage" -> input.optString("path").takeIf(String::isNotBlank)?.let { path ->
                                        attachments += ChatAttachment(
                                            kind = ChatAttachmentKind.IMAGE,
                                            name = attachmentDisplayName(path),
                                            containerPath = path,
                                        )
                                    }
                                    "mention" -> input.optString("path").takeIf(String::isNotBlank)?.let { path ->
                                        attachments += ChatAttachment(
                                            kind = ChatAttachmentKind.FILE,
                                            name = input.optString("name").takeIf(String::isNotBlank) ?: attachmentDisplayName(path),
                                            containerPath = path,
                                        )
                                    }
                                }
                            }
                        }
                        val restored = restoredThreadId?.let {
                            attachmentIndex.attachments(
                                threadId = it,
                                userOrdinal = userOrdinal,
                                text = text,
                                serverAttachments = attachments,
                                turnId = turnId,
                                afterIndexedOrdinal = lastIndexedOrdinal,
                                usedOrdinals = usedIndexedOrdinals,
                            )
                        }
                        restored?.let {
                            usedIndexedOrdinals += it.ordinal
                            lastIndexedOrdinal = it.ordinal
                        }
                        val mergedAttachments = (restored?.attachments.orEmpty() + attachments)
                            .distinctBy(ChatAttachment::containerPath)
                        if (text.isNotBlank() || mergedAttachments.isNotEmpty()) {
                            history += ChatMessage(itemId, ChatRole.USER, text, mergedAttachments)
                        }
                        userOrdinal += 1
                    }
                    "agentMessage" -> {
                        val text = item.optString("text")
                        if (text.isNotBlank()) {
                            assistantMessageIds[itemId] = itemId
                            history += ChatMessage(
                                itemId,
                                ChatRole.ASSISTANT,
                                text,
                                extractAgentAttachments(text, installer.workspace),
                            )
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
            "turn/started" -> params.optJSONObject("turn")
                ?.optString("id")
                ?.takeIf(String::isNotBlank)
                ?.let(::onTurnStarted)
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
                activeTurnId = null
                interruptRequested = false
                interruptRequestId = null
                _interrupting.value = false
                if (!closed.get()) _state.value = CodexChatState.Ready(model)
            }
            "error" -> {
                val detail = params.optString("message", "Codex reported an error")
                addSystemMessage(detail)
            }
        }
    }

    private fun onTurnStarted(turnId: String) {
        activeTurnId = turnId
        if (interruptRequested) {
            val activeThread = threadId ?: return
            interruptRequested = false
            sendInterrupt(activeThread, turnId)
        }
    }

    private fun sendInterrupt(activeThread: String, turnId: String): Boolean {
        val requestId = nextRequestId.getAndIncrement()
        interruptRequestId = requestId
        val sent = sendRequest(
            requestId,
            "turn/interrupt",
            JSONObject().put("threadId", activeThread).put("turnId", turnId),
        )
        if (!sent) {
            interruptRequestId = null
            _interrupting.value = false
            addSystemMessage("Could not send the stop request to Codex")
        }
        return sent
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
            val text = current[index].text + delta
            current[index] = current[index].copy(
                text = text,
                attachments = extractAgentAttachments(text, installer.workspace),
            )
        } else {
            current += ChatMessage(
                id = messageId,
                role = ChatRole.ASSISTANT,
                text = delta,
                attachments = extractAgentAttachments(delta, installer.workspace),
            )
        }
        _messages.value = current
    }

    private fun addSystemMessage(detail: String) {
        if (detail.isBlank()) return
        _messages.value = _messages.value + ChatMessage(role = ChatRole.SYSTEM, text = detail)
    }

    private fun fail(detail: String) {
        addSystemMessage(detail)
        activeTurnId = null
        interruptRequested = false
        interruptRequestId = null
        _interrupting.value = false
        _state.value = CodexChatState.Failed(detail)
        runCatching { writer?.close() }
        process?.let(installer::terminateProcessTree)
        writer = null
        process = null
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

internal data class CodexTurnInputSpec(
    val type: String,
    val text: String? = null,
    val path: String? = null,
    val name: String? = null,
    val detail: String? = null,
)

internal fun buildTurnInputSpecs(text: String, attachments: List<ChatAttachment>): List<CodexTurnInputSpec> = buildList {
    text.trim().takeIf(String::isNotEmpty)?.let { prompt ->
        add(CodexTurnInputSpec(type = "text", text = prompt))
    }
    attachments.forEach { attachment ->
        add(
            when (attachment.kind) {
                ChatAttachmentKind.IMAGE -> CodexTurnInputSpec(
                    type = "localImage",
                    path = attachment.containerPath,
                    detail = "auto",
                )
                ChatAttachmentKind.FILE -> CodexTurnInputSpec(
                    type = "mention",
                    name = attachment.name,
                    path = attachment.containerPath,
                )
            },
        )
    }
}

internal fun buildTurnInput(text: String, attachments: List<ChatAttachment>): JSONArray = JSONArray().apply {
    buildTurnInputSpecs(text, attachments).forEach { input ->
        put(JSONObject().put("type", input.type).apply {
            input.text?.let { put("text", it) }
            input.path?.let { put("path", it) }
            input.name?.let { put("name", it) }
            input.detail?.let { put("detail", it) }
        })
    }
}

internal fun attachmentDisplayName(path: String): String = File(path).name.replace(
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-"),
    "",
)

private val agentFileLink = Regex("\\[([^]]*)]\\(<?(/root/[^)>]+)>?\\)")
private val standaloneAgentFileLink = Regex(
    "(?m)^\\s*(?:(?:[-*+])|(?:\\d+[.)]))\\s+\\[[^]]*]\\(<?/root/[^)>]+>?\\)\\s*$",
)

internal fun extractAgentAttachments(text: String, workspace: File): List<ChatAttachment> = agentFileLink
    .findAll(text)
    .mapNotNull { match ->
        val containerPath = runCatching {
            URLDecoder.decode(match.groupValues[2], StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return@mapNotNull null
        val relative = containerPath.removePrefix("/root/")
        if (relative == containerPath) return@mapNotNull null
        val root = workspace.canonicalFile
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return@mapNotNull null
        val mimeType = guessAttachmentMimeType(file.name)
        ChatAttachment(
            kind = if (mimeType.startsWith("image/")) ChatAttachmentKind.IMAGE else ChatAttachmentKind.FILE,
            name = attachmentDisplayName(containerPath),
            containerPath = containerPath,
            mimeType = mimeType,
            sizeBytes = file.length(),
        )
    }
    .distinctBy(ChatAttachment::containerPath)
    .toList()

internal fun agentDisplayText(text: String): String = standaloneAgentFileLink
    .replace(text, "")
    .let { value -> agentFileLink.replace(value) { match -> match.groupValues[1] } }
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

internal fun guessAttachmentMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "txt", "log" -> "text/plain"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
    }

private const val AGENT_ATTACHMENT_INSTRUCTIONS =
    "When you intend to deliver a file to the user, create it under /root and include an explicit Markdown link " +
        "to its absolute /root path in your final response, for example [report.pdf](/root/project/report.pdf). " +
        "Pandora turns verified local file links into previewable, openable, saveable attachments."
