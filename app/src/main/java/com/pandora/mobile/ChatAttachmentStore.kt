package com.pandora.mobile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.pandora.mobile.linux.ChatAttachment
import com.pandora.mobile.linux.ChatAttachmentKind
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ChatAttachmentStore {
    private const val MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024

    suspend fun import(context: Context, uri: Uri, sessionId: String): ChatAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "attachment"
        var reportedSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { index ->
                    displayName = cursor.getString(index).orEmpty().ifBlank { displayName }
                }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { index ->
                    reportedSize = cursor.getLong(index)
                }
            }
        }
        check(reportedSize == null || reportedSize!! <= MAX_ATTACHMENT_BYTES) {
            "Attachments must be 50 MB or smaller"
        }

        val safeSessionId = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.filesDir, "linux-workspace/.pandora/attachments/$safeSessionId")
        check(directory.mkdirs() || directory.isDirectory) { "Could not prepare attachment storage" }
        val safeName = sanitizeFileName(displayName)
        val target = File(directory, "${UUID.randomUUID()}-$safeName")
        var copied = 0L
        try {
            val input = checkNotNull(resolver.openInputStream(uri)) { "Could not open the selected item" }
            input.buffered().use { source ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        check(copied <= MAX_ATTACHMENT_BYTES) { "Attachments must be 50 MB or smaller" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }

        val mimeType = resolver.getType(uri).orEmpty()
        ChatAttachment(
            kind = if (mimeType.startsWith("image/", ignoreCase = true)) {
                ChatAttachmentKind.IMAGE
            } else {
                ChatAttachmentKind.FILE
            },
            name = displayName,
            containerPath = "/root/.pandora/attachments/$safeSessionId/${target.name}",
            mimeType = mimeType,
            sizeBytes = copied,
        )
    }

    fun deletePending(context: Context, attachment: ChatAttachment) {
        val relative = attachment.containerPath.removePrefix("/root/")
        if (relative == attachment.containerPath) return
        val workspace = File(context.filesDir, "linux-workspace").canonicalFile
        val target = File(workspace, relative).canonicalFile
        if (target.path.startsWith(workspace.path + File.separator)) target.delete()
    }

    fun resolve(context: Context, attachment: ChatAttachment): File? {
        val relative = attachment.containerPath.removePrefix("/root/")
        if (relative == attachment.containerPath) return null
        val workspace = File(context.filesDir, "linux-workspace").canonicalFile
        val target = File(workspace, relative).canonicalFile
        return target.takeIf { it.path.startsWith(workspace.path + File.separator) && it.isFile }
    }

    fun decodePreview(context: Context, attachment: ChatAttachment, maxDimension: Int): Bitmap? {
        val file = resolve(context, attachment) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    fun readTextPreview(context: Context, attachment: ChatAttachment, maxCharacters: Int = 32_000): String? {
        if (!attachment.isTextPreviewable()) return null
        val file = resolve(context, attachment) ?: return null
        return file.bufferedReader().use { reader ->
            val buffer = CharArray(maxCharacters)
            val count = reader.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count) + if (reader.read() >= 0) "\n…" else ""
        }
    }

    fun openWithAndroid(context: Context, attachment: ChatAttachment): Boolean {
        val file = resolve(context, attachment) ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType.ifBlank { "application/octet-stream" })
            clipData = ClipData.newRawUri(attachment.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    suspend fun saveCopy(context: Context, attachment: ChatAttachment, destination: Uri) = withContext(Dispatchers.IO) {
        val source = checkNotNull(resolve(context, attachment)) { "The attachment is no longer available" }
        val output = checkNotNull(context.contentResolver.openOutputStream(destination, "w")) {
            "Could not open the selected destination"
        }
        source.inputStream().buffered().use { input ->
            output.buffered().use(input::copyTo)
        }
    }

    internal fun sanitizeFileName(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        val sanitized = leaf
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
            .replace(Regex("[^\\p{L}\\p{N}._ ()+@-]"), "_")
            .trim()
            .take(96)
        return sanitized.takeUnless { it.isBlank() || it.all { char -> char == '_' } } ?: "attachment"
    }
}

internal fun ChatAttachment.isTextPreviewable(): Boolean {
    if (mimeType.startsWith("text/", ignoreCase = true)) return true
    return name.substringAfterLast('.', "").lowercase() in setOf(
        "c", "cc", "cpp", "css", "csv", "h", "hpp", "html", "java", "js", "json", "kt", "kts",
        "log", "md", "py", "rb", "rs", "sh", "toml", "ts", "tsx", "txt", "xml", "yaml", "yml",
    )
}
