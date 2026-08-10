package com.pandora.mobile

import android.content.Context

object AppSettings {
    const val MIN_TERMINAL_FONT_SIZE = 9f
    const val MAX_TERMINAL_FONT_SIZE = 22f
    const val DEFAULT_TERMINAL_FONT_SIZE = 13f

    private const val PREFERENCES = "pandora_settings"
    private const val TERMINAL_FONT_SIZE = "terminal_font_size"
    private const val THEME = "theme"

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
}
