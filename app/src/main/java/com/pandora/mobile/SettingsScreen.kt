package com.pandora.mobile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.pandora.mobile.linux.WorkspaceBackup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsInk = Color(0xFF20211F)
private val SettingsMuted = Color(0xFF73756E)
private val SettingsLine = Color(0xFFE3E3DD)
private val SettingsAccent = Color(0xFF6D5CE7)

@Composable
fun SettingsScreen(onBack: () -> Unit, onCodexLogin: () -> Unit) {
    val context = LocalContext.current
    var fontSize by remember { mutableFloatStateOf(AppSettings.terminalFontSize(context)) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val backup = remember { WorkspaceBackup(context.filesDir, context.cacheDir) }
    val codexInstalled = remember { File(context.filesDir, "linux-workspace/.local/bin/codex").exists() }
    val codexSignedIn = remember { File(context.filesDir, "linux-workspace/.codex/auth.json").exists() }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            backupStatus = "Saving backup…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = checkNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                            "Could not open the selected file"
                        }
                        output.use(backup::write)
                    }
                }
                result.onSuccess {
                    backupStatus = "Backup saved · ${it.files} files · ${formatBytes(it.bytes)}"
                }.onFailure {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    backupStatus = "Backup failed: ${it.message}"
                }
                backupBusy = false
            }
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    fun updateFontSize(value: Float) {
        fontSize = value.roundToInt().toFloat().coerceIn(
            AppSettings.MIN_TERMINAL_FONT_SIZE,
            AppSettings.MAX_TERMINAL_FONT_SIZE,
        )
        AppSettings.setTerminalFontSize(context, fontSize)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(Color(0xFFEAEAE5), CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", color = SettingsInk, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = SettingsInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsLine))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp)) {
            Text("TERMINAL", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Font size", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Changes apply when a terminal is opened.", color = SettingsMuted, fontSize = 13.sp)
                }
                Text(
                    "${fontSize.roundToInt()} sp",
                    color = SettingsInk,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { updateFontSize(fontSize - 1f) }
                Slider(
                    value = fontSize,
                    onValueChange = ::updateFontSize,
                    valueRange = AppSettings.MIN_TERMINAL_FONT_SIZE..AppSettings.MAX_TERMINAL_FONT_SIZE,
                    steps = 12,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = SettingsAccent,
                        activeTrackColor = SettingsAccent,
                        inactiveTrackColor = Color(0xFFDAD9D4),
                    ),
                )
                StepButton("+") { updateFontSize(fontSize + 1f) }
            }

            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(
                    "localhost:~# lscpu",
                    color = Color(0xFFD8E4D1),
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.45f).sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Architecture: aarch64",
                    color = Color(0xFFD8E4D1),
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.45f).sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("CODEX", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, SettingsLine, RoundedCornerShape(16.dp))
                    .clickable(enabled = codexInstalled, onClick = onCodexLogin)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).background(Color(0xFF20211F), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("✦", color = Color(0xFFBEB3FF), fontSize = 18.sp) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Codex login", color = SettingsInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            !codexInstalled -> "Codex is still installing"
                            codexSignedIn -> "Signed in · authenticate again"
                            else -> "Sign in with ChatGPT"
                        },
                        color = SettingsMuted,
                        fontSize = 13.sp,
                    )
                }
                Text("→", color = if (codexInstalled) SettingsAccent else SettingsMuted, fontSize = 19.sp)
            }

            Spacer(Modifier.height(28.dp))
            Text("BACKUP", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Backups contain your complete Linux home, including projects, settings, and Codex/GitHub credentials.",
                color = SettingsMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            BackupAction(
                title = "Save backup",
                subtitle = "Export the persistent workspace as a portable ZIP",
                symbol = "↓",
                enabled = !backupBusy,
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                    createBackup.launch("pandora-backup-$stamp.zip")
                },
            )
            Spacer(Modifier.height(10.dp))
            BackupAction(
                title = "Restore backup",
                subtitle = "Replace the current workspace from a Pandora backup",
                symbol = "↑",
                enabled = !backupBusy,
                onClick = { chooseBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
            )
            if (backupStatus != null || backupBusy) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (backupBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SettingsAccent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(backupStatus.orEmpty(), color = SettingsMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy) restoreUri = null },
            title = { Text("Replace Linux workspace?") },
            text = {
                Text(
                    "Everything currently stored in /root will be replaced, including projects and account sessions. " +
                        "Pandora validates and stages the backup before changing the current workspace.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = {
                        val uri = restoreUri ?: return@TextButton
                        backupBusy = true
                        backupStatus = "Restoring backup…"
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val input = checkNotNull(context.contentResolver.openInputStream(uri)) {
                                        "Could not open the selected backup"
                                    }
                                    input.use(backup::restore)
                                }
                            }
                            result.onSuccess {
                                backupStatus = "Restore complete · ${it.files} entries · ${formatBytes(it.bytes)}"
                            }.onFailure {
                                backupStatus = "Restore failed: ${it.message}"
                            }
                            backupBusy = false
                            restoreUri = null
                        }
                    },
                ) { Text("Replace and restore") }
            },
            dismissButton = {
                TextButton(enabled = !backupBusy, onClick = { restoreUri = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupAction(
    title: String,
    subtitle: String,
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, SettingsLine, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(Color(0xFFEDEAE5), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(symbol, color = SettingsAccent, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) SettingsInk else SettingsMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = SettingsMuted, fontSize = 13.sp)
        }
        Text("→", color = if (enabled) SettingsAccent else SettingsMuted, fontSize = 19.sp)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(Locale.US, bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .border(1.dp, SettingsLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SettingsInk, fontSize = 20.sp)
    }
}
