package com.pandora.mobile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pandora.mobile.linux.PtyTerminalSession

private val TerminalBackground = Color.Black
private val AccessoryBackground = Color(0xFF111310)
private val AccessoryActive = Color(0xFF6757DE)
private val AccessoryText = Color(0xFFE6E8E2)

@Composable
fun TerminalScreen(
    session: ManagedTerminalSession,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    val emulator = session.emulator
    val terminalFontSize = remember { AppSettings.terminalFontSize(context) }
    val input = rememberTerminalInputController()
    val sessionState by session.state.collectAsState()
    val status by session.status.collectAsState()
    val readOnly = sessionState == PtyTerminalSession.State.STOPPED || sessionState == PtyTerminalSession.State.FAILED
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }
    fun send(bytes: ByteArray) {
        if (bytes.isEmpty() || readOnly) return
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

    BackHandler {
        input.clearFocus()
        onBack()
    }

    androidx.compose.runtime.LaunchedEffect(readOnly) {
        if (readOnly) input.clearFocus()
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
                    onTap = { if (!readOnly) input.requestFocus() },
                )
                if (!readOnly) {
                    TerminalInputView(
                        onInput = ::send,
                        applicationCursorKeys = emulator.applicationCursorKeys,
                        controller = input,
                        modifier = Modifier.size(1.dp),
                    )
                }
                if (sessionState == PtyTerminalSession.State.PREPARING) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 34.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            "Preparing your local workspace",
                            color = Color(0xFFE6E8E2),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pandora is installing a private Linux environment on this device.",
                            color = Color(0xFF9A9D95),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                        LinearProgressIndicator(
                            progress = { installProgress(status) },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = Color(0xFF9E90FF),
                            trackColor = Color(0xFF292C27),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF9E90FF),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(9.dp))
                            Text(status, color = Color(0xFFB6B9B1), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
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
            readOnly = readOnly,
            onStop = { showStop = true },
            onClear = {
                send(byteArrayOf(0x15))
                emulator.feed("\u001Bc".toByteArray())
            },
        )

        if (readOnly) {
            StoppedTranscriptBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                onRestart = onRestart,
            )
        } else {
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
        }

        if (showStop && !readOnly) {
            AlertDialog(
                onDismissRequest = { showStop = false },
                title = { Text("Stop this Linux session?") },
                text = { Text("The running shell and its child processes will end. Files in /root will remain saved.") },
                confirmButton = {
                    TextButton(onClick = {
                        showStop = false
                        input.clearFocus()
                        onStop()
                    }) { Text("Stop session", color = Color(0xFFD84B4B)) }
                },
                dismissButton = {
                    TextButton(onClick = { showStop = false }) { Text("Keep running") }
                },
            )
        }
    }
}

private fun installProgress(status: String): Float = when {
    status.contains("workspace", ignoreCase = true) -> 0.12f
    status.contains("Alpine", ignoreCase = true) -> 0.28f
    status.contains("SSL", ignoreCase = true) || status.contains("utilities", ignoreCase = true) -> 0.48f
    status.contains("terminal", ignoreCase = true) -> 0.66f
    status.contains("Codex", ignoreCase = true) -> 0.84f
    status.contains("container", ignoreCase = true) -> 0.96f
    else -> 0.08f
}

@Composable
private fun TerminalTopBar(
    modifier: Modifier,
    readOnly: Boolean,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(46.dp).background(TerminalBackground),
    ) {
        Text(
            "‹",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
                .padding(horizontal = 18.dp, vertical = 5.dp),
            color = Color.White,
            fontSize = 28.sp,
        )
        Text(
            "Pandora Linux",
            modifier = Modifier.align(Alignment.Center),
            color = Color(0xFFDADDD6),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (readOnly) {
                Box(Modifier.size(7.dp).background(Color(0xFF777B73), androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.size(7.dp))
                Text("stopped", color = Color(0xFF9A9D95), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 34.dp)
                        .background(Color(0xFF7D2424), RoundedCornerShape(7.dp))
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("■ stop", color = Color(0xFFFFDADA), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Text(
                    "clear",
                    modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 8.dp, vertical = 8.dp),
                    color = Color(0xFF9E90FF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StoppedTranscriptBar(modifier: Modifier = Modifier, onRestart: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(AccessoryBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Color(0xFF777B73), androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(9.dp))
        Text("Stopped", color = Color(0xFFB6B9B1), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(36.dp)
                .background(AccessoryActive, RoundedCornerShape(10.dp))
                .clickable(onClick = onRestart)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
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
