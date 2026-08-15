package com.pandora.mobile.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PtyTerminalSessionTest {
    @Test
    fun startsInteractiveBashForTabCompletion() {
        val command = terminalShellCommand(persistentSessionName = null, zmxAvailable = true)

        assertEquals(listOf("/bin/bash", "-l", "-i"), command)
        assertFalse(command.contains("/bin/sh"))
    }

    @Test
    fun startsBashInsidePersistentSessions() {
        assertEquals(
            listOf("/root/.local/bin/zmx", "attach", "pandora-test", "/bin/bash", "-l", "-i"),
            terminalShellCommand("pandora-test", zmxAvailable = true),
        )
    }
}
