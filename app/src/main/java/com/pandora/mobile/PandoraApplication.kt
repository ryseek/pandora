package com.pandora.mobile

import android.app.Application

class PandoraApplication : Application() {
    val terminalSessions: TerminalSessionManager by lazy { TerminalSessionManager(this) }
    val chatSessions: CodexChatSessionManager by lazy { CodexChatSessionManager(this) }
    val speechModels: SpeechModelManager by lazy { SpeechModelManager(this) }
    val onDeviceSpeech: OnDeviceSpeech by lazy { OnDeviceSpeech(this, speechModels) }
}
