package com.pandora.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderSettingsTest {
    @Test
    fun parsesDistinctModelIdentifiersFromLinesAndCommas() {
        assertEquals(
            listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b"),
            ModelProviderSettings.parseModelIds(
                "openai/gpt-oss-120b, openai/gpt-oss-20b\nopenai/gpt-oss-120b",
            ),
        )
    }

    @Test
    fun acceptsOnlyCompleteHttpProviderUrls() {
        assertTrue(ModelProviderSettings.isSupportedUrl("https://openrouter.ai/api/v1"))
        assertTrue(ModelProviderSettings.isSupportedUrl("http://192.168.1.5:11434/v1"))
        assertFalse(ModelProviderSettings.isSupportedUrl("openrouter.ai/api/v1"))
        assertFalse(ModelProviderSettings.isSupportedUrl("file:///tmp/models"))
    }
}
