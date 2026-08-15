package com.pandora.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test
    fun parsesCommandsAndOptionalModelArgument() {
        assertEquals(
            SlashCommandParseResult.Command(SlashCommandId.COMPACT),
            parseSlashCommand(" /compact "),
        )
        assertEquals(
            SlashCommandParseResult.Command(SlashCommandId.MODEL, "gpt-5.6-sol"),
            parseSlashCommand("/model gpt-5.6-sol"),
        )
        assertEquals(
            SlashCommandParseResult.Command(SlashCommandId.MODEL, "gpt-5.6-sol"),
            parseSlashCommand("/model\tgpt-5.6-sol"),
        )
    }

    @Test
    fun rejectsArgumentsForArgumentlessCommands() {
        val result = parseSlashCommand("/review staged")
        assertTrue(result is SlashCommandParseResult.Invalid)
    }

    @Test
    fun leavesUnknownCommandsAndMultilinePromptsUntouched() {
        assertNull(parseSlashCommand("/future-command"))
        assertNull(parseSlashCommand("/status\nExplain the output"))
    }

    @Test
    fun filtersPaletteSuggestionsByCommandPrefix() {
        assertEquals(listOf("review"), slashCommandSuggestions("/rev").map { it.name })
        assertEquals(5, slashCommandSuggestions("/").size)
        assertTrue(slashCommandSuggestions("/model ").isEmpty())
    }
}
