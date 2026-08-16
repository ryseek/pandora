package com.pandora.mobile.linux

import java.util.UUID

const val GENERAL_CHAT_WORKSPACE_ROOT = "/root/chats"

fun generalChatWorkingDirectory(chatId: String): String {
    UUID.fromString(chatId)
    return "$GENERAL_CHAT_WORKSPACE_ROOT/$chatId"
}

fun isReservedChatWorkspacePath(path: String): Boolean {
    val normalized = path.trimEnd('/')
    return normalized == GENERAL_CHAT_WORKSPACE_ROOT ||
        normalized.startsWith("$GENERAL_CHAT_WORKSPACE_ROOT/")
}

fun isGeneralChatWorkingDirectory(path: String): Boolean {
    val normalized = path.trimEnd('/')
    val prefix = "$GENERAL_CHAT_WORKSPACE_ROOT/"
    if (!normalized.startsWith(prefix)) return false
    val id = normalized.removePrefix(prefix)
    if (id.isBlank() || '/' in id) return false
    return runCatching { UUID.fromString(id) }.isSuccess
}
