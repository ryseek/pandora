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
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
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
    data object Transcribing : DictationState
    data class Finished(val text: String) : DictationState
    data class Failed(val detail: String) : DictationState
}

data class DictationDiagnostics(
    val processor: AppSettings.DictationProcessor = AppSettings.DictationProcessor.CPU,
    val realTimeFactor: Float? = null,
    val droppedAudioMillis: Long = 0,
    val bufferedAudioMillis: Long = 0,
    val processedAudioMillis: Long = 0,
    val active: Boolean = false,
)

internal fun dictationRealTimeFactor(
    inferenceNanos: Long,
    processedSamples: Long,
    sampleRate: Int,
): Float? {
    if (inferenceNanos <= 0 || processedSamples <= 0 || sampleRate <= 0) return null
    val audioNanos = processedSamples.toDouble() * 1_000_000_000.0 / sampleRate
    return (inferenceNanos / audioNanos).toFloat()
}

internal fun audioSamplesToMillis(samples: Long, sampleRate: Int): Long =
    if (samples <= 0 || sampleRate <= 0) 0 else samples * 1_000L / sampleRate

internal fun enqueueAudioChunk(
    queue: ArrayBlockingQueue<ShortArray>,
    chunk: ShortArray,
): Int {
    if (queue.offer(chunk)) return 0
    val discardedSamples = queue.poll()?.size ?: 0
    return if (queue.offer(chunk)) discardedSamples else discardedSamples + chunk.size
}

sealed interface SpeechPlaybackState {
    data object Idle : SpeechPlaybackState
    data object Loading : SpeechPlaybackState
    data object Speaking : SpeechPlaybackState
    data class Failed(val detail: String) : SpeechPlaybackState
}

class OnDeviceSpeech(context: Context, private val models: SpeechModelManager) {
    private data class OnlineCache(
        val modelId: String,
        val processor: AppSettings.DictationProcessor,
        val recognizer: OnlineRecognizer,
    )

    private data class OfflineCache(
        val modelId: String,
        val processor: AppSettings.DictationProcessor,
        val recognizer: OfflineRecognizer,
    )

    private data class TtsCache(val modelId: String, val tts: OfflineTts)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dictationGeneration = AtomicLong()
    private val playbackGeneration = AtomicLong()
    private val dictationLock = Any()
    private val playbackLock = Any()
    private val dictationOperationLock = Any()
    private val playbackOperationLock = Any()
    private val modelCacheLock = Any()
    private val _dictation = MutableStateFlow<DictationState>(DictationState.Idle)
    private val _diagnostics = MutableStateFlow(DictationDiagnostics())
    private val _playback = MutableStateFlow<SpeechPlaybackState>(SpeechPlaybackState.Idle)
    val dictation: StateFlow<DictationState> = _dictation.asStateFlow()
    val diagnostics: StateFlow<DictationDiagnostics> = _diagnostics.asStateFlow()
    val playback: StateFlow<SpeechPlaybackState> = _playback.asStateFlow()

    private val activeDictation = AtomicLong(0)
    private val dictationModelInUse = AtomicBoolean(false)
    private val playbackModelInUse = AtomicBoolean(false)
    @Volatile private var stopPlayback = false
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var onlineCache: OnlineCache? = null
    private var offlineCache: OfflineCache? = null
    private var ttsCache: TtsCache? = null

    fun prewarmSelectedModels() {
        scope.launch {
            val processor = AppSettings.dictationProcessor(appContext)
            selectedModel(SpeechModelKind.SPEECH_TO_TEXT)
                ?.takeIf(models::isInstalled)
                ?.let { model ->
                    runCatching {
                        if (model.engine == SpeechModels.WHISPER_TINY_ID) {
                            cachedOfflineRecognizer(model, processor)
                        } else {
                            cachedOnlineRecognizer(model, processor)
                        }
                    }
                }
            SpeechModels.find(SpeechModels.WHISPER_TINY_ID)
                ?.takeIf {
                    AppSettings.refineDictationWithWhisper(appContext) && models.isInstalled(it)
                }
                ?.let { runCatching { cachedOfflineRecognizer(it, processor) } }
            selectedModel(SpeechModelKind.TEXT_TO_SPEECH)
                ?.takeIf(models::isInstalled)
                ?.let { model ->
                    runCatching {
                        synchronized(playbackOperationLock) {
                            playbackModelInUse.set(true)
                            try {
                                cachedTts(model).generate("Ready.", sid = 0, speed = TTS_SPEED)
                            } finally {
                                playbackModelInUse.set(false)
                            }
                        }
                    }
                }
        }
    }

