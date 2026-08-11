package com.pandora.mobile

import java.util.concurrent.ArrayBlockingQueue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationDiagnosticsTest {
    @Test
    fun realTimeFactorComparesInferenceTimeWithAudioDuration() {
        assertEquals(
            0.5f,
            dictationRealTimeFactor(
                inferenceNanos = 500_000_000,
                processedSamples = 16_000,
                sampleRate = 16_000,
            ),
        )
    }

    @Test
    fun realTimeFactorNeedsARealMeasurement() {
        assertNull(dictationRealTimeFactor(0, 16_000, 16_000))
        assertNull(dictationRealTimeFactor(500_000_000, 0, 16_000))
        assertNull(dictationRealTimeFactor(500_000_000, 16_000, 0))
    }

    @Test
    fun sampleCountConvertsToDroppedMilliseconds() {
        assertEquals(250, audioSamplesToMillis(4_000, 16_000))
        assertEquals(0, audioSamplesToMillis(0, 16_000))
    }

    @Test
    fun fullAudioQueueDropsOldestChunkAndKeepsCurrentSpeech() {
        val queue = ArrayBlockingQueue<ShortArray>(2)
        queue.add(shortArrayOf(1, 1))
        queue.add(shortArrayOf(2, 2, 2))

        assertEquals(2, enqueueAudioChunk(queue, shortArrayOf(3, 3, 3, 3)))
        assertArrayEquals(shortArrayOf(2, 2, 2), queue.remove())
        assertArrayEquals(shortArrayOf(3, 3, 3, 3), queue.remove())
    }
}
