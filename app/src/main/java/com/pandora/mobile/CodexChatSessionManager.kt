package com.pandora.mobile

import android.content.Context
import com.pandora.mobile.linux.CodexChatSession

class CodexChatSessionManager(private val context: Context) {
    var current: CodexChatSession? = null
        private set

    fun create(threadId: String? = null): CodexChatSession {
        current?.close()
        return CodexChatSession(context, threadId).also { current = it }
    }
}
