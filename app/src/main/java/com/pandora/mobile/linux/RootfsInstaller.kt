package com.pandora.mobile.linux

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.WeakHashMap
import java.util.zip.GZIPInputStream

class RootfsInstaller(private val context: Context) {
    val rootfs = File(context.filesDir, "alpine-rootfs")
    val workspace = File(context.filesDir, "linux-workspace")
    val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
    val tempDir = File(context.cacheDir, "proot-tmp")
    private val marker = File(rootfs, ".pandora-rootfs-v1")
    private val defaultPackagesMarker = File(rootfs, ".pandora-default-packages-v2")
    private val workspaceMarker = File(context.filesDir, ".pandora-workspace-v1")

    fun installIfNeeded(onStatus: (String) -> Unit) {
        synchronized(INSTALL_LOCK) {
            tempDir.mkdirs()
            ensurePersistentWorkspace(onStatus)
            ensureAgentKnowledge()
            ensureCodexDefaults()
            check(proot.exists()) { "Bundled PRoot runtime is missing" }

            if (!marker.exists()) {
                onStatus("Installing Alpine Linux…")
                installFreshRootfs()
            }
            installDefaultPackages(onStatus)
            installZmxIfNeeded(onStatus)
            installCodexIfNeeded(onStatus)
            onStatus("Starting container…")
        }
    }

    /** Replaces the Linux system image while leaving the separately-mounted /root intact. */
    fun repair(onStatus: (String) -> Unit) {
        synchronized(INSTALL_LOCK) {
            tempDir.mkdirs()
            ensurePersistentWorkspace(onStatus)
            ensureAgentKnowledge()
            ensureCodexDefaults()
            check(proot.exists()) { "Bundled PRoot runtime is missing" }
            onStatus("Repairing Alpine Linux…")
            installFreshRootfs()
            installDefaultPackages(onStatus)
            installZmxIfNeeded(onStatus)
            installCodexIfNeeded(onStatus)
            onStatus("Repair complete")
        }
    }

    private fun installFreshRootfs() {
        if (rootfs.exists()) rootfs.deleteRecursively()
        rootfs.mkdirs()
        context.assets.open("alpine-minirootfs.tar").use { input ->
            extractTar(input, rootfs)
        }

        File(rootfs, "root").mkdirs()
        File(rootfs, "tmp").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
        File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        marker.writeText("aarch64\n")
    }

