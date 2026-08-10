package com.pandora.mobile

import android.content.Context
import com.pandora.mobile.linux.CodexChatSession
import com.pandora.mobile.linux.CodexChatState

class CodexChatSessionManager(private val context: Context) {
    var current: CodexChatSession? = null
        private set

    fun create(threadId: String? = null): CodexChatSession {
        val existing = current
        if (
            threadId != null &&
            existing?.activeThreadId == threadId &&
            existing.state.value !is CodexChatState.Closed &&
            existing.state.value !is CodexChatState.Failed
        ) {
            return existing
        }
        current?.close()
        return CodexChatSession(context, threadId).also { current = it }
    }
}
