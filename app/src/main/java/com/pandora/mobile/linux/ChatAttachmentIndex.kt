package com.pandora.mobile.linux

import java.io.File
import java.util.Properties

/** Pandora-owned attachment metadata that Codex thread history does not preserve for generic files. */
internal class ChatAttachmentIndex(private val workspace: File) {
    private val directory = File(workspace, ".pandora/chat-attachment-index")

    fun record(threadId: String, userOrdinal: Int, text: String, attachments: List<ChatAttachment>) {
        if (attachments.isEmpty()) return
        synchronized(lock) {
            directory.mkdirs()
            val file = indexFile(threadId)
            val values = read(file)
            val prefix = "$userOrdinal."
            values.stringPropertyNames().filter { it.startsWith(prefix) }.forEach(values::remove)
            values["${prefix}count"] = attachments.size.toString()
            values["${prefix}text"] = text
            attachments.forEachIndexed { index, attachment ->
                val attachmentPrefix = "$prefix$index."
                values["${attachmentPrefix}kind"] = attachment.kind.name
                values["${attachmentPrefix}name"] = attachment.name
                values["${attachmentPrefix}containerPath"] = attachment.containerPath
                values["${attachmentPrefix}mimeType"] = attachment.mimeType
                attachment.sizeBytes?.let { values["${attachmentPrefix}sizeBytes"] = it.toString() }
            }
            writeAtomically(file, values)
        }
    }

    fun associateTurn(threadId: String, userOrdinal: Int, turnId: String) {
        if (turnId.isBlank()) return
        synchronized(lock) {
            val file = indexFile(threadId)
            val values = read(file)
            if (!values.containsKey("$userOrdinal.count")) return
            values["$userOrdinal.turnId"] = turnId
            writeAtomically(file, values)
        }
    }

    fun attachments(
        threadId: String,
        userOrdinal: Int,
        text: String,
        serverAttachments: List<ChatAttachment>,
        turnId: String = "",
        afterIndexedOrdinal: Int? = null,
        usedOrdinals: Set<Int> = emptySet(),
    ): IndexedAttachmentMatch? = synchronized(lock) {
        val values = read(indexFile(threadId))
        val ordinals = values.stringPropertyNames()
            .mapNotNull { key -> Regex("^(\\d+)\\.count$").matchEntire(key)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()
            .sorted()
            .filterNot(usedOrdinals::contains)
        val serverPaths = serverAttachments.map(ChatAttachment::containerPath).toSet()
        val matchedOrdinal = ordinals.firstOrNull { ordinal ->
            turnId.isNotBlank() && values.getProperty("$ordinal.turnId") == turnId
        } ?: ordinals.firstOrNull { ordinal ->
            serverPaths.isNotEmpty() && storedPaths(values, ordinal).any(serverPaths::contains)
        } ?: ordinals.firstOrNull { ordinal ->
            text.isNotEmpty() && values.getProperty("$ordinal.text") == text
        } ?: ordinals.firstOrNull { ordinal ->
            text.isEmpty() && serverAttachments.isEmpty() && afterIndexedOrdinal != null &&
                ordinal > afterIndexedOrdinal && values.getProperty("$ordinal.text").isNullOrEmpty()
        } ?: userOrdinal.takeIf {
            text.isNotEmpty() && it in ordinals && values.getProperty("$it.text") == text
        }
        val count = matchedOrdinal?.let { values.getProperty("$it.count")?.toIntOrNull() }
            ?: return@synchronized null
        val attachments = buildList {
            repeat(count) { index ->
                values.toAttachment(workspace, "$matchedOrdinal.$index.")?.let(::add)
            }
        }
        IndexedAttachmentMatch(matchedOrdinal, attachments)
    }

    private fun storedPaths(values: Properties, ordinal: Int): List<String> {
        val count = values.getProperty("$ordinal.count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { values.getProperty("$ordinal.$it.containerPath") }
    }

    private fun indexFile(threadId: String): File {
        val safeId = threadId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(directory, "$safeId.properties")
    }

    private fun read(file: File): Properties = Properties().apply {
        if (file.isFile) file.inputStream().buffered().use(::load)
    }

    private fun writeAtomically(file: File, values: Properties) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().buffered().use { values.store(it, "Pandora chat attachment index") }
        check(temporary.renameTo(file) || runCatching {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }.isSuccess) { "Could not persist attachment metadata" }
    }

    private fun Properties.toAttachment(workspace: File, prefix: String): ChatAttachment? {
        val path = getProperty("${prefix}containerPath").orEmpty()
        val relative = path.removePrefix("/root/")
        if (relative == path) return null
        val root = workspace.canonicalFile
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) return null
        return ChatAttachment(
            kind = runCatching { ChatAttachmentKind.valueOf(getProperty("${prefix}kind")) }
                .getOrDefault(ChatAttachmentKind.FILE),
            name = getProperty("${prefix}name", file.name),
            containerPath = path,
            mimeType = getProperty("${prefix}mimeType", ""),
            sizeBytes = getProperty("${prefix}sizeBytes")?.toLongOrNull() ?: file.length(),
        )
    }

    private companion object {
        val lock = Any()
    }
}

internal data class IndexedAttachmentMatch(
    val ordinal: Int,
    val attachments: List<ChatAttachment>,
)