    /** Applies the same baseline after first install and every system repair. */
    private fun installDefaultPackages(onStatus: (String) -> Unit) {
        if (defaultPackagesMarker.exists()) return
        onStatus("Installing SSL and Linux utilities…")

        val command = mutableListOf(
            proot.absolutePath,
            "-0",
            "--link2symlink",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${workspace.absolutePath}:/root",
            "-w", "/root",
            "/sbin/apk", "add", "--no-cache",
        ).apply { addAll(DEFAULT_PACKAGES) }

        val process = startContainerProcess(command, mergeError = true)

        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            defaultPackagesMarker.writeText(DEFAULT_PACKAGES.joinToString("\n", postfix = "\n"))
        } else {
            // An offline first launch should still reach a shell. With no marker,
            // the exact same manifest is retried automatically on the next launch.
            Log.w(TAG, "Default package install deferred (exit $exitCode): $output")
            onStatus("Utilities pending; starting offline…")
        }
    }

    /** Installs Codex into persistent /root storage once Node/npm are available. */
    private fun installCodexIfNeeded(onStatus: (String) -> Unit) {
        val codex = File(workspace, ".local/bin/codex")
        if (codex.exists()) {
            ensureLocalBinOnPath()
            return
        }
        if (!File(rootfs, "usr/bin/npm").exists()) return

        onStatus("Installing Codex…")
        ensureLocalBinOnPath()
        val command = containerCommand(
            "/usr/bin/npm",
            "install",
            "--global",
            "--prefix", "/root/.local",
            "@openai/codex",
        )
        val (exitCode, output) = runContainerCommand(command)
        if (exitCode != 0 || !codex.exists()) {
            // Codex remains absent rather than blocking offline startup. Its
            // existence check retries the install on the next container launch.
            Log.w(TAG, "Codex install deferred (exit $exitCode): $output")
            onStatus("Codex pending; starting offline…")
        }
    }

    /** Installs a pinned static ARM64 zmx so every Pandora terminal can detach and reattach. */
    private fun installZmxIfNeeded(onStatus: (String) -> Unit) {
        val zmx = File(workspace, ".local/bin/zmx")
        if (zmx.exists()) return
        onStatus("Installing persistent terminal sessions…")
        val archive = File(tempDir, "zmx-$ZMX_VERSION.tar.gz")
        val extraction = File(tempDir, "zmx-$ZMX_VERSION-extract")
        runCatching {
            URL(ZMX_URL).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }.getInputStream().buffered().use { input ->
                archive.outputStream().buffered().use(input::copyTo)
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(digest == ZMX_SHA256) { "zmx checksum did not match" }
            if (extraction.exists()) extraction.deleteRecursively()
            extraction.mkdirs()
            GZIPInputStream(archive.inputStream().buffered()).use { input -> extractTar(input, extraction) }
            val extracted = File(extraction, "zmx")
            check(extracted.isFile) { "zmx archive did not contain the binary" }
            zmx.parentFile?.mkdirs()
            extracted.copyTo(zmx, overwrite = true)
            check(zmx.setExecutable(true, false)) { "Could not make zmx executable" }
        }.onFailure { error ->
            Log.w(TAG, "zmx install deferred", error)
            onStatus("Persistent terminals pending; starting normally…")
        }
        archive.delete()
        if (extraction.exists()) extraction.deleteRecursively()
    }

    private fun ensureLocalBinOnPath() {
        val profile = File(workspace, ".profile")
        val export = "export PATH=\"\$HOME/.local/bin:\$PATH\""
        val current = profile.takeIf { it.exists() }?.readText().orEmpty()
        if (!current.lineSequence().any { it.trim() == export }) {
            profile.parentFile?.mkdirs()
            profile.appendText((if (current.isNotEmpty() && !current.endsWith('\n')) "\n" else "") + export + "\n")
        }
    }

    /** Installs global device-specific Codex context without replacing user edits. */
    private fun ensureAgentKnowledge() {
        val instructions = File(workspace, ".codex/AGENTS.md")
        if (instructions.exists()) return

        instructions.parentFile?.mkdirs()
        context.assets.open("pandora/AGENTS.md").use { input ->
            instructions.outputStream().use(input::copyTo)
        }
    }

    /**
     * Android/PRoot has neither a desktop keyring nor the user namespaces Bubblewrap needs.
     * Keep these machine-level Codex settings above any TOML tables so they apply globally.
     */
    private fun ensureCodexDefaults() {
        val config = File(workspace, ".codex/config.toml")
        var current = config.takeIf { it.exists() }?.readText().orEmpty()
        current = setTopLevelTomlSetting(
            content = current,
            key = "cli_auth_credentials_store",
            value = "\"file\"",
            replaceExisting = false,
        )
        current = setTopLevelTomlSetting(
            content = current,
            key = "sandbox_mode",
            value = "\"danger-full-access\"",
            replaceExisting = true,
        )
        config.parentFile?.mkdirs()
        if (!config.exists() || config.readText() != current) config.writeText(current)
    }

    private fun setTopLevelTomlSetting(
        content: String,
        key: String,
        value: String,
        replaceExisting: Boolean,
    ): String {
        val lines = content.trimEnd().let { trimmed ->
            if (trimmed.isEmpty()) mutableListOf() else trimmed.lines().toMutableList()
        }
        val firstTable = lines.indexOfFirst { it.trimStart().startsWith('[') }.let {
            if (it == -1) lines.size else it
        }
        val settingPattern = Regex("^${Regex.escape(key)}\\s*=")
        val existing = lines.take(firstTable).indexOfFirst { settingPattern.containsMatchIn(it.trimStart()) }
        val setting = "$key = $value"
        if (existing >= 0) {
            if (replaceExisting) lines[existing] = setting
        } else {
            lines.add(firstTable, setting)
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    internal fun containerCommand(vararg executableAndArgs: String): List<String> = listOf(
        proot.absolutePath,
        "-0",
        "--link2symlink",
        "-r", rootfs.absolutePath,
        "-b", "/dev",
        "-b", "/proc",
        "-b", "/sys",
        "-b", "${workspace.absolutePath}:/root",
        "-w", "/root",
        *executableAndArgs,
    )

    internal fun startContainerProcess(command: List<String>, mergeError: Boolean): Process {
        val processIdentity = UUID.randomUUID().toString()
        val process = ProcessBuilder(command).redirectErrorStream(mergeError).apply {
            environment().apply {
                put("PROOT_TMP_DIR", tempDir.absolutePath)
                put("LD_LIBRARY_PATH", nativeLibDir.absolutePath)
                put("PROOT_LOADER", File(nativeLibDir, "libproot-loader.so").absolutePath)
                put("PROOT_LOADER_32", File(nativeLibDir, "libproot-loader32.so").absolutePath)
                put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                put("HOME", "/root")
                put("ZMX_DIR", "/root/.local/state/zmx/sessions")
                put("TERM", "dumb")
                put(PROCESS_IDENTITY_ENV, processIdentity)
            }
        }.start()
        synchronized(PROCESS_IDENTITIES) { PROCESS_IDENTITIES[process] = processIdentity }
        return process
    }

    /** PRoot's launcher can exit without terminating its Node/Codex descendants. */
    internal fun terminateProcessTree(process: Process) {
        val identity = synchronized(PROCESS_IDENTITIES) { PROCESS_IDENTITIES.remove(process) }
        if (identity != null) {
            ownProcessDirectories()
                .filter { directory ->
                    runCatching {
                        File(directory, "environ")
                            .readBytes()
                            .toString(Charsets.UTF_8)
                            .split('\u0000')
                            .contains("$PROCESS_IDENTITY_ENV=$identity")
                    }.getOrDefault(false)
                }
                .mapNotNull { it.name.toIntOrNull() }
                .sortedDescending()
                .forEach(::killPid)
        }
        if (process.isAlive) runCatching { process.destroyForcibly() }
    }

    /** Clears app-server trees orphaned by an Android process restart or an older build. */
    internal fun terminateStaleCodexAppServers() {
        val ownPid = android.os.Process.myPid()
        ownProcessDirectories()
            .mapNotNull { directory -> directory.name.toIntOrNull()?.let { it to directory } }
            .filter { (pid, _) -> pid != ownPid }
            .filter { (_, directory) ->
                val command = runCatching {
                    File(directory, "cmdline").readText().replace('\u0000', ' ')
                }.getOrDefault("")
                command.contains(proot.absolutePath) &&
                    command.contains("/root/.local/bin/codex") &&
                    command.contains("app-server")
            }
            .map { it.first }
            .toList()
            .forEach(::terminatePidTree)
    }

    private fun terminatePidTree(rootPid: Int) {
        val processIds = linkedSetOf<Int>()
        fun collect(pid: Int) {
            if (!processIds.add(pid)) return
            val children = runCatching {
                File("/proc/$pid/task/$pid/children")
                    .readText()
                    .trim()
                    .split(Regex("\\s+"))
                    .mapNotNull(String::toIntOrNull)
            }.getOrDefault(emptyList())
            children.forEach(::collect)
        }
        collect(rootPid)
        processIds.toList().asReversed().forEach(::killPid)
    }

    private fun ownProcessDirectories(): Sequence<File> {
        val ownUid = android.os.Process.myUid()
        return File("/proc").listFiles().orEmpty().asSequence().filter { directory ->
            directory.name.toIntOrNull() != null &&
                runCatching { Os.stat(directory.absolutePath).st_uid == ownUid }.getOrDefault(false)
        }
    }

    private fun killPid(pid: Int) {
        runCatching { Os.kill(pid, android.system.OsConstants.SIGKILL) }
    }

    private fun runContainerCommand(command: List<String>): Pair<Int, String> {
        val process = startContainerProcess(command, mergeError = true)
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return process.waitFor() to output
    }

    /** One-time migration from the original monolithic rootfs into durable user storage. */
    private fun ensurePersistentWorkspace(onStatus: (String) -> Unit) {
        if (workspaceMarker.exists()) {
            workspace.mkdirs()
            return
        }

        onStatus("Preserving workspace…")
        workspace.mkdirs()
        val oldHome = File(rootfs, "root")
        if (oldHome.isDirectory) {
            oldHome.listFiles().orEmpty().forEach { source ->
                val destination = File(workspace, source.name)
                if (!destination.exists()) {
                    check(source.copyRecursively(destination, overwrite = false)) {
                        "Could not preserve ${source.name}"
                    }
                }
            }
        }
        workspaceMarker.writeText("/root\n")
    }

    private fun extractTar(input: InputStream, target: File) {
        val header = ByteArray(512)
        while (readFully(input, header) == 512 && header.any { it != 0.toByte() }) {
            val name = string(header, 0, 100)
            val prefix = string(header, 345, 155)
            val archivedPath = if (prefix.isEmpty()) name else "$prefix/$name"
            val relative = archivedPath.removePrefix("./").trimStart('/')
            val safeTarget = File(target, relative).canonicalFile
            val targetPath = target.canonicalPath
            require(safeTarget.path == targetPath || safeTarget.path.startsWith(targetPath + File.separator)) {
                "Unsafe path in Linux image: $archivedPath"
            }

            val mode = string(header, 100, 8).trim().toIntOrNull(8) ?: 0
            val size = string(header, 124, 12).trim().toLongOrNull(8) ?: 0L
            val type = header[156].toInt().toChar()
            val linkName = string(header, 157, 100)

            when (type) {
                '5' -> safeTarget.mkdirs()
                '2' -> {
                    safeTarget.parentFile?.mkdirs()
                    runCatching {
                        if (safeTarget.exists()) safeTarget.delete()
                        Os.symlink(linkName, safeTarget.absolutePath)
                    }
                }
                '1' -> {
                    safeTarget.parentFile?.mkdirs()
                    val source = File(target, linkName)
                    if (source.exists()) source.copyTo(safeTarget, overwrite = true)
                }
                '0', '\u0000' -> {
                    safeTarget.parentFile?.mkdirs()
                    safeTarget.outputStream().use { output ->
                        copyExactly(input, output::write, size)
                    }
                    safeTarget.setReadable(true, false)
                    safeTarget.setWritable(mode and 0b010_010_010 != 0, true)
                    if (mode and 0b001_001_001 != 0) safeTarget.setExecutable(true, false)
                    skipPadding(input, size)
                    continue
                }
            }

            if (size > 0) {
                skipFully(input, ((size + 511) / 512) * 512)
            }
        }
    }

    private fun copyExactly(input: InputStream, write: (ByteArray, Int, Int) -> Unit, count: Long) {
        var remaining = count
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(read >= 0) { "Unexpected end of Linux image" }
            write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val remainder = size % 512
        if (remainder != 0L) skipFully(input, 512 - remainder)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) return
            remaining -= read
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return offset
            offset += read
        }
        return offset
    }

    private fun string(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && buffer[end] != 0.toByte()) end++
        return String(buffer, offset, end - offset, StandardCharsets.UTF_8)
    }

    private companion object {
        const val TAG = "RootfsInstaller"
        const val PROCESS_IDENTITY_ENV = "PANDORA_PROCESS_ID"
        val INSTALL_LOCK = Any()
        val PROCESS_IDENTITIES = WeakHashMap<Process, String>()
        val DEFAULT_PACKAGES = listOf(
            "ca-certificates",
            "ssl_client",
            "lscpu",
            "util-linux",
            "nodejs",
            "npm",
            "git",
            "ripgrep",
        )
        const val ZMX_VERSION = "0.7.0"
        const val ZMX_URL = "https://zmx.sh/a/zmx-0.7.0-linux-aarch64.tar.gz"
        const val ZMX_SHA256 = "77599f66124694fae80bbb1d2fa0eafdb8c648b427a048cad90513ecf6136fc9"
    }
}