    fun releaseWarmModels() {
        if (
            activeDictation.get() != 0L ||
            dictationModelInUse.get() ||
            playbackModelInUse.get() ||
            _dictation.value is DictationState.Loading ||
            _dictation.value is DictationState.Transcribing ||
            _playback.value is SpeechPlaybackState.Loading ||
            _playback.value is SpeechPlaybackState.Speaking
        ) return
        synchronized(modelCacheLock) {
            onlineCache?.recognizer?.release()
            offlineCache?.recognizer?.release()
            ttsCache?.tts?.release()
            onlineCache = null
            offlineCache = null
            ttsCache = null
        }
    }

    @SuppressLint("MissingPermission")
    fun startDictation() {
        if (
            _dictation.value is DictationState.Loading ||
            _dictation.value is DictationState.Listening ||
            _dictation.value is DictationState.Transcribing
        ) return
        stopSpeaking()
        val model = selectedModel(SpeechModelKind.SPEECH_TO_TEXT)
        if (model == null || !models.isInstalled(model)) {
            _dictation.value = DictationState.Failed("Download the selected dictation model in Settings first.")
            return
        }
        val operation = dictationGeneration.incrementAndGet()
        val processor = AppSettings.dictationProcessor(appContext)
        _dictation.value = DictationState.Loading
        _diagnostics.value = DictationDiagnostics(processor = processor, active = true)
        if (model.engine == SpeechModels.WHISPER_TINY_ID) {
            startOfflineDictation(model, operation, processor)
            return
        }
        scope.launch {
            synchronized(dictationOperationLock) {
                dictationModelInUse.set(true)
            var recognizer: OnlineRecognizer? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var localRecorder: AudioRecord? = null
            var captureThread: Thread? = null
            val audioQueue = ArrayBlockingQueue<ShortArray>(AUDIO_QUEUE_CAPACITY)
            val captureFinished = AtomicBoolean(false)
            val captureFailure = AtomicReference<Throwable?>(null)
            val droppedSamples = AtomicLong()
            val processedSamples = AtomicLong()
            val inferenceNanos = AtomicLong()
            val whisper = SpeechModels.find(SpeechModels.WHISPER_TINY_ID)?.takeIf {
                AppSettings.refineDictationWithWhisper(appContext) && models.isInstalled(it)
            }
            val recordedAudio = if (whisper != null) mutableListOf<ShortArray>() else null
            var recordedAudioSamples = 0L
            var lastDiagnosticsNanos = 0L
            fun publishDiagnostics(active: Boolean, force: Boolean = false) {
                val now = System.nanoTime()
                if (!force && now - lastDiagnosticsNanos < DIAGNOSTICS_INTERVAL_NANOS) return
                lastDiagnosticsNanos = now
                _diagnostics.value = DictationDiagnostics(
                    processor = processor,
                    realTimeFactor = dictationRealTimeFactor(
                        inferenceNanos = inferenceNanos.get(),
                        processedSamples = processedSamples.get(),
                        sampleRate = SAMPLE_RATE,
                    ),
                    droppedAudioMillis = audioSamplesToMillis(droppedSamples.get(), SAMPLE_RATE),
                    bufferedAudioMillis = audioSamplesToMillis(
                        audioQueue.sumOf { it.size.toLong() },
                        SAMPLE_RATE,
                    ),
                    processedAudioMillis = audioSamplesToMillis(processedSamples.get(), SAMPLE_RATE),
                    active = active,
                )
            }
            try {
                recognizer = try {
                    cachedOnlineRecognizer(model, processor)
                } catch (error: Throwable) {
                    if (processor == AppSettings.DictationProcessor.GPU) {
                        throw IllegalStateException(
                            "GPU acceleration is not supported by this device or dictation model. Choose CPU in Settings.",
                            error,
                        )
                    }
                    throw error
                }
                if (operation != dictationGeneration.get()) return@launch
                val minBytes = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minBytes > 0) { "This device could not open a 16 kHz microphone stream" }
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes * 4, AUDIO_CHUNK_SAMPLES * 8),
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
                captureThread = thread(name = "pandora-dictation-capture", isDaemon = true) {
                    try {
                        while (
                            activeDictation.get() == operation &&
                            operation == dictationGeneration.get()
                        ) {
                            val buffer = ShortArray(AUDIO_CHUNK_SAMPLES)
                            val count = audioRecord.read(
                                buffer,
                                0,
                                buffer.size,
                                AudioRecord.READ_BLOCKING,
                            )
                            if (count > 0) {
                                val chunk = if (count == buffer.size) buffer else buffer.copyOf(count)
                                droppedSamples.addAndGet(enqueueAudioChunk(audioQueue, chunk).toLong())
                            } else if (
                                count < 0 &&
                                activeDictation.get() == operation &&
                                operation == dictationGeneration.get()
                            ) {
                                error("Microphone read failed ($count)")
                            }
                        }
                    } catch (error: Throwable) {
                        captureFailure.set(error)
                        activeDictation.compareAndSet(operation, 0)
                    } finally {
                        captureFinished.set(true)
                    }
                }
                var committed = ""
                while (
                    operation == dictationGeneration.get() &&
                    (!captureFinished.get() || audioQueue.isNotEmpty())
                ) {
                    val samples16 = audioQueue.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    recordedAudio?.let { chunks ->
                        val remaining = MAX_DICTATION_SAMPLES - recordedAudioSamples
                        if (remaining > 0) {
                            val kept = if (samples16.size <= remaining) {
                                samples16.copyOf()
                            } else {
                                samples16.copyOf(remaining.toInt())
                            }
                            chunks += kept
                            recordedAudioSamples += kept.size
                        }
                    }
                    val inferenceStarted = System.nanoTime()
                    val samples = FloatArray(samples16.size) { samples16[it] / 32768f }
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    val partial = recognizer.getResult(stream).text.trim()
                    inferenceNanos.addAndGet(System.nanoTime() - inferenceStarted)
                    processedSamples.addAndGet(samples16.size.toLong())
                    publishDiagnostics(active = true)
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
                captureFailure.get()?.let { throw it }
                stream.inputFinished()
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                var finalText = joinText(committed, recognizer.getResult(stream).text.trim())
                if (whisper != null && recordedAudio?.isNotEmpty() == true) {
                    stream.release()
                    stream = null
                    if (operation == dictationGeneration.get()) {
                        _dictation.value = DictationState.Transcribing
                        finalText = runCatching {
                            transcribeWithWhisper(whisper, recordedAudio, processor)
                        }.getOrDefault(finalText)
                    }
                }
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
                captureThread?.join(500)
                publishDiagnostics(active = false, force = true)
                localRecorder?.release()
                synchronized(dictationLock) {
                    if (recorder === localRecorder) recorder = null
                }
                stream?.release()
                    dictationModelInUse.set(false)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startOfflineDictation(
        model: SpeechModel,
        operation: Long,
        processor: AppSettings.DictationProcessor,
    ) {
        scope.launch {
            synchronized(dictationOperationLock) {
                dictationModelInUse.set(true)
            var localRecorder: AudioRecord? = null
            var captureThread: Thread? = null
            val audioQueue = ArrayBlockingQueue<ShortArray>(AUDIO_QUEUE_CAPACITY)
            val captureFinished = AtomicBoolean(false)
            val captureFailure = AtomicReference<Throwable?>(null)
            val droppedSamples = AtomicLong()
            val recordedAudio = mutableListOf<ShortArray>()
            try {
                val audioRecord = createAudioRecord()
                localRecorder = audioRecord
                synchronized(dictationLock) {
                    if (operation != dictationGeneration.get()) return@launch
                    recorder = audioRecord
                    activeDictation.set(operation)
                    audioRecord.startRecording()
                    _dictation.value = DictationState.Listening("")
                }
                captureThread = startCaptureThread(
                    audioRecord = audioRecord,
                    operation = operation,
                    audioQueue = audioQueue,
                    captureFinished = captureFinished,
                    captureFailure = captureFailure,
                    droppedSamples = droppedSamples,
                )
                var recordedSamples = 0L
                while (
                    operation == dictationGeneration.get() &&
                    (!captureFinished.get() || audioQueue.isNotEmpty())
                ) {
                    val chunk = audioQueue.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    val remaining = MAX_DICTATION_SAMPLES - recordedSamples
                    if (remaining > 0) {
                        val kept = if (chunk.size <= remaining) chunk else chunk.copyOf(remaining.toInt())
                        recordedAudio += kept
                        recordedSamples += kept.size
                    }
                    _diagnostics.value = DictationDiagnostics(
                        processor = processor,
                        droppedAudioMillis = audioSamplesToMillis(droppedSamples.get(), SAMPLE_RATE),
                        bufferedAudioMillis = audioSamplesToMillis(
                            audioQueue.sumOf { it.size.toLong() },
                            SAMPLE_RATE,
                        ),
                        processedAudioMillis = audioSamplesToMillis(recordedSamples, SAMPLE_RATE),
                        active = true,
                    )
                    if (recordedSamples >= MAX_DICTATION_SAMPLES) {
                        activeDictation.compareAndSet(operation, 0)
                        runCatching { audioRecord.stop() }
                    }
                }
                captureFailure.get()?.let { throw it }
                if (operation != dictationGeneration.get()) return@launch
                runCatching { audioRecord.stop() }
                captureThread.join(500)
                audioRecord.release()
                localRecorder = null
                synchronized(dictationLock) { if (recorder === audioRecord) recorder = null }
                _dictation.value = DictationState.Transcribing
                val finalText = transcribeWithWhisper(model, recordedAudio, processor)
                if (operation == dictationGeneration.get()) {
                    _dictation.value = DictationState.Finished(finalText)
                }
            } catch (error: Throwable) {
                if (operation == dictationGeneration.get()) {
                    _dictation.value = DictationState.Failed(error.message ?: "Dictation failed")
                }
            } finally {
                activeDictation.compareAndSet(operation, 0)
                runCatching { localRecorder?.stop() }
                captureThread?.join(500)
                localRecorder?.release()
                synchronized(dictationLock) { if (recorder === localRecorder) recorder = null }
                _diagnostics.value = _diagnostics.value.copy(active = false)
                    dictationModelInUse.set(false)
                }
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
            synchronized(playbackOperationLock) {
                playbackModelInUse.set(true)
            var tts: OfflineTts? = null
            var localTrack: AudioTrack? = null
            var generationThread: Thread? = null
            var focusRequest: AudioFocusRequest? = null
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                tts = cachedTts(model)
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
                val audioQueue = ArrayBlockingQueue<FloatArray>(TTS_QUEUE_CAPACITY)
                val generationFinished = AtomicBoolean(false)
                val generationFailure = AtomicReference<Throwable?>(null)
                val activeTts = checkNotNull(tts)
                generationThread = thread(name = "pandora-tts-generation", isDaemon = true) {
                    try {
                        for (chunk in speechChunks(cleanText)) {
                            if (stopPlayback || operation != playbackGeneration.get()) break
                            val samples = activeTts.generate(chunk, sid = 0, speed = TTS_SPEED).samples
                            while (
                                !stopPlayback &&
                                operation == playbackGeneration.get() &&
                                !audioQueue.offer(samples, 50, TimeUnit.MILLISECONDS)
                            ) Unit
                        }
                    } catch (error: Throwable) {
                        generationFailure.set(error)
                    } finally {
                        generationFinished.set(true)
                    }
                }
                while (
                    !stopPlayback &&
                    operation == playbackGeneration.get() &&
                    (!generationFinished.get() || audioQueue.isNotEmpty())
                ) {
                    val samples = audioQueue.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    val written = audioTrack.write(
                        samples,
                        0,
                        samples.size,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    check(written >= 0) { "Audio playback failed ($written)" }
                }
                generationThread.join()
                generationFailure.get()?.let { throw it }
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
                generationThread?.join()
                runCatching { localTrack?.stop() }
                localTrack?.release()
                synchronized(playbackLock) {
                    if (track === localTrack) track = null
                }
                focusRequest?.let(audioManager::abandonAudioFocusRequest)
                    playbackModelInUse.set(false)
                }
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

    private fun createAudioRecord(): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "This device could not open a 16 kHz microphone stream" }
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBytes * 4, AUDIO_CHUNK_SAMPLES * 8),
        ).also {
            check(it.state == AudioRecord.STATE_INITIALIZED) { "The microphone could not be initialized" }
        }
    }

    private fun startCaptureThread(
        audioRecord: AudioRecord,
        operation: Long,
        audioQueue: ArrayBlockingQueue<ShortArray>,
        captureFinished: AtomicBoolean,
        captureFailure: AtomicReference<Throwable?>,
        droppedSamples: AtomicLong,
    ): Thread = thread(name = "pandora-dictation-capture", isDaemon = true) {
        try {
            while (activeDictation.get() == operation && operation == dictationGeneration.get()) {
                val buffer = ShortArray(AUDIO_CHUNK_SAMPLES)
                val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    val chunk = if (count == buffer.size) buffer else buffer.copyOf(count)
                    droppedSamples.addAndGet(enqueueAudioChunk(audioQueue, chunk).toLong())
                } else if (
                    count < 0 &&
                    activeDictation.get() == operation &&
                    operation == dictationGeneration.get()
                ) {
                    error("Microphone read failed ($count)")
                }
            }
        } catch (error: Throwable) {
            captureFailure.set(error)
            activeDictation.compareAndSet(operation, 0)
        } finally {
            captureFinished.set(true)
        }
    }

    private fun transcribeWithWhisper(
        model: SpeechModel,
        audio: List<ShortArray>,
        processor: AppSettings.DictationProcessor,
    ): String {
        if (audio.isEmpty()) return ""
        var recognizer: OfflineRecognizer? = null
        var stream: com.k2fsa.sherpa.onnx.OfflineStream? = null
        val processedSamples = audio.sumOf { it.size.toLong() }
        try {
            recognizer = try {
                cachedOfflineRecognizer(model, processor)
            } catch (error: Throwable) {
                if (processor == AppSettings.DictationProcessor.GPU) {
                    throw IllegalStateException(
                        "GPU acceleration is not supported by this device or Whisper. Choose CPU in Settings.",
                        error,
                    )
                }
                throw error
            }
            stream = recognizer.createStream()
            audio.forEach { chunk ->
                stream.acceptWaveform(FloatArray(chunk.size) { chunk[it] / 32768f }, SAMPLE_RATE)
            }
            val started = System.nanoTime()
            recognizer.decode(stream)
            val elapsed = System.nanoTime() - started
            _diagnostics.value = DictationDiagnostics(
                processor = processor,
                realTimeFactor = dictationRealTimeFactor(elapsed, processedSamples, SAMPLE_RATE),
                processedAudioMillis = audioSamplesToMillis(processedSamples, SAMPLE_RATE),
                active = false,
            )
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream?.release()
        }
    }

    private fun cachedOnlineRecognizer(
        model: SpeechModel,
        processor: AppSettings.DictationProcessor,
    ): OnlineRecognizer = synchronized(modelCacheLock) {
        onlineCache?.takeIf { it.modelId == model.id && it.processor == processor }?.recognizer
            ?: createRecognizer(model, processor).also { recognizer ->
                onlineCache?.recognizer?.release()
                onlineCache = OnlineCache(model.id, processor, recognizer)
            }
    }

    private fun cachedOfflineRecognizer(
        model: SpeechModel,
        processor: AppSettings.DictationProcessor,
    ): OfflineRecognizer = synchronized(modelCacheLock) {
        offlineCache?.takeIf { it.modelId == model.id && it.processor == processor }?.recognizer
            ?: createOfflineRecognizer(model, processor).also { recognizer ->
                offlineCache?.recognizer?.release()
                offlineCache = OfflineCache(model.id, processor, recognizer)
            }
    }

    private fun cachedTts(model: SpeechModel): OfflineTts = synchronized(modelCacheLock) {
        ttsCache?.takeIf { it.modelId == model.id }?.tts
            ?: createTts(model).also { tts ->
                ttsCache?.tts?.release()
                ttsCache = TtsCache(model.id, tts)
            }
    }

    private fun createOfflineRecognizer(
        model: SpeechModel,
        processor: AppSettings.DictationProcessor,
    ): OfflineRecognizer {
        val dir = models.modelDirectory(model)
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(dir, "tiny.en-encoder.int8.onnx").absolutePath,
                        decoder = File(dir, "tiny.en-decoder.int8.onnx").absolutePath,
                        language = "en",
                        task = "transcribe",
                    ),
                    tokens = File(dir, "tiny.en-tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = when (processor) {
                        AppSettings.DictationProcessor.CPU -> "cpu"
                        AppSettings.DictationProcessor.GPU -> "nnapi"
                    },
                ),
                decodingMethod = "greedy_search",
            ),
        )
    }

    private fun createRecognizer(
        model: SpeechModel,
        processor: AppSettings.DictationProcessor,
    ): OnlineRecognizer {
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
            provider = when (processor) {
                AppSettings.DictationProcessor.CPU -> "cpu"
                AppSettings.DictationProcessor.GPU -> "nnapi"
            },
            modelType = "zipformer2",
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
                    numThreads = TTS_THREADS,
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

    private fun speechChunks(text: String, maxLength: Int = 220): List<String> {
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

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val AUDIO_CHUNK_SAMPLES = SAMPLE_RATE / 10
        const val AUDIO_QUEUE_CAPACITY = 20
        const val MAX_DICTATION_SAMPLES = SAMPLE_RATE * 120L
        const val TTS_SPEED = 1.25f
        const val TTS_QUEUE_CAPACITY = 3
        const val TTS_THREADS = 4
        const val DIAGNOSTICS_INTERVAL_NANOS = 250_000_000L
    }
}
