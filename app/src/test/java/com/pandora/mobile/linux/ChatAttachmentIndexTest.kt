package com.pandora.mobile.linux

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentIndexTest {
    @Test
    fun restoresAttachmentsByThreadAndUserOrdinal() {
        val workspace = Files.createTempDirectory("pandora-attachment-index").toFile()
        try {
            val storedFile = File(workspace, ".pandora/attachments/session/notes.md")
            checkNotNull(storedFile.parentFile).mkdirs()
            storedFile.writeText("hello")
            val attachment = ChatAttachment(
                kind = ChatAttachmentKind.FILE,
                name = "notes.md",
                containerPath = "/root/.pandora/attachments/session/notes.md",
                mimeType = "text/markdown",
                sizeBytes = storedFile.length(),
            )

            ChatAttachmentIndex(workspace).record("thread/unsafe", 3, "Review this", listOf(attachment))
            val restored = ChatAttachmentIndex(workspace).attachments("thread/unsafe", 2, "Review this", emptyList())

            assertEquals(listOf(attachment), restored?.attachments)
            assertTrue(ChatAttachmentIndex(workspace).attachments("thread/unsafe", 2, "Different", emptyList()) == null)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun restoresFileOnlyTurnsAfterAnAnchoredAttachmentTurn() {
        val workspace = Files.createTempDirectory("pandora-file-only-index").toFile()
        try {
            fun attachment(name: String): ChatAttachment {
                val storedFile = File(workspace, ".pandora/attachments/session/$name")
                checkNotNull(storedFile.parentFile).mkdirs()
                storedFile.writeText(name)
                return ChatAttachment(
                    kind = ChatAttachmentKind.FILE,
                    name = name,
                    containerPath = "/root/.pandora/attachments/session/$name",
                    mimeType = "text/plain",
                    sizeBytes = storedFile.length(),
                )
            }

            val index = ChatAttachmentIndex(workspace)
            val anchor = attachment("anchor.txt")
            val firstFileOnly = attachment("first.txt")
            val secondFileOnly = attachment("second.txt")
            index.record("thread", 7, "With text", listOf(anchor))
            index.record("thread", 8, "", listOf(firstFileOnly))
            index.record("thread", 9, "", listOf(secondFileOnly))

            val anchored = index.attachments("thread", 5, "With text", emptyList())
            val first = index.attachments(
                "thread", 6, "", emptyList(),
                afterIndexedOrdinal = anchored?.ordinal,
                usedOrdinals = setOfNotNull(anchored?.ordinal),
            )
            val second = index.attachments(
                "thread", 7, "", emptyList(),
                afterIndexedOrdinal = first?.ordinal,
                usedOrdinals = setOfNotNull(anchored?.ordinal, first?.ordinal),
            )

            assertEquals(7, anchored?.ordinal)
            assertEquals(listOf(firstFileOnly), first?.attachments)
            assertEquals(listOf(secondFileOnly), second?.attachments)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun restoresFileOnlyTurnByStableCodexTurnId() {
        val workspace = Files.createTempDirectory("pandora-turn-id-index").toFile()
        try {
            val storedFile = File(workspace, ".pandora/attachments/session/notes.md")
            checkNotNull(storedFile.parentFile).mkdirs()
            storedFile.writeText("hello")
            val attachment = ChatAttachment(
                kind = ChatAttachmentKind.FILE,
                name = "notes.md",
                containerPath = "/root/.pandora/attachments/session/notes.md",
            )
            val index = ChatAttachmentIndex(workspace)
            index.record("thread", 4, "", listOf(attachment))
            index.associateTurn("thread", 4, "turn-stable")

            val restored = index.attachments(
                "thread", 99, "", emptyList(), turnId = "turn-stable",
            )

            assertEquals(4, restored?.ordinal)
            assertEquals(listOf(attachment.copy(sizeBytes = storedFile.length())), restored?.attachments)
        } finally {
            workspace.deleteRecursively()
        }
    }
}
