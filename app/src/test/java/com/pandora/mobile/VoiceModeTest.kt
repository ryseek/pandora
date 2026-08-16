package com.pandora.mobile

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeTest {
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
