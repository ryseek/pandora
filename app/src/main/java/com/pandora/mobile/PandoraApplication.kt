package com.pandora.mobile

import android.app.Application

class PandoraApplication : Application() {
    val terminalSessions: TerminalSessionManager by lazy { TerminalSessionManager(this) }
    val chatSessions: CodexChatSessionManager by lazy { CodexChatSessionManager(this) }
}
