package com.pandora.mobile.linux

import com.pandora.mobile.BuildConfig
import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

data class CodexThreadSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val cwd: String,
)

/** Reads durable native Codex threads without keeping another app-server process alive. */
class CodexThreadCatalog(context: Context) {
    private val installer = RootfsInstaller(context.applicationContext)
    private val registry = PandoraChatRegistry(context.applicationContext)
    private val cache = File(installer.workspace, ".pandora/chat-list-cache.json")

    fun readCached(): List<CodexThreadSummary> = synchronized(CACHE_LOCK) {
        runCatching {
            val data = JSONArray(cache.readText())
            buildList {
                for (index in 0 until data.length()) {
                    val item = data.getJSONObject(index)
                    add(
                        CodexThreadSummary(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            preview = item.optString("preview"),
                            updatedAtMillis = item.getLong("updatedAtMillis"),
                            cwd = item.optString("cwd", "/root").ifBlank { "/root" },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun read(): List<CodexThreadSummary> {
        installer.installIfNeeded { }
        val process = installer.startContainerProcess(
            installer.containerCommand("/root/.local/bin/codex", "app-server"),
            mergeError = false,
        )
        val stderrDrainer = thread(name = "codex-catalog-stderr", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { } } }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            process.outputStream.bufferedWriter().use { writer ->
                writer.appendLine(
                    JSONObject()
                        .put("method", "initialize")
                        .put("id", 1)
                        .put(
                            "params",
                            JSONObject().put(
                                "clientInfo",
                                JSONObject()
                                    .put("name", "pandora_android")
                                    .put("title", "Pandora Android")
                                    .put("version", BuildConfig.VERSION_NAME),
                            ),
                        )
                        .toString(),
                )
                writer.appendLine(JSONObject().put("method", "initialized").put("params", JSONObject()).toString())
                writer.appendLine(
                    JSONObject()
                        .put("method", "thread/list")
                        .put("id", 2)
                        .put(
                            "params",
                            JSONObject()
                                .put("limit", 100)
                                .put("sortKey", "updated_at")
                                .put("sortDirection", "desc"),
                        )
                        .toString(),
                )
                writer.flush()
                val response = executor.submit<JSONObject> {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                            .first { it.optInt("id", -1) == 2 }
                    }
                }.get(30, TimeUnit.SECONDS)
                response.optJSONObject("error")?.let {
                    error(it.optString("message", "Could not load Codex threads"))
                }
                val data = response.getJSONObject("result").getJSONArray("data")
                val registeredIds = registry.read()
                val threads = buildList {
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val taggedByPandora = item.optString("threadSource") == "pandora_android"
                        if (id !in registeredIds && !taggedByPandora) continue
                        val preview = item.optString("preview").trim()
                        val name = if (item.isNull("name")) "" else item.optString("name").trim()
                        add(
                            CodexThreadSummary(
                                id = id,
                                title = name.ifBlank { preview.lineSequence().firstOrNull().orEmpty() }
                                    .ifBlank { "Codex chat" }
                                    .take(64),
                                preview = preview,
                                updatedAtMillis = item.optLong("updatedAt") * 1000L,
                                cwd = item.optString("cwd", "/root").ifBlank { "/root" },
                            ),
                        )
                    }
                }
                writeCache(threads)
                return threads
            }
        } finally {
            installer.terminateProcessTree(process)
            executor.shutdownNow()
            runCatching { stderrDrainer.join(500) }
        }
    }

    fun rename(threadId: String, newName: String) {
        val name = newName.trim().take(64)
        require(name.isNotBlank()) { "Enter a name for this chat" }
        request("thread/name/set", JSONObject().put("threadId", threadId).put("name", name))
        writeCache(readCached().map { if (it.id == threadId) it.copy(title = name) else it })
    }

    fun delete(threadId: String) {
        request("thread/delete", JSONObject().put("threadId", threadId))
        registry.remove(threadId)
        writeCache(readCached().filterNot { it.id == threadId })
    }

    private fun request(method: String, params: JSONObject) {
        installer.installIfNeeded { }
        val process = installer.startContainerProcess(
            installer.containerCommand("/root/.local/bin/codex", "app-server"),
            mergeError = false,
        )
        val stderrDrainer = thread(name = "codex-action-stderr", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { } } }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            process.outputStream.bufferedWriter().use { writer ->
                writer.appendLine(
                    JSONObject()
                        .put("method", "initialize")
                        .put("id", 1)
                        .put(
                            "params",
                            JSONObject().put(
                                "clientInfo",
                                JSONObject()
                                    .put("name", "pandora_android")
                                    .put("title", "Pandora Android")
                                    .put("version", BuildConfig.VERSION_NAME),
                            ),
                        )
                        .toString(),
                )
                writer.appendLine(JSONObject().put("method", "initialized").put("params", JSONObject()).toString())
                writer.appendLine(JSONObject().put("method", method).put("id", 2).put("params", params).toString())
                writer.flush()
                val response = executor.submit<JSONObject> {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                            .first { it.optInt("id", -1) == 2 }
                    }
                }.get(30, TimeUnit.SECONDS)
                response.optJSONObject("error")?.let {
                    error(it.optString("message", "Could not update this chat"))
                }
            }
        } finally {
            installer.terminateProcessTree(process)
            executor.shutdownNow()
            runCatching { stderrDrainer.join(500) }
        }
    }

    private fun writeCache(threads: List<CodexThreadSummary>) = synchronized(CACHE_LOCK) {
        val data = JSONArray()
        threads.forEach { thread ->
            data.put(
                JSONObject()
                    .put("id", thread.id)
                    .put("title", thread.title)
                    .put("preview", thread.preview)
                    .put("updatedAtMillis", thread.updatedAtMillis)
                    .put("cwd", thread.cwd),
            )
        }
        cache.parentFile?.mkdirs()
        val temporary = File(cache.parentFile, "${cache.name}.tmp")
        temporary.writeText(data.toString())
        if (!temporary.renameTo(cache)) temporary.copyTo(cache, overwrite = true)
        temporary.delete()
    }

    private companion object {
        val CACHE_LOCK = Any()
    }
}
