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
    private const val DICTATION_PROCESSOR = "dictation_processor"
    private const val REFINE_DICTATION_WITH_WHISPER = "refine_dictation_with_whisper"
    private const val TEXT_TO_SPEECH_MODEL = "text_to_speech_model"
    private const val SPEAK_ASSISTANT_RESPONSES = "speak_assistant_responses"
    private const val ONBOARDING_COMPLETED = "onboarding_completed"

    enum class ThemePreference { SYSTEM, LIGHT, DARK }

    enum class DictationProcessor { CPU, GPU }

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

    fun speechToTextModel(context: Context): String {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(SPEECH_TO_TEXT_MODEL, SpeechModels.DEFAULT_STT_ID)
            ?: SpeechModels.DEFAULT_STT_ID
        return stored.takeIf {
            SpeechModels.find(it)?.kind == SpeechModelKind.SPEECH_TO_TEXT
        } ?: SpeechModels.DEFAULT_STT_ID
    }

    fun setSpeechToTextModel(context: Context, modelId: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(SPEECH_TO_TEXT_MODEL, modelId).apply()
    }

    fun dictationProcessor(context: Context): DictationProcessor {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(DICTATION_PROCESSOR, DictationProcessor.CPU.name)
        return runCatching { DictationProcessor.valueOf(stored.orEmpty()) }
            .getOrDefault(DictationProcessor.CPU)
    }

    fun setDictationProcessor(context: Context, processor: DictationProcessor) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(DICTATION_PROCESSOR, processor.name).apply()
    }

    fun refineDictationWithWhisper(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(REFINE_DICTATION_WITH_WHISPER, false)

    fun setRefineDictationWithWhisper(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(REFINE_DICTATION_WITH_WHISPER, enabled).apply()
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

    fun onboardingCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ONBOARDING_COMPLETED, completed).apply()
    }
}
