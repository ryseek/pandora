package com.pandora.mobile.linux

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ArchivedWorkspace(
    val projectPaths: Set<String> = emptySet(),
    val chatIds: Set<String> = emptySet(),
)

/** Durable, Pandora-local archive state. Archiving never removes Codex threads or project files. */
class ArchiveCatalog(context: Context) {
    private val file = File(context.filesDir, "linux-workspace/.pandora/archive.json")

    fun read(): ArchivedWorkspace = synchronized(LOCK) {
        runCatching {
            val data = JSONObject(file.readText())
            ArchivedWorkspace(
                projectPaths = data.optJSONArray("projectPaths").toStringSet(),
                chatIds = data.optJSONArray("chatIds").toStringSet(),
            )
        }.getOrDefault(ArchivedWorkspace())
    }

    fun setProjectArchived(path: String, archived: Boolean): ArchivedWorkspace = synchronized(LOCK) {
        update { current ->
            current.copy(projectPaths = current.projectPaths.withValue(path, archived))
        }
    }

    fun setChatArchived(threadId: String, archived: Boolean): ArchivedWorkspace = synchronized(LOCK) {
        update { current ->
            current.copy(chatIds = current.chatIds.withValue(threadId, archived))
        }
    }

    fun removeChats(threadIds: Collection<String>): ArchivedWorkspace = synchronized(LOCK) {
        update { current -> current.copy(chatIds = current.chatIds - threadIds.toSet()) }
    }

    private fun update(block: (ArchivedWorkspace) -> ArchivedWorkspace): ArchivedWorkspace {
        val updated = block(read())
        write(updated)
        return updated
    }

    private fun write(archive: ArchivedWorkspace) {
        file.parentFile?.mkdirs()
        val data = JSONObject()
            .put("projectPaths", JSONArray(archive.projectPaths.sorted()))
            .put("chatIds", JSONArray(archive.chatIds.sorted()))
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(data.toString())
        if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
        temporary.delete()
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val values = this@toStringSet ?: return@buildSet
        for (index in 0 until values.length()) {
            values.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun Set<String>.withValue(value: String, included: Boolean): Set<String> =
        if (included) this + value else this - value

    private companion object {
        val LOCK = Any()
    }
}
