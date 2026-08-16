package com.pandora.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelsTest {
    @Test
    fun catalogIdsAreUniqueAndDefaultsExist() {
        assertEquals(SpeechModels.all.size, SpeechModels.all.map { it.id }.distinct().size)
        assertEquals(SpeechModelKind.SPEECH_TO_TEXT, SpeechModels.find(SpeechModels.DEFAULT_STT_ID)?.kind)
        assertEquals(SpeechModelKind.TEXT_TO_SPEECH, SpeechModels.find(SpeechModels.DEFAULT_TTS_ID)?.kind)
    }

    @Test
    fun everyModelHasACompleteSecureManifest() {
        SpeechModels.all.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/"))
            assertTrue(model.downloadBytes > 0)
            assertTrue(model.archiveRoot.isNotBlank())
            assertTrue(model.requiredFiles.isNotEmpty())
            assertTrue(model.requiredFiles.none { it.startsWith("/") || ".." in it })
        }
    }

    @Test
    fun compactDefaultsRespectPhoneDownloadBudget() {
        val stt = SpeechModels.find(SpeechModels.DEFAULT_STT_ID)
        val tts = SpeechModels.find(SpeechModels.DEFAULT_TTS_ID)
        assertNotNull(stt)
        assertNotNull(tts)
        assertEquals(SpeechModelTier.COMPACT, stt?.tier)
        assertEquals(SpeechModelTier.COMPACT, tts?.tier)
        assertTrue(stt!!.downloadBytes < 60L * 1024 * 1024)
        assertTrue(tts!!.downloadBytes < 25L * 1024 * 1024)
    }

    @Test
    fun dictationCatalogContainsOnlyTheNewLiveAndWhisperModels() {
        val dictationModels = SpeechModels.all.filter { it.kind == SpeechModelKind.SPEECH_TO_TEXT }
        assertEquals(setOf(SpeechModels.DEFAULT_STT_ID, SpeechModels.WHISPER_TINY_ID), dictationModels.map { it.id }.toSet())
        assertEquals("zipformer-en-kroko", SpeechModels.find(SpeechModels.DEFAULT_STT_ID)?.engine)
        assertTrue(SpeechModels.find(SpeechModels.WHISPER_TINY_ID)?.retainOnlyRequiredFiles == true)
    }

    @Test
    fun speechChunksGrowAfterACompactLeadingPhrase() {
        val chunks = lowLatencySpeechChunks(
            "One two three four five six seven eight nine ten eleven twelve thirteen fourteen " +
                "fifteen sixteen seventeen eighteen nineteen twenty.",
            targetLengths = intArrayOf(20, 40, 80),
        )

        assertEquals(
            listOf(
                "One two three four",
                "five six seven eight nine ten eleven",
                "twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty.",
            ),
            chunks,
        )
    }

    @Test
    fun speechChunksPreserveAllWordsWithoutSplittingLongTokens() {
        val text = "Okay supercalifragilisticexpialidocious now"
        assertEquals(text, lowLatencySpeechChunks(text, intArrayOf(8)).joinToString(" "))
    }
}
