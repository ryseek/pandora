package com.pandora.mobile

internal enum class SlashCommandId {
    COMPACT,
    REVIEW,
    STATUS,
    MODEL,
    NEW,
}

internal data class SlashCommandDefinition(
    val id: SlashCommandId,
    val name: String,
    val usage: String,
    val description: String,
)

internal val supportedSlashCommands = listOf(
    SlashCommandDefinition(
        id = SlashCommandId.COMPACT,
        name = "compact",
        usage = "/compact",
        description = "Summarize this conversation to free context",
    ),
    SlashCommandDefinition(
        id = SlashCommandId.REVIEW,
        name = "review",
        usage = "/review",
        description = "Review uncommitted workspace changes",
    ),
    SlashCommandDefinition(
        id = SlashCommandId.STATUS,
        name = "status",
        usage = "/status",
        description = "Show this session's model and workspace",
    ),
    SlashCommandDefinition(
        id = SlashCommandId.MODEL,
        name = "model",
        usage = "/model [name]",
        description = "Choose or switch the active model",
    ),
    SlashCommandDefinition(
        id = SlashCommandId.NEW,
        name = "new",
        usage = "/new",
        description = "Start a new chat in this workspace",
    ),
)

internal sealed interface SlashCommandParseResult {
    data class Command(
        val id: SlashCommandId,
        val argument: String? = null,
    ) : SlashCommandParseResult

    data class Invalid(val message: String) : SlashCommandParseResult
}

/** Returns null when [input] should remain an ordinary chat message. */
internal fun parseSlashCommand(input: String): SlashCommandParseResult? {
    val trimmed = input.trim()
    if (!trimmed.startsWith('/') || '\n' in trimmed || '\r' in trimmed) return null

    val body = trimmed.drop(1)
    val separator = body.indexOfFirst(Char::isWhitespace)
    val commandName = body.take(if (separator < 0) body.length else separator).lowercase()
    val definition = supportedSlashCommands.firstOrNull { it.name == commandName } ?: return null
    val argument = if (separator < 0) null else body.drop(separator + 1).trim().takeIf(String::isNotEmpty)
    if (definition.id != SlashCommandId.MODEL && argument != null) {
        return SlashCommandParseResult.Invalid("${definition.usage} does not accept arguments.")
    }
    return SlashCommandParseResult.Command(definition.id, argument)
}

internal fun slashCommandSuggestions(draft: String): List<SlashCommandDefinition> {
    val trimmed = draft.trimStart()
    if (!trimmed.startsWith('/') || trimmed.any(Char::isWhitespace)) return emptyList()
    val query = trimmed.drop(1).lowercase()
    return supportedSlashCommands.filter { it.name.startsWith(query) }
}
