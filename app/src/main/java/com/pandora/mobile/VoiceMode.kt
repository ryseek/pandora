package com.pandora.mobile

internal fun isVoiceStopCommand(text: String): Boolean {
    val normalized = text
        .lowercase()
        .replace(Regex("[^a-z ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized in setOf(
        "stop",
        "cancel",
        "stop codex",
        "cancel that",
        "be quiet",
        "never mind",
        "nevermind",
    )
}
