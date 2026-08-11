package com.pandora.mobile

import com.pandora.mobile.linux.ChatAttachment
import com.pandora.mobile.linux.ChatAttachmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentStoreTest {
    @Test
    fun sanitizesProviderFileNamesForLinuxStorage() {
        assertEquals("mockup_final_.png", ChatAttachmentStore.sanitizeFileName("folder/mockup:final?.png"))
        assertEquals("attachment", ChatAttachmentStore.sanitizeFileName("\u0000"))
    }

    @Test
    fun detectsTextFilesThatCanBePreviewed() {
        fun attachment(name: String, mimeType: String = "") = ChatAttachment(
            kind = ChatAttachmentKind.FILE,
            name = name,
            containerPath = "/root/.pandora/attachments/test/$name",
            mimeType = mimeType,
        )

        assertTrue(attachment("notes.bin", "text/plain").isTextPreviewable())
        assertTrue(attachment("README.md").isTextPreviewable())
        assertFalse(attachment("archive.zip", "application/zip").isTextPreviewable())
    }
}
