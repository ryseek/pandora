package com.pandora.mobile

import android.app.Application
import android.content.ComponentCallbacks2

class PandoraApplication : Application() {
    val terminalSessions: TerminalSessionManager by lazy { TerminalSessionManager(this) }
    val chatSessions: CodexChatSessionManager by lazy { CodexChatSessionManager(this) }
    val speechModels: SpeechModelManager by lazy { SpeechModelManager(this) }
    val onDeviceSpeech: OnDeviceSpeech by lazy { OnDeviceSpeech(this, speechModels) }
    val adbPlugin: AdbPluginManager by lazy { AdbPluginManager(this) }

    override fun onCreate() {
        super.onCreate()
        onDeviceSpeech.prewarmSelectedModels()
        // The loopback bridge also serves agent notifications, so it remains
        // available even when the optional ADB phone-control plugin is disabled.
        adbPlugin.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            onDeviceSpeech.releaseWarmModels()
        }
    }

    override fun onTerminate() {
        onDeviceSpeech.releaseWarmModels()
        chatSessions.stopAll()
        terminalSessions.stopAll()
        adbPlugin.close()
        super.onTerminate()
    }
}
