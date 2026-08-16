package com.pandora.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeTest {
    @Test
    fun `short stop phrases are handled locally`() {
        assertTrue(isVoiceStopCommand("Stop!"))
        assertTrue(isVoiceStopCommand("Never mind."))
        assertTrue(isVoiceStopCommand("cancel that"))
    }

    @Test
    fun `requests containing stop are still sent to Codex`() {
        assertFalse(isVoiceStopCommand("Stop the server and show me the logs"))
        assertFalse(isVoiceStopCommand("Can you cancel the download?"))
    }
}
