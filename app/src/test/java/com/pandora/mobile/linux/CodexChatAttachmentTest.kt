package com.pandora.mobile.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexChatAttachmentTest {
    @Test
    fun identifiesAndroidPackagesForDirectInstallation() {
        assertEquals("application/vnd.android.package-archive", guessAttachmentMimeType("pandora.apk"))
    }

    @Test
    fun extractsVerifiedAgentFileLinksAsAttachments() {
        val workspace = Files.createTempDirectory("pandora-agent-attachment").toFile()
        try {
            val report = File(workspace, "project/report.md")
            checkNotNull(report.parentFile).mkdirs()
            report.writeText("# Report")

            val attachments = extractAgentAttachments(
                "Ready: [report.md](/root/project/report.md). Ignore [outside](/root/../etc/passwd).",
                workspace,
            )

            assertEquals(1, attachments.size)
            assertEquals("report.md", attachments.single().name)
            assertEquals("text/markdown", attachments.single().mimeType)
            assertEquals(report.length(), attachments.single().sizeBytes)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun removesStandaloneAgentFileLinksFromDisplayedResponse() {
        val text = """
            They are located here:

            - [first.md](/root/first.md)
            - [second.md](/root/second.md)

            They contain identical content.
        """.trimIndent()

        assertEquals(
            "They are located here:\n\nThey contain identical content.",
            agentDisplayText(text),
        )
        assertEquals(
            "Open report.pdf when ready.",
            agentDisplayText("Open [report.pdf](/root/report.pdf) when ready."),
        )
    }

    @Test
    fun removesAttachmentLinkAfterALongResponseEvenWhenMetadataIsUnavailable() {
        val longBody = (1..2_000).joinToString(" ") { "word$it" }
        val text = "$longBody\n\n- [result.zip](/root/project/result.zip)"

        assertEquals(longBody, agentDisplayText(text))
    }

    @Test
    fun restoresOriginalNameFromStoredAttachmentPath() {
        assertEquals(
            "pandora-projects-final.png",
            attachmentDisplayName("/root/.pandora/attachments/session/1d4d0649-b3d4-4f83-863f-c786da0006cd-pandora-projects-final.png"),
        )
    }

    @Test
    fun buildsMultimodalTurnInputInConversationOrder() {
        val input = buildTurnInputSpecs(
            text = "Review these",
            attachments = listOf(
                ChatAttachment(
                    kind = ChatAttachmentKind.IMAGE,
                    name = "screen.png",
                    containerPath = "/root/.pandora/attachments/chat/screen.png",
                ),
                ChatAttachment(
                    kind = ChatAttachmentKind.FILE,
                    name = "notes.txt",
                    containerPath = "/root/.pandora/attachments/chat/notes.txt",
                ),
            ),
        )

        assertEquals("text", input[0].type)
        assertEquals("Review these", input[0].text)
        assertEquals("localImage", input[1].type)
        assertEquals("auto", input[1].detail)
        assertEquals("mention", input[2].type)
        assertEquals("notes.txt", input[2].name)
    }

    @Test
    fun supportsAttachmentOnlyTurns() {
        val input = buildTurnInputSpecs(
            text = "  ",
            attachments = listOf(
                ChatAttachment(
                    kind = ChatAttachmentKind.FILE,
                    name = "report.pdf",
                    containerPath = "/root/.pandora/attachments/chat/report.pdf",
                ),
            ),
        )

        assertEquals(1, input.size)
        assertEquals("mention", input[0].type)
    }
}
