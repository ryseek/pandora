package com.pandora.mobile

import android.content.Context
import android.os.StatFs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

sealed interface SpeechModelDownload {
    data object NotInstalled : SpeechModelDownload
    data object Installed : SpeechModelDownload
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : SpeechModelDownload
    data class Failed(val detail: String) : SpeechModelDownload
}

class SpeechModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "speech-models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installMutex = Mutex()
    private val _states = MutableStateFlow(readStates())
    val states: StateFlow<Map<String, SpeechModelDownload>> = _states.asStateFlow()

    fun state(modelId: String): SpeechModelDownload =
        _states.value[modelId] ?: SpeechModelDownload.NotInstalled

    fun modelDirectory(model: SpeechModel): File = File(root, model.id)

    fun isInstalled(model: SpeechModel): Boolean =
        model.requiredFiles.all { File(modelDirectory(model), it).isFile }

    @Synchronized
    fun download(model: SpeechModel) {
        if (state(model.id) is SpeechModelDownload.Downloading || isInstalled(model)) return
        update(model.id, SpeechModelDownload.Downloading(0, model.downloadBytes))
        scope.launch {
            runCatching {
                installMutex.withLock {
                    if (!isInstalled(model)) downloadAndInstall(model)
                }
            }
                .onSuccess { update(model.id, SpeechModelDownload.Installed) }
                .onFailure { error ->
                    update(model.id, SpeechModelDownload.Failed(error.message ?: "Download failed"))
                }
        }
    }

    fun delete(model: SpeechModel): Boolean {
        if (state(model.id) is SpeechModelDownload.Downloading) return false
        val removed = modelDirectory(model).deleteRecursively()
        update(model.id, SpeechModelDownload.NotInstalled)
        return removed
    }

    private fun readStates(): Map<String, SpeechModelDownload> =
        SpeechModels.all.associate { model ->
            model.id to if (isInstalled(model)) SpeechModelDownload.Installed else SpeechModelDownload.NotInstalled
        }

    private fun update(modelId: String, state: SpeechModelDownload) {
        _states.update { current -> current.toMutableMap().apply { put(modelId, state) } }
    }

    private fun downloadAndInstall(model: SpeechModel) {
        root.mkdirs()
        val available = StatFs(root.absolutePath).availableBytes
        val required = model.downloadBytes * 3
        check(available > required) {
            "Not enough free storage. Free at least ${formatModelBytes(required)} and try again."
        }

        val archive = File(root, ".${model.id}.download")
        val staging = File(root, ".${model.id}.staging")
        archive.delete()
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()
            check(connection.responseCode in 200..299) { "Server returned HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.downloadBytes
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var readTotal = 0L
                    var lastPublished = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        readTotal += count
                        if (readTotal - lastPublished >= 1024 * 1024) {
                            update(model.id, SpeechModelDownload.Downloading(readTotal, total))
                            lastPublished = readTotal
                        }
                    }
                }
            }
            connection.disconnect()
            extractArchive(archive, staging)
            val extractedRoot = File(staging, model.archiveRoot)
            check(extractedRoot.isDirectory) { "The downloaded model has an unexpected layout" }
            check(model.requiredFiles.all { File(extractedRoot, it).isFile }) {
                "The downloaded model is incomplete"
            }
            val destination = modelDirectory(model)
            destination.deleteRecursively()
            check(extractedRoot.renameTo(destination)) { "Could not finish installing the model" }
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private fun extractArchive(archive: File, destination: File) {
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())),
        ).use { tar ->
            var entry = tar.nextEntry
            val destinationPath = destination.canonicalPath + File.separator
            while (entry != null) {
                val output = File(destination, entry.name)
                check(output.canonicalPath.startsWith(destinationPath)) { "Unsafe path in model archive" }
                when {
                    entry.isDirectory -> output.mkdirs()
                    entry.isSymbolicLink || entry.isLink -> Unit
                    else -> {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { tar.copyTo(it) }
                    }
                }
                entry = tar.nextEntry
            }
        }
    }
}

fun formatModelBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}
