package com.pandora.mobile

enum class SpeechModelKind { SPEECH_TO_TEXT, TEXT_TO_SPEECH }

enum class SpeechModelTier { COMPACT, BALANCED }

data class SpeechModel(
    val id: String,
    val kind: SpeechModelKind,
    val name: String,
    val language: String,
    val description: String,
    val tier: SpeechModelTier,
    val downloadBytes: Long,
    val downloadUrl: String,
    val archiveRoot: String,
    val requiredFiles: List<String>,
    val engine: String,
    val retainOnlyRequiredFiles: Boolean = false,
)

object SpeechModels {
    const val DEFAULT_STT_ID = "zipformer-en-kroko"
    const val WHISPER_TINY_ID = "whisper-tiny-en-int8"
    const val DEFAULT_TTS_ID = "piper-amy-en-int8"

    val all = listOf(
        SpeechModel(
            id = DEFAULT_STT_ID,
            kind = SpeechModelKind.SPEECH_TO_TEXT,
            name = "Kroko · Live",
            language = "English (US)",
            description = "Immediate partial text while you speak. Best for quick dictation.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 57_267_600,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06.tar.bz2",
            archiveRoot = "sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06",
            requiredFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"),
            engine = "zipformer-en-kroko",
        ),
        SpeechModel(
            id = WHISPER_TINY_ID,
            kind = SpeechModelKind.SPEECH_TO_TEXT,
            name = "Whisper Tiny · Accurate",
            language = "English (US)",
            description = "More accurate final text after recording stops. Can also refine Kroko.",
            tier = SpeechModelTier.BALANCED,
            downloadBytes = 118_071_777,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-whisper-tiny.en.tar.bz2",
            archiveRoot = "sherpa-onnx-whisper-tiny.en",
            requiredFiles = listOf(
                "tiny.en-encoder.int8.onnx",
                "tiny.en-decoder.int8.onnx",
                "tiny.en-tokens.txt",
            ),
            engine = "whisper-tiny-en-int8",
            retainOnlyRequiredFiles = true,
        ),
        SpeechModel(
            id = DEFAULT_TTS_ID,
            kind = SpeechModelKind.TEXT_TO_SPEECH,
            name = "Amy · Compact",
            language = "English (US)",
            description = "Clear female voice, int8 optimized for older phones.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 21_099_246,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-en_US-amy-low-int8.tar.bz2",
            archiveRoot = "vits-piper-en_US-amy-low-int8",
            requiredFiles = listOf("en_US-amy-low.onnx", "tokens.txt", "espeak-ng-data/phontab"),
            engine = "vits-piper-en_US-amy-low-int8",
        ),
        SpeechModel(
            id = "piper-lessac-en-int8",
            kind = SpeechModelKind.TEXT_TO_SPEECH,
            name = "Lessac · Compact",
            language = "English (US)",
            description = "Alternative male voice with the same low resource use.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 21_070_568,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-en_US-lessac-low-int8.tar.bz2",
            archiveRoot = "vits-piper-en_US-lessac-low-int8",
            requiredFiles = listOf("en_US-lessac-low.onnx", "tokens.txt", "espeak-ng-data/phontab"),
            engine = "vits-piper-en_US-lessac-low-int8",
        ),
        SpeechModel(
            id = "piper-thorsten-de-int8",
            kind = SpeechModelKind.TEXT_TO_SPEECH,
            name = "Thorsten · Compact",
            language = "German",
            description = "Compact German voice optimized with int8 weights.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 21_292_232,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-de_DE-thorsten-low-int8.tar.bz2",
            archiveRoot = "vits-piper-de_DE-thorsten-low-int8",
            requiredFiles = listOf("de_DE-thorsten-low.onnx", "tokens.txt", "espeak-ng-data/phontab"),
            engine = "vits-piper-de_DE-thorsten-low-int8",
        ),
    )

    fun find(id: String): SpeechModel? = all.firstOrNull { it.id == id }
}
