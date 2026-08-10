package com.pandora.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun parsesGitHubStyleTableAsTableBlock() {
        val markdown = """
            | Python quirk | Example | Explanation |
            |---|:---:|---:|
            | Mutable default arguments | `def f(items=[]): ...` | The same list is reused across calls. |
            | Boolean integers | `True == 1` | `bool` is a subclass of `int`. |
        """.trimIndent()

        val block = parseMarkdown(markdown).single()
        assertTrue(block is MarkdownBlock.Table)
        val table = block as MarkdownBlock.Table

        assertEquals(listOf("Python quirk", "Example", "Explanation"), table.headers)
        assertEquals(
            listOf(TableAlignment.START, TableAlignment.CENTER, TableAlignment.END),
            table.alignments,
        )
        assertEquals("`def f(items=[]): ...`", table.rows.first()[1])
    }

    @Test
    fun keepsEscapedAndInlineCodePipesInsideTableCells() {
        val markdown = """
            | Value | Meaning |
            | --- | --- |
            | one \| two | `left | right` |
        """.trimIndent()

        val block = parseMarkdown(markdown).single()
        assertTrue(block is MarkdownBlock.Table)
        val table = block as MarkdownBlock.Table

        assertEquals(listOf("one | two", "`left | right`"), table.rows.single())
    }

    @Test
    fun doesNotMistakeOrdinaryPipeTextForTable() {
        val blocks = parseMarkdown("Use A | B when discussing alternatives.")

        assertEquals(listOf(MarkdownBlock.Paragraph("Use A | B when discussing alternatives.")), blocks)
    }
}
