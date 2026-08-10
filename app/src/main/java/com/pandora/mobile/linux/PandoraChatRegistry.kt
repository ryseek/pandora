package com.pandora.mobile.linux

import android.content.Context
import java.io.File

/** Exact durable set of Codex thread IDs created by Pandora's New chat action. */
class PandoraChatRegistry(context: Context) {
    private val file = File(context.filesDir, "linux-workspace/.pandora/chat-thread-ids")

    fun read(): Set<String> = synchronized(LOCK) {
        file.takeIf(File::isFile)?.readLines()?.map(String::trim)?.filter(String::isNotBlank)?.toSet()
            ?: emptySet()
    }

    fun add(threadId: String) = synchronized(LOCK) {
        if (threadId.isBlank()) return@synchronized
        val ids = read().toMutableSet()
        if (!ids.add(threadId)) return@synchronized
        file.parentFile?.mkdirs()
        file.writeText(ids.sorted().joinToString("\n", postfix = "\n"))
    }

    fun remove(threadId: String) = synchronized(LOCK) {
        val ids = read().toMutableSet()
        if (!ids.remove(threadId)) return@synchronized
        if (ids.isEmpty()) {
            file.delete()
        } else {
            file.writeText(ids.sorted().joinToString("\n", postfix = "\n"))
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
