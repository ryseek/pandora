package com.pandora.mobile.linux

import com.pandora.mobile.BuildConfig
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.roundToInt

data class CodexLimitWindow(
    val remainingPercent: Int,
    val durationMinutes: Int,
    val resetsAt: Long,
)

sealed interface CodexLimitsState {
    data object Loading : CodexLimitsState
    data object SignedOut : CodexLimitsState
    data object Unavailable : CodexLimitsState
    data class Available(
        val primary: CodexLimitWindow,
        val secondary: CodexLimitWindow?,
    ) : CodexLimitsState
}

/** Reads ChatGPT-backed Codex allowance through the documented app-server JSON-RPC API. */
class CodexUsageReader(context: Context) {
    private val installer = RootfsInstaller(context.applicationContext)
    private val codex = File(installer.workspace, ".local/bin/codex")
    private val auth = File(installer.workspace, ".codex/auth.json")

    fun read(): CodexLimitsState {
        if (!auth.exists()) return CodexLimitsState.SignedOut
        if (!codex.exists() || !installer.rootfs.exists() || !installer.proot.exists()) {
            return CodexLimitsState.Unavailable
        }
        return runCatching { readFromAppServer() }.getOrDefault(CodexLimitsState.Unavailable)
    }

    private fun readFromAppServer(): CodexLimitsState.Available {
        val command = installer.containerCommand("/root/.local/bin/codex", "app-server")
        val process = installer.startContainerProcess(command, mergeError = false)
        val stderrDrainer = thread(name = "codex-usage-stderr", isDaemon = true) {
            runCatching {
                process.errorStream.bufferedReader().useLines { lines -> lines.forEach { } }
            }
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
                writer.appendLine(JSONObject().put("method", "account/rateLimits/read").put("id", 2).toString())
                writer.flush()

                val response = executor.submit<JSONObject> {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                            .first { message -> message.optInt("id", -1) == 2 }
                    }
                }.get(20, TimeUnit.SECONDS)

                response.optJSONObject("error")?.let { rpcError ->
                    error(rpcError.optString("message", "Codex usage failed"))
                }
                val rateLimits = response.getJSONObject("result").getJSONObject("rateLimits")
                return CodexLimitsState.Available(
                    primary = parseWindow(rateLimits.getJSONObject("primary")),
                    secondary = rateLimits.optJSONObject("secondary")?.let(::parseWindow),
                )
            }
        } finally {
            installer.terminateProcessTree(process)
            executor.shutdownNow()
            runCatching { stderrDrainer.join(500) }
        }
    }

    private fun parseWindow(json: JSONObject): CodexLimitWindow = CodexLimitWindow(
        remainingPercent = (100.0 - json.getDouble("usedPercent")).roundToInt().coerceIn(0, 100),
        durationMinutes = json.getInt("windowDurationMins"),
        resetsAt = json.getLong("resetsAt"),
    )
}
