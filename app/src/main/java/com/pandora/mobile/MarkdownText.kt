package com.pandora.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val ordered: Boolean, val index: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlignment>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal enum class TableAlignment { START, CENTER, END }

@Composable
fun RichMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Text(
                        inlineMarkdown(block.text),
                        color = color,
                        fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp },
                        lineHeight = when (block.level) { 1 -> 28.sp; 2 -> 25.sp; else -> 23.sp },
                        fontWeight = FontWeight.SemiBold,
                    )
                    is MarkdownBlock.Paragraph -> Text(
                        inlineMarkdown(block.text),
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    is MarkdownBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (block.ordered) "${block.index}." else "•",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            inlineMarkdown(block.text),
                            modifier = Modifier.weight(1f),
                            color = color,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    is MarkdownBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            inlineMarkdown(block.text),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, top = 3.dp, bottom = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    is MarkdownBlock.Code -> Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                block.language.lowercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            block.text,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            color = color,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    is MarkdownBlock.Table -> MarkdownTable(block, color)
                    MarkdownBlock.Rule -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table, color: Color) {
    val columnWidths = remember(table) {
        table.headers.indices.map { column ->
            val longest = sequenceOf(table.headers) + table.rows.asSequence()
            val characters = longest.maxOf { row -> row.getOrElse(column) { "" }.length }
            (characters * 7 + 28).coerceIn(104, 220).dp
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        MarkdownTableRow(table.headers, table.alignments, columnWidths, color, header = true)
        table.rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MarkdownTableRow(row, table.alignments, columnWidths, color, header = false)
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    alignments: List<TableAlignment>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    color: Color,
    header: Boolean,
) {
    Row(
        Modifier
            .height(IntrinsicSize.Min)
            .background(if (header) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
    ) {
        columnWidths.forEachIndexed { index, width ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Text(
                inlineMarkdown(cells.getOrElse(index) { "" }),
                modifier = Modifier
                    .width(width)
                    .padding(horizontal = 12.dp, vertical = if (header) 10.dp else 9.dp),
                color = color,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = when (alignments.getOrElse(index) { TableAlignment.START }) {
                    TableAlignment.START -> TextAlign.Start
                    TableAlignment.CENTER -> TextAlign.Center
                    TableAlignment.END -> TextAlign.End
                },
            )
        }
    }
}

internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var language = ""
    var inCode = false
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph.clear()
        }
    }
    val lines = markdown.lines()
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val raw = lines[lineIndex]
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(language, code.joinToString("\n"))
                code.clear()
                language = ""
                inCode = false
            } else {
                flushParagraph()
                language = line.trim().removePrefix("```").trim()
                inCode = true
            }
            lineIndex++
            continue
        }
        if (inCode) {
            code += raw
            lineIndex++
            continue
        }

        val trimmed = line.trim()
        val table = if (lineIndex + 1 < lines.size) {
            parseTableHeader(trimmed, lines[lineIndex + 1].trim())
        } else {
            null
        }
        if (table != null) {
            flushParagraph()
            val rows = mutableListOf<List<String>>()
            lineIndex += 2
            while (lineIndex < lines.size) {
                val candidate = lines[lineIndex].trim()
                if (candidate.isEmpty() || !candidate.contains('|')) break
                rows += parseTableRow(candidate)
                lineIndex++
            }
            blocks += MarkdownBlock.Table(table.first, table.second, rows)
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed.matches(Regex("^#{1,3}\\s+.*")) -> {
                flushParagraph()
                val marks = trimmed.takeWhile { it == '#' }
                blocks += MarkdownBlock.Heading(marks.length, trimmed.drop(marks.length).trim())
            }
            trimmed.matches(Regex("^[-*_]{3,}$")) -> {
                flushParagraph()
                blocks += MarkdownBlock.Rule
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(trimmed.removePrefix("> "))
            }
            Regex("^[-*+]\\s+.*").matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(false, 0, trimmed.drop(2))
            }
            Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                flushParagraph()
                val match = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)!!
                blocks += MarkdownBlock.Bullet(true, match.groupValues[1].toInt(), match.groupValues[2])
            }
            else -> paragraph += trimmed
        }
        lineIndex++
    }
    if (inCode) blocks += MarkdownBlock.Code(language, code.joinToString("\n"))
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(markdown)) }
}

private fun parseTableHeader(
    headerLine: String,
    delimiterLine: String,
): Pair<List<String>, List<TableAlignment>>? {
    if (!headerLine.contains('|') || !delimiterLine.contains('|')) return null
    val headers = parseTableRow(headerLine)
    val delimiters = parseTableRow(delimiterLine)
    if (headers.isEmpty() || headers.size != delimiters.size) return null

    val alignments = delimiters.map { delimiter ->
        if (!delimiter.matches(Regex("^:?-{3,}:?$"))) return null
        when {
            delimiter.startsWith(':') && delimiter.endsWith(':') -> TableAlignment.CENTER
            delimiter.endsWith(':') -> TableAlignment.END
            else -> TableAlignment.START
        }
    }
    return headers to alignments
}

private fun parseTableRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var escaped = false
    var inCode = false
    line.forEach { character ->
        when {
            escaped -> {
                cell.append(character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == '`' -> {
                inCode = !inCode
                cell.append(character)
            }
            character == '|' && !inCode -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(character)
        }
    }
    if (escaped) cell.append('\\')
    cells += cell.toString().trim()
    if (cells.firstOrNull().isNullOrEmpty()) cells.removeAt(0)
    if (cells.lastOrNull().isNullOrEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("(\\*\\*[^*]+\\*\\*|__[^_]+__|`[^`]+`|~~[^~]+~~|\\[[^]]+]\\([^)]+\\)|\\*[^*]+\\*|_[^_]+_)")
    var cursor = 0
    token.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val raw = match.value
        when {
            raw.startsWith("**") || raw.startsWith("__") -> pushStyled(raw.drop(2).dropLast(2), SpanStyle(fontWeight = FontWeight.Bold))
            raw.startsWith('`') -> pushStyled(
                raw.drop(1).dropLast(1),
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x1A7C6FE8),
                ),
            )
            raw.startsWith("~~") -> pushStyled(raw.drop(2).dropLast(2), SpanStyle(textDecoration = TextDecoration.LineThrough))
            raw.startsWith('[') -> {
                val label = raw.substringAfter('[').substringBefore("](")
                pushStyled(
                    label,
                    SpanStyle(color = Color(0xFF7767E8), textDecoration = TextDecoration.Underline),
                )
            }
            else -> pushStyled(raw.drop(1).dropLast(1), SpanStyle(fontStyle = FontStyle.Italic))
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private fun AnnotatedString.Builder.pushStyled(text: String, style: SpanStyle) {
    pushStyle(style)
    append(text)
    pop()
}
