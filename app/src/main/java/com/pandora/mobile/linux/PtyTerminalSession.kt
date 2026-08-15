package com.pandora.mobile.linux

import android.content.Context
import com.openminis.app.sandbox.PtyBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.File

/** A real PTY connected to an interactive shell inside Pandora's PRoot workspace. */
class PtyTerminalSession(
    context: Context,
    private val persistentSessionName: String? = null,
) {
    enum class State { PREPARING, RUNNING, STOPPED, FAILED }

    private val appContext = context.applicationContext
    private val installer = RootfsInstaller(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.PREPARING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _output = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()

    @Volatile private var masterFd = -1
    @Volatile private var childPid = 0
    @Volatile private var cols = 80
    @Volatile private var rows = 24
    private var readerJob: Job? = null

    fun start(onStatus: (String) -> Unit = {}) {
        if (_state.value == State.RUNNING) return
        _state.value = State.PREPARING
        scope.launch {
            try {
                installer.installIfNeeded(onStatus)
                val command = buildCommand()
                val environment = buildEnvironment()
                val outPid = IntArray(1)
                val fd = PtyBridge.forkExec(
                    installer.proot.absolutePath,
                    command.toTypedArray(),
                    environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                    installer.nativeLibDir.absolutePath,
                    cols,
                    rows,
                    outPid,
                )
                check(fd >= 0) { "Could not create terminal (errno ${-fd})" }
                masterFd = fd
                childPid = outPid[0]
                _state.value = State.RUNNING
                readerJob = scope.launch { readLoop(fd) }
                scope.launch {
                    val status = PtyBridge.waitFor(outPid[0])
                    _output.emit("\r\n[Process exited: $status]\r\n".toByteArray())
                    if (_state.value != State.FAILED) _state.value = State.STOPPED
                }
            } catch (error: Throwable) {
                _output.emit("Pandora terminal failed: ${error.message}\r\n".toByteArray())
                _state.value = State.FAILED
            }
        }
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty() || masterFd < 0) return
        scope.launch {
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(2048, bytes.size - offset)
                val written = PtyBridge.writeBytes(masterFd, bytes, offset, length)
                if (written <= 0) return@launch
                offset += written
                if (offset < bytes.size) yield()
            }
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols < 1 || newRows < 1) return
        cols = newCols
        rows = newRows
        val fd = masterFd
        if (fd >= 0) PtyBridge.setWindowSize(fd, newCols, newRows)
    }

    fun stop() {
        readerJob?.cancel()
        readerJob = null
        val fd = masterFd
        val pid = childPid
        masterFd = -1
        childPid = 0
        if (fd >= 0) PtyBridge.closeFd(fd)
        if (pid > 0) PtyBridge.sendSignal(pid, 15)
        _state.value = State.STOPPED
        scope.cancel()
    }

    private suspend fun readLoop(fd: Int) {
        val buffer = ByteArray(8192)
        while (_state.value == State.RUNNING) {
            val count = PtyBridge.readBytes(fd, buffer, 0, buffer.size)
            if (count <= 0) return
            _output.emit(buffer.copyOf(count))
        }
    }

    private fun buildCommand(): List<String> {
        val shell = terminalShellCommand(
            persistentSessionName = persistentSessionName,
            zmxAvailable = File(installer.workspace, ".local/bin/zmx").isFile,
        )
        return listOf(
        installer.proot.absolutePath,
        "-0",
        "--link2symlink",
        "-r", installer.rootfs.absolutePath,
        "-b", "/dev",
        "-b", "/proc",
        "-b", "/sys",
        "-b", "${installer.workspace.absolutePath}:/root",
        "-w", "/root",
        *shell.toTypedArray(),
        )
    }

    private fun buildEnvironment(): LinkedHashMap<String, String> = linkedMapOf(
        "PROOT_TMP_DIR" to installer.tempDir.absolutePath,
        "LD_LIBRARY_PATH" to installer.nativeLibDir.absolutePath,
        "PROOT_LOADER" to File(installer.nativeLibDir, "libproot-loader.so").absolutePath,
        "PROOT_LOADER_32" to File(installer.nativeLibDir, "libproot-loader32.so").absolutePath,
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "HOME" to "/root",
        "ZMX_DIR" to "/root/.local/state/zmx/sessions",
        "TERM" to "xterm-256color",
        "COLORTERM" to "truecolor",
        "LANG" to "C.UTF-8",
    )
}

internal fun terminalShellCommand(
    persistentSessionName: String?,
    zmxAvailable: Boolean,
): List<String> = if (persistentSessionName != null && zmxAvailable) {
    listOf("/root/.local/bin/zmx", "attach", persistentSessionName, "/bin/bash", "-l", "-i")
} else {
    listOf("/bin/bash", "-l", "-i")
}
