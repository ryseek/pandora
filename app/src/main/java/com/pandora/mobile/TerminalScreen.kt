package com.pandora.mobile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.terminal.canvas.TerminalInputView
import com.openminis.app.ui.terminal.canvas.TerminalNativeViewCompose
import com.openminis.app.ui.terminal.canvas.rememberTerminalInputController
import com.openminis.app.ui.terminal.emulator.TerminalEmulator
import com.pandora.mobile.linux.PtyTerminalSession
import com.pandora.mobile.linux.RootfsInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TerminalBackground = Color.Black
private val AccessoryBackground = Color(0xFF111310)
private val AccessoryActive = Color(0xFF6757DE)
private val AccessoryText = Color(0xFFE6E8E2)

@Composable
fun TerminalScreen(initialCommand: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val session = remember { PtyTerminalSession(context) }
    val emulator = remember { TerminalEmulator() }
    val terminalFontSize = remember { AppSettings.terminalFontSize(context) }
    val input = rememberTerminalInputController()
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparing Linux…") }
    var showRepair by remember { mutableStateOf(false) }
    var repairing by remember { mutableStateOf(false) }
    var repairError by remember { mutableStateOf<String?>(null) }
    var loginUrlOpened by remember { mutableStateOf(false) }
    val loginOutput = remember { StringBuilder() }
    val isCodexLogin = initialCommand?.startsWith("codex login") == true

    DisposableEffect(Unit) {
        LinuxSessionService.start(context)
        onDispose { LinuxSessionService.stop(context) }
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        emulator.scrollOffset = 0
        var outgoing = bytes
        if (ctrlActive && bytes.size == 1) {
            val char = (bytes[0].toInt() and 0xff).toChar().uppercaseChar()
            if (char in 'A'..'Z') outgoing = byteArrayOf((char - 'A' + 1).toByte())
            ctrlActive = false
        }
        if (altActive) {
            outgoing = byteArrayOf(0x1B) + outgoing
            altActive = false
        }
        session.send(outgoing)
    }

    LaunchedEffect(session) {
        emulator.onResponse = session::send
        launch {
            session.output.collect { bytes ->
                emulator.feed(bytes)
                if (isCodexLogin) {
                    loginOutput.append(bytes.toString(Charsets.UTF_8))
                    if (loginOutput.length > 16_384) loginOutput.delete(0, loginOutput.length - 16_384)
                    if (!loginUrlOpened) {
                        val url = LOGIN_URL.findAll(loginOutput)
                            .map { it.value.trimEnd('.', ',', ')', ']', '\u001B') }
                            .firstOrNull { it.contains("openai.com") || it.contains("chatgpt.com") }
                        if (url != null) {
                            loginUrlOpened = true
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
        }
        session.start { status = it }
    }

    LaunchedEffect(sessionState, initialCommand) {
        if (sessionState == PtyTerminalSession.State.RUNNING && initialCommand != null) {
            delay(350)
            session.send((initialCommand + "\r").toByteArray())
        }
    }

    DisposableEffect(session) {
        onDispose { session.stop() }
    }

    Box(Modifier.fillMaxSize().background(TerminalBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(top = 46.dp, bottom = 46.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TerminalNativeViewCompose(
                    emulator = emulator,
                    modifier = Modifier.fillMaxSize(),
                    fontSizeSp = terminalFontSize,
                    onResize = { columns, rows ->
                        emulator.resize(columns, rows)
                        session.resize(columns, rows)
                    },
                    onTap = { input.requestFocus() },
                )
                TerminalInputView(
                    onInput = ::send,
                    applicationCursorKeys = emulator.applicationCursorKeys,
                    controller = input,
                    modifier = Modifier.size(1.dp),
                )
                if (sessionState == PtyTerminalSession.State.PREPARING) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color(0xFF9E90FF),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Color(0xFF8C8F88), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        TerminalTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars),
            onBack = {
                input.clearFocus()
                onBack()
            },
            onClear = {
                send(byteArrayOf(0x15))
                emulator.feed("\u001Bc".toByteArray())
            },
            onRepair = { showRepair = true },
        )

        TerminalAccessoryRow(
            ctrlActive = ctrlActive,
            altActive = altActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars),
            onCtrl = { ctrlActive = !ctrlActive },
            onAlt = { altActive = !altActive },
            send = ::send,
            sendArrow = { direction ->
                val prefix = if (emulator.applicationCursorKeys) "\u001BO" else "\u001B["
                send((prefix + direction).toByteArray())
            },
        )

        if (showRepair) {
            AlertDialog(
                onDismissRequest = { if (!repairing) showRepair = false },
                title = { Text(if (repairing) "Repairing Linux…" else "Repair Linux container?") },
                text = {
                    Text(
                        repairError ?: if (repairing) {
                            status
                        } else {
                            "Alpine system files and installed packages will be reset. Files and projects in /root will remain untouched."
                        },
                    )
                },
                confirmButton = {
                    if (repairing) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = {
                            repairing = true
                            repairError = null
                            input.clearFocus()
                            session.stop()
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        RootfsInstaller(context).repair { status = it }
                                    }
                                }.onSuccess {
                                    showRepair = false
                                    repairing = false
                                    onBack()
                                }.onFailure { error ->
                                    repairing = false
                                    repairError = "Repair failed: ${error.message}"
                                }
                            }
                        }) { Text("Repair") }
                    }
                },
                dismissButton = {
                    if (!repairing) {
                        TextButton(onClick = { showRepair = false }) { Text(if (repairError == null) "Cancel" else "Close") }
                    }
                },
            )
        }
    }
}

private val LOGIN_URL = Regex("""https://[^\s\u001B]+""")

@Composable
private fun TerminalTopBar(
    modifier: Modifier,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onRepair: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(46.dp).background(TerminalBackground).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 5.dp),
            color = Color.White,
            fontSize = 28.sp,
        )
        Spacer(Modifier.weight(1f))
        Text("Pandora Linux", color = Color(0xFFDADDD6), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Text(
            "repair",
            modifier = Modifier.clickable(onClick = onRepair).padding(horizontal = 7.dp, vertical = 8.dp),
            color = Color(0xFFDADDD6),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "clear",
            modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 7.dp, vertical = 8.dp),
            color = Color(0xFF9E90FF),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TerminalAccessoryRow(
    ctrlActive: Boolean,
    altActive: Boolean,
    modifier: Modifier,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    send: (ByteArray) -> Unit,
    sendArrow: (Char) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(46.dp).background(AccessoryBackground).padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalKey("ESC", Modifier.weight(1f)) { send(byteArrayOf(0x1B)) }
        TerminalKey("⇥", Modifier.weight(1f)) { send(byteArrayOf(0x09)) }
        TerminalKey("CTRL", Modifier.weight(1f), ctrlActive, onCtrl)
        TerminalKey("ALT", Modifier.weight(1f), altActive, onAlt)
        TerminalKey("−", Modifier.weight(1f)) { send(byteArrayOf('-'.code.toByte())) }
        TerminalKey("↓", Modifier.weight(1f)) { sendArrow('B') }
        TerminalKey("↑", Modifier.weight(1f)) { sendArrow('A') }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    modifier: Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .background(if (active) AccessoryActive else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else AccessoryText,
            fontSize = if (label.length > 2) 11.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
