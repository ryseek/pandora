package com.pandora.mobile.linux

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WorkspaceBackup(private val filesDir: File, private val cacheDir: File) {
    data class Result(val files: Int, val bytes: Long)

    private val workspace = File(filesDir, "linux-workspace")

    fun write(output: OutputStream): Result {
        check(workspace.isDirectory) { "The Linux workspace has not been created yet" }
        var fileCount = 0
        var byteCount = 0L
        val symlinks = mutableListOf<Pair<String, String>>()
        val executables = mutableListOf<String>()

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putText(MANIFEST, "format=1\nroot=linux-workspace\n")
            Files.walk(workspace.toPath()).use { paths ->
                paths.forEach { path ->
                    val relative = workspace.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                    val archivePath = if (relative.isEmpty()) "workspace/" else "workspace/$relative"
                    when {
                        Files.isSymbolicLink(path) -> {
                            symlinks += relative to Files.readSymbolicLink(path).toString()
                        }
                        Files.isDirectory(path) -> {
                            val name = archivePath.trimEnd('/') + "/"
                            zip.putNextEntry(ZipEntry(name).apply { time = path.toFile().lastModified() })
                            zip.closeEntry()
                        }
                        Files.isRegularFile(path) -> {
                            zip.putNextEntry(ZipEntry(archivePath).apply { time = path.toFile().lastModified() })
                            Files.newInputStream(path).use { input ->
                                val copied = input.copyTo(zip)
                                byteCount += copied
                            }
                            zip.closeEntry()
                            fileCount++
                            if (Files.isExecutable(path)) executables += relative
                        }
                    }
                }
            }
            zip.putText(SYMLINKS, symlinks.joinToString("") { encode(it.first) + "\t" + encode(it.second) + "\n" })
            zip.putText(EXECUTABLES, executables.joinToString("") { encode(it) + "\n" })
        }
        return Result(fileCount, byteCount)
    }

    fun restore(input: InputStream): Result {
        val staging = File(cacheDir, "workspace-restore-${UUID.randomUUID()}")
        val restoredWorkspace = File(staging, "workspace")
        var manifest: String? = null
        var symlinkData = ""
        var executableData = ""
        var fileCount = 0
        var byteCount = 0L

        try {
            restoredWorkspace.mkdirs()
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check(++fileCount <= MAX_ENTRIES) { "Backup contains too many entries" }
                    when (entry.name) {
                        MANIFEST -> manifest = zip.readLimited(MAX_METADATA_BYTES)
                        SYMLINKS -> symlinkData = zip.readLimited(MAX_METADATA_BYTES)
                        EXECUTABLES -> executableData = zip.readLimited(MAX_METADATA_BYTES)
                        else -> {
                            check(entry.name.startsWith("workspace/")) { "Unknown backup entry: ${entry.name}" }
                            val relative = entry.name.removePrefix("workspace/").trimEnd('/')
                            if (relative.isNotEmpty()) {
                                val target = safeTarget(restoredWorkspace, relative)
                                if (entry.isDirectory) {
                                    check(target.mkdirs() || target.isDirectory) { "Could not create $relative" }
                                } else {
                                    target.parentFile?.mkdirs()
                                    target.outputStream().buffered().use { output ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        while (true) {
                                            val count = zip.read(buffer)
                                            if (count < 0) break
                                            byteCount += count
                                            check(byteCount <= MAX_UNCOMPRESSED_BYTES) { "Backup is too large" }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                    if (entry.time > 0) target.setLastModified(entry.time)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }

            check(manifest?.lineSequence()?.any { it == "format=1" } == true) {
                "This is not a supported Pandora backup"
            }
            restoreSymlinks(restoredWorkspace, symlinkData)
            executableData.lineSequence().filter { it.isNotBlank() }.forEach { encoded ->
                safeTarget(restoredWorkspace, decode(encoded)).setExecutable(true, false)
            }
            replaceWorkspace(restoredWorkspace)
            return Result(fileCount, byteCount)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun restoreSymlinks(root: File, data: String) {
        data.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('\t', limit = 2)
            check(parts.size == 2) { "Invalid symlink metadata" }
            val link = safeTarget(root, decode(parts[0]))
            check(!link.exists() && !Files.isSymbolicLink(link.toPath())) { "Duplicate backup path" }
            link.parentFile?.mkdirs()
            Files.createSymbolicLink(link.toPath(), File(decode(parts[1])).toPath())
        }
    }

    private fun replaceWorkspace(restored: File) {
        val previous = File(filesDir, "linux-workspace-restore-previous")
        if (previous.exists()) previous.deleteRecursively()
        val hadWorkspace = workspace.exists()
        if (hadWorkspace) check(workspace.renameTo(previous)) { "Could not stage the current workspace" }
        try {
            check(restored.renameTo(workspace)) { "Could not activate the restored workspace" }
            previous.deleteRecursively()
        } catch (error: Throwable) {
            if (!workspace.exists() && hadWorkspace) previous.renameTo(workspace)
            throw error
        }
    }

    private fun safeTarget(root: File, relative: String): File {
        check(relative.isNotBlank() && !relative.contains('\\')) { "Unsafe backup path" }
        val target = File(root, relative).canonicalFile
        val rootPath = root.canonicalPath
        check(target.path.startsWith(rootPath + File.separator)) { "Unsafe backup path" }
        return target
    }

    private fun ZipOutputStream.putText(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun ZipInputStream.readLimited(limit: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(output.size() + count <= limit) { "Backup metadata is too large" }
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    companion object {
        private const val MANIFEST = ".pandora-backup/manifest.properties"
        private const val SYMLINKS = ".pandora-backup/symlinks.tsv"
        private const val EXECUTABLES = ".pandora-backup/executables.txt"
        private const val MAX_ENTRIES = 200_000
        private const val MAX_METADATA_BYTES = 8 * 1024 * 1024
        private const val MAX_UNCOMPRESSED_BYTES = 8L * 1024 * 1024 * 1024
    }
}
