package com.pandora.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DictationState {
    data object Idle : DictationState
    data object Loading : DictationState
    data class Listening(val text: String) : DictationState
    data class Finished(val text: String) : DictationState
    data class Failed(val detail: String) : DictationState
}

sealed interface SpeechPlaybackState {
    data object Idle : SpeechPlaybackState
    data object Loading : SpeechPlaybackState
    data object Speaking : SpeechPlaybackState
    data class Failed(val detail: String) : SpeechPlaybackState
}

class OnDeviceSpeech(context: Context, private val models: SpeechModelManager) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dictationGeneration = AtomicLong()
    private val playbackGeneration = AtomicLong()
    private val dictationLock = Any()
    private val playbackLock = Any()
    private val _dictation = MutableStateFlow<DictationState>(DictationState.Idle)
    private val _playback = MutableStateFlow<SpeechPlaybackState>(SpeechPlaybackState.Idle)
    val dictation: StateFlow<DictationState> = _dictation.asStateFlow()
    val playback: StateFlow<SpeechPlaybackState> = _playback.asStateFlow()

    private val activeDictation = AtomicLong(0)
    @Volatile private var stopPlayback = false
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null

    @SuppressLint("MissingPermission")
    fun startDictation() {
        if (_dictation.value is DictationState.Loading || _dictation.value is DictationState.Listening) return
        stopSpeaking()
        val model = selectedModel(SpeechModelKind.SPEECH_TO_TEXT)
        if (model == null || !models.isInstalled(model)) {
            _dictation.value = DictationState.Failed("Download the selected dictation model in Settings first.")
            return
        }
        val operation = dictationGeneration.incrementAndGet()
        _dictation.value = DictationState.Loading
        scope.launch {
            var recognizer: OnlineRecognizer? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var localRecorder: AudioRecord? = null
            try {
                recognizer = createRecognizer(model)
                if (operation != dictationGeneration.get()) return@launch
                val sampleRate = 16_000
                val minBytes = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minBytes > 0) { "This device could not open a 16 kHz microphone stream" }
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBytes * 2,
                )
                localRecorder = audioRecord
                check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "The microphone could not be initialized" }
                stream = recognizer.createStream()
                synchronized(dictationLock) {
                    if (operation != dictationGeneration.get()) return@launch
                    recorder = audioRecord
                    activeDictation.set(operation)
                    audioRecord.startRecording()
                    _dictation.value = DictationState.Listening("")
                }
                val samples16 = ShortArray((sampleRate * 0.1).toInt())
                var committed = ""
                while (activeDictation.get() == operation && operation == dictationGeneration.get()) {
                    val count = audioRecord.read(samples16, 0, samples16.size)
                    if (count <= 0) continue
                    val samples = FloatArray(count) { samples16[it] / 32768f }
                    stream.acceptWaveform(samples, sampleRate)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    val partial = recognizer.getResult(stream).text.trim()
                    synchronized(dictationLock) {
                        if (
                            operation == dictationGeneration.get() &&
                            activeDictation.get() == operation
                        ) {
                            _dictation.value = DictationState.Listening(joinText(committed, partial))
                        }
                    }
                    if (recognizer.isEndpoint(stream)) {
                        committed = joinText(committed, partial)
                        recognizer.reset(stream)
                    }
                }
                stream.inputFinished()
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val finalText = joinText(committed, recognizer.getResult(stream).text.trim())
                synchronized(dictationLock) {
                    if (operation == dictationGeneration.get()) {
                        _dictation.value = DictationState.Finished(finalText)
                    }
                }
            } catch (error: Throwable) {
                synchronized(dictationLock) {
                    if (operation == dictationGeneration.get()) {
                        _dictation.value = DictationState.Failed(error.message ?: "Dictation failed")
                    }
                }
            } finally {
                activeDictation.compareAndSet(operation, 0)
                runCatching { localRecorder?.stop() }
                localRecorder?.release()
                synchronized(dictationLock) {
                    if (recorder === localRecorder) recorder = null
                }
                stream?.release()
                recognizer?.release()
            }
        }
    }

    fun stopDictation() {
        synchronized(dictationLock) {
            if (_dictation.value is DictationState.Loading) {
                dictationGeneration.incrementAndGet()
                _dictation.value = DictationState.Idle
            }
            activeDictation.set(0)
            runCatching { recorder?.stop() }
        }
    }

    fun cancelDictation() {
        synchronized(dictationLock) {
            dictationGeneration.incrementAndGet()
            activeDictation.set(0)
            runCatching { recorder?.stop() }
            _dictation.value = DictationState.Idle
        }
    }

    fun clearDictation() {
        if (activeDictation.get() == 0L) _dictation.value = DictationState.Idle
    }

    fun microphonePermissionDenied() {
        _dictation.value = DictationState.Failed(
            "Microphone access is off. Allow it in Android Settings to use dictation.",
        )
    }

    fun speak(text: String) {
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)")) { match -> match.groupValues[1] }
            .replace(Regex("[*_#>`~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isBlank()) return
        cancelDictation()
        stopSpeaking()
        val model = selectedModel(SpeechModelKind.TEXT_TO_SPEECH)
        if (model == null || !models.isInstalled(model)) {
            _playback.value = SpeechPlaybackState.Failed("Download the selected voice in Settings first.")
            return
        }
        val operation = playbackGeneration.incrementAndGet()
        stopPlayback = false
        _playback.value = SpeechPlaybackState.Loading
        scope.launch {
            var tts: OfflineTts? = null
            var localTrack: AudioTrack? = null
            var focusRequest: AudioFocusRequest? = null
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                tts = createTts(model)
                if (operation != playbackGeneration.get()) return@launch
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(speechAudioAttributes())
                    .setOnAudioFocusChangeListener(
                        { change ->
                            if (change < 0) stopSpeaking()
                        },
                        Handler(Looper.getMainLooper()),
                    )
                    .build()
                check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "Another app is using audio. Try again when it finishes."
                }
                val audioTrack = createAudioTrack(tts.sampleRate())
                localTrack = audioTrack
                synchronized(playbackLock) {
                    if (operation != playbackGeneration.get()) return@launch
                    track = audioTrack
                    audioTrack.play()
                    _playback.value = SpeechPlaybackState.Speaking
                }
                for (chunk in speechChunks(cleanText)) {
                    if (stopPlayback || operation != playbackGeneration.get()) break
                    tts.generateWithCallback(chunk, sid = 0, speed = 1f) { samples ->
                        if (stopPlayback || operation != playbackGeneration.get()) return@generateWithCallback 0
                        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1
                    }
                }
                synchronized(playbackLock) {
                    if (!stopPlayback && operation == playbackGeneration.get()) {
                        audioTrack.stop()
                        _playback.value = SpeechPlaybackState.Idle
                    }
                }
            } catch (error: Throwable) {
                synchronized(playbackLock) {
                    if (operation == playbackGeneration.get()) {
                        _playback.value = SpeechPlaybackState.Failed(error.message ?: "Speech playback failed")
                    }
                }
            } finally {
                runCatching { localTrack?.stop() }
                localTrack?.release()
                synchronized(playbackLock) {
                    if (track === localTrack) track = null
                }
                focusRequest?.let(audioManager::abandonAudioFocusRequest)
                tts?.release()
            }
        }
    }

    fun stopSpeaking() {
        synchronized(playbackLock) {
            stopPlayback = true
            playbackGeneration.incrementAndGet()
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            _playback.value = SpeechPlaybackState.Idle
        }
    }

    private fun selectedModel(kind: SpeechModelKind): SpeechModel? {
        val id = when (kind) {
            SpeechModelKind.SPEECH_TO_TEXT -> AppSettings.speechToTextModel(appContext)
            SpeechModelKind.TEXT_TO_SPEECH -> AppSettings.textToSpeechModel(appContext)
        }
        return SpeechModels.find(id)?.takeIf { it.kind == kind }
    }

    private fun createRecognizer(model: SpeechModel): OnlineRecognizer {
        val dir = models.modelDirectory(model)
        val files = model.requiredFiles
        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(dir, files[0]).absolutePath,
                decoder = File(dir, files[1]).absolutePath,
                joiner = File(dir, files[2]).absolutePath,
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = if (model.id == SpeechModels.DEFAULT_STT_ID) "zipformer" else "zipformer2",
        )
        return OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = modelConfig,
                endpointConfig = getEndpointConfig(),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            ),
        )
    }

    private fun createTts(model: SpeechModel): OfflineTts {
        val dir = models.modelDirectory(model)
        val modelFile = model.requiredFiles.first { it.endsWith(".onnx") }
        return OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, modelFile).absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = File(dir, "espeak-ng-data").absolutePath,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        )
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val attributes = speechAudioAttributes()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        return AudioTrack(
            attributes,
            format,
            minimum.coerceAtLeast(sampleRate / 2),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
    }

    private fun speechAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .build()

    private fun speechChunks(text: String, maxLength: Int = 1_500): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) chunks += current.toString().trim()
            current = StringBuilder()
        }
        sentences.forEach { sentence ->
            if (sentence.length > maxLength) {
                flush()
                sentence.chunked(maxLength).forEach(chunks::add)
            } else if (current.length + sentence.length + 1 > maxLength) {
                flush()
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        flush()
        return chunks
    }

    private fun joinText(first: String, second: String): String = when {
        first.isBlank() -> second
        second.isBlank() -> first
        first.endsWith(" ") -> first + second
        else -> "$first $second"
    }
}
