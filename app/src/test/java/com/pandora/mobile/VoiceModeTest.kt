package com.pandora.mobile

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeTest {
    @Test
    fun `voice over speed snaps and stays in supported range`() {
        assertEquals(0.5f, normalizeVoiceOverSpeed(0.1f), 0.001f)
        assertEquals(1.25f, normalizeVoiceOverSpeed(1.27f), 0.001f)
        assertEquals(2f, normalizeVoiceOverSpeed(3f), 0.001f)
    }

    @Test
    fun `ducking audio focus does not cancel voice playback`() {
        assertFalse(shouldStopSpeechForAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertTrue(shouldStopSpeechForAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertTrue(shouldStopSpeechForAudioFocus(AudioManager.AUDIOFOCUS_LOSS))
    }

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
