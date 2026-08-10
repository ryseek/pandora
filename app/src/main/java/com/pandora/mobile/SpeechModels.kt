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
)

object SpeechModels {
    const val DEFAULT_STT_ID = "zipformer-en-20m-mobile"
    const val DEFAULT_TTS_ID = "piper-amy-en-int8"

    val all = listOf(
        SpeechModel(
            id = DEFAULT_STT_ID,
            kind = SpeechModelKind.SPEECH_TO_TEXT,
            name = "English · Compact",
            language = "English (US)",
            description = "Fast streaming dictation with the smallest memory footprint.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 107_569_151,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile.tar.bz2",
            archiveRoot = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile",
            requiredFiles = listOf(
                "encoder-epoch-99-avg-1.int8.onnx",
                "decoder-epoch-99-avg-1.onnx",
                "joiner-epoch-99-avg-1.int8.onnx",
                "tokens.txt",
            ),
            engine = "zipformer-en-20m-mobile",
        ),
        SpeechModel(
            id = "zipformer-en-balanced",
            kind = SpeechModelKind.SPEECH_TO_TEXT,
            name = "English · Balanced",
            language = "English (US)",
            description = "Higher accuracy, but slower to load and heavier on memory.",
            tier = SpeechModelTier.BALANCED,
            downloadBytes = 310_414_022,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2",
            archiveRoot = "sherpa-onnx-streaming-zipformer-en-2023-06-26",
            requiredFiles = listOf(
                "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
                "decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
                "joiner-epoch-99-avg-1-chunk-16-left-128.onnx",
                "tokens.txt",
            ),
            engine = "zipformer-en-balanced",
        ),
        SpeechModel(
            id = "zipformer-de-kroko",
            kind = SpeechModelKind.SPEECH_TO_TEXT,
            name = "German · Compact",
            language = "German",
            description = "Small streaming German model with a light download footprint.",
            tier = SpeechModelTier.COMPACT,
            downloadBytes = 57_565_698,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06.tar.bz2",
            archiveRoot = "sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06",
            requiredFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"),
            engine = "zipformer-de-kroko",
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
