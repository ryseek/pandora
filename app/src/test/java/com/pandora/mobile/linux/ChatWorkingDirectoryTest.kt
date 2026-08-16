package com.pandora.mobile.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWorkingDirectoryTest {
    private val chatId = "1d4d0649-b3d4-4f83-863f-c786da0006cd"

    @Test
    fun createsWorkspaceUnderReservedChatsDirectory() {
        assertEquals("/root/chats/$chatId", generalChatWorkingDirectory(chatId))
    }

    @Test
    fun recognizesOnlyDirectUuidChatWorkspaces() {
        assertTrue(isGeneralChatWorkingDirectory("/root/chats/$chatId"))
        assertTrue(isGeneralChatWorkingDirectory("/root/chats/$chatId/"))
        assertFalse(isGeneralChatWorkingDirectory("/root/chats/not-a-uuid"))
        assertFalse(isGeneralChatWorkingDirectory("/root/chats/$chatId/nested"))
        assertFalse(isGeneralChatWorkingDirectory("/root/projects/$chatId"))
    }

    @Test
    fun recognizesTheWholeReservedChatWorkspaceTree() {
        assertTrue(isReservedChatWorkspacePath("/root/chats"))
        assertTrue(isReservedChatWorkspacePath("/root/chats/$chatId/result"))
        assertFalse(isReservedChatWorkspacePath("/root/chats-old/$chatId"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidChatIds() {
        generalChatWorkingDirectory("../project")
    }
}
