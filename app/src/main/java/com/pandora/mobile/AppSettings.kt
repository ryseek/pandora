package com.pandora.mobile

import android.content.Context

object AppSettings {
    const val MIN_TERMINAL_FONT_SIZE = 9f
    const val MAX_TERMINAL_FONT_SIZE = 22f
    const val DEFAULT_TERMINAL_FONT_SIZE = 13f

    private const val PREFERENCES = "pandora_settings"
    private const val TERMINAL_FONT_SIZE = "terminal_font_size"
    private const val THEME = "theme"
    private const val SPEECH_TO_TEXT_MODEL = "speech_to_text_model"
    private const val TEXT_TO_SPEECH_MODEL = "text_to_speech_model"
    private const val SPEAK_ASSISTANT_RESPONSES = "speak_assistant_responses"

    enum class ThemePreference { SYSTEM, LIGHT, DARK }

    fun terminalFontSize(context: Context): Float =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getFloat(TERMINAL_FONT_SIZE, DEFAULT_TERMINAL_FONT_SIZE)
            .coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)

    fun setTerminalFontSize(context: Context, size: Float) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putFloat(TERMINAL_FONT_SIZE, size.coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE))
            .apply()
    }

    fun theme(context: Context): ThemePreference {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(THEME, ThemePreference.SYSTEM.name)
        return runCatching { ThemePreference.valueOf(stored.orEmpty()) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    fun setTheme(context: Context, theme: ThemePreference) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME, theme.name)
            .apply()
    }

    fun speechToTextModel(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(SPEECH_TO_TEXT_MODEL, SpeechModels.DEFAULT_STT_ID)
            ?: SpeechModels.DEFAULT_STT_ID

    fun setSpeechToTextModel(context: Context, modelId: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(SPEECH_TO_TEXT_MODEL, modelId).apply()
    }

    fun textToSpeechModel(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TEXT_TO_SPEECH_MODEL, SpeechModels.DEFAULT_TTS_ID)
            ?: SpeechModels.DEFAULT_TTS_ID

    fun setTextToSpeechModel(context: Context, modelId: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(TEXT_TO_SPEECH_MODEL, modelId).apply()
    }

    fun speakAssistantResponses(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(SPEAK_ASSISTANT_RESPONSES, false)

    fun setSpeakAssistantResponses(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(SPEAK_ASSISTANT_RESPONSES, enabled).apply()
    }
}
