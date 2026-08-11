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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandora.mobile.linux.RootfsInstaller
import com.pandora.mobile.linux.WorkspaceBackup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsInk: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
private val SettingsMuted: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
private val SettingsLine: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
private val SettingsAccent: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val SettingsSoftSurface: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant

@Composable
fun SettingsScreen(
    themePreference: AppSettings.ThemePreference,
    onThemePreferenceChanged: (AppSettings.ThemePreference) -> Unit,
    onBack: () -> Unit,
    onCodexLogin: () -> Unit,
    onStopAllForRepair: () -> Unit,
) {
    val context = LocalContext.current
    var fontSize by remember { mutableFloatStateOf(AppSettings.terminalFontSize(context)) }
    val application = context.applicationContext as PandoraApplication
    val speechManager = application.speechModels
    val speechStates by speechManager.states.collectAsState()
    val dictationDiagnostics by application.onDeviceSpeech.diagnostics.collectAsState()
    var selectedStt by remember { mutableStateOf(AppSettings.speechToTextModel(context)) }
    var dictationProcessor by remember { mutableStateOf(AppSettings.dictationProcessor(context)) }
    var refineWithWhisper by remember { mutableStateOf(AppSettings.refineDictationWithWhisper(context)) }
    var selectedTts by remember { mutableStateOf(AppSettings.textToSpeechModel(context)) }
    var autoSpeak by remember { mutableStateOf(AppSettings.speakAssistantResponses(context)) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRepair by remember { mutableStateOf(false) }
    var repairing by remember { mutableStateOf(false) }
    var repairStatus by remember { mutableStateOf<String?>(null) }
    var repairError by remember { mutableStateOf<String?>(null) }
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

    fun deleteSpeechModel(model: SpeechModel) {
        if (!speechManager.delete(model)) return
        if (model.id == SpeechModels.WHISPER_TINY_ID) {
            refineWithWhisper = false
            AppSettings.setRefineDictationWithWhisper(context, false)
        }
        val fallback = SpeechModels.all.firstOrNull {
            it.kind == model.kind && it.id != model.id && speechManager.isInstalled(it)
        }
        when (model.kind) {
            SpeechModelKind.SPEECH_TO_TEXT -> if (selectedStt == model.id) {
                selectedStt = fallback?.id ?: SpeechModels.DEFAULT_STT_ID
                AppSettings.setSpeechToTextModel(context, selectedStt)
            }
            SpeechModelKind.TEXT_TO_SPEECH -> if (selectedTts == model.id) {
                selectedTts = fallback?.id ?: SpeechModels.DEFAULT_TTS_ID
                AppSettings.setTextToSpeechModel(context, selectedTts)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(SettingsSoftSurface, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SettingsInk,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = SettingsInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsLine))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp)) {
            Text("APPEARANCE", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("Theme", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Follow the device or choose a look for Pandora.", color = SettingsMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().background(SettingsSoftSurface, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeChoice(
                    label = "System",
                    selected = themePreference == AppSettings.ThemePreference.SYSTEM,
                    icon = { tint -> Icon(Icons.Rounded.SettingsBrightness, null, tint = tint, modifier = Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = { onThemePreferenceChanged(AppSettings.ThemePreference.SYSTEM) },
                )
                ThemeChoice(
                    label = "Light",
                    selected = themePreference == AppSettings.ThemePreference.LIGHT,
                    icon = { tint -> Icon(Icons.Rounded.WbSunny, null, tint = tint, modifier = Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = { onThemePreferenceChanged(AppSettings.ThemePreference.LIGHT) },
                )
                ThemeChoice(
                    label = "Dark",
                    selected = themePreference == AppSettings.ThemePreference.DARK,
                    icon = { tint -> Icon(Icons.Rounded.DarkMode, null, tint = tint, modifier = Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = { onThemePreferenceChanged(AppSettings.ThemePreference.DARK) },
                )
            }

            Spacer(Modifier.height(30.dp))
            Text("VOICE", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Speech stays on this device. Models download only when you ask and unload after use.",
                color = SettingsMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text("Dictation processor", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (dictationProcessor == AppSettings.DictationProcessor.CPU) {
                    "Most compatible · two inference threads"
                } else {
                    "Android NNAPI · acceleration depends on device support"
                },
                color = SettingsMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(SettingsSoftSurface, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeChoice(
                    label = "CPU",
                    selected = dictationProcessor == AppSettings.DictationProcessor.CPU,
                    icon = { tint -> Icon(Icons.Rounded.Memory, null, tint = tint, modifier = Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        dictationProcessor = AppSettings.DictationProcessor.CPU
                        AppSettings.setDictationProcessor(context, dictationProcessor)
                    },
                )
                ThemeChoice(
                    label = "GPU",
                    selected = dictationProcessor == AppSettings.DictationProcessor.GPU,
                    icon = { tint -> Icon(Icons.Rounded.Speed, null, tint = tint, modifier = Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        dictationProcessor = AppSettings.DictationProcessor.GPU
                        AppSettings.setDictationProcessor(context, dictationProcessor)
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                dictationDiagnosticsLabel(dictationDiagnostics),
                color = if (dictationDiagnostics.droppedAudioMillis > 0) {
                    androidx.compose.material3.MaterialTheme.colorScheme.error
                } else {
                    SettingsMuted
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(22.dp))
            Text("Dictation model", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            SpeechModels.all.filter { it.kind == SpeechModelKind.SPEECH_TO_TEXT }.forEach { model ->
                SpeechModelRow(
                    model = model,
                    selected = selectedStt == model.id,
                    state = speechStates[model.id] ?: SpeechModelDownload.NotInstalled,
                    onSelect = {
                        selectedStt = model.id
                        AppSettings.setSpeechToTextModel(context, model.id)
                    },
                    onDownload = {
                        speechManager.download(model)
                    },
                    onDelete = { deleteSpeechModel(model) },
                )
                Spacer(Modifier.height(10.dp))
            }

            val whisperInstalled = speechStates[SpeechModels.WHISPER_TINY_ID] is SpeechModelDownload.Installed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = whisperInstalled) {
                        refineWithWhisper = !refineWithWhisper
                        AppSettings.setRefineDictationWithWhisper(context, refineWithWhisper)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Refine final transcript with Whisper",
                        color = if (whisperInstalled) SettingsInk else SettingsMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (whisperInstalled) {
                            "Keep Kroko's live text, then improve it after you stop recording."
                        } else {
                            "Download Whisper Tiny above to enable the optional second pass."
                        },
                        color = SettingsMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                Switch(
                    checked = refineWithWhisper && whisperInstalled,
                    onCheckedChange = { enabled ->
                        refineWithWhisper = enabled
                        AppSettings.setRefineDictationWithWhisper(context, enabled)
                    },
                    enabled = whisperInstalled,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Reading voice", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            SpeechModels.all.filter { it.kind == SpeechModelKind.TEXT_TO_SPEECH }.forEach { model ->
                SpeechModelRow(
                    model = model,
                    selected = selectedTts == model.id,
                    state = speechStates[model.id] ?: SpeechModelDownload.NotInstalled,
                    onSelect = {
                        selectedTts = model.id
                        AppSettings.setTextToSpeechModel(context, model.id)
                    },
                    onDownload = {
                        selectedTts = model.id
                        AppSettings.setTextToSpeechModel(context, model.id)
                        speechManager.download(model)
                    },
                    onDelete = { deleteSpeechModel(model) },
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Read new replies aloud", color = SettingsInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text("Off by default to save battery.", color = SettingsMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = autoSpeak,
                    onCheckedChange = {
                        autoSpeak = it
                        AppSettings.setSpeakAssistantResponses(context, it)
                    },
                )
            }

            Spacer(Modifier.height(30.dp))
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
            Text("CONTAINER", color = SettingsAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Maintenance for the Alpine Linux system. Your persistent /root workspace is stored separately.",
                color = SettingsMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            BackupAction(
                title = "Repair Linux container",
                subtitle = "Reinstall system files and required packages",
                symbol = "↻",
                enabled = !repairing && !backupBusy,
                onClick = {
                    repairError = null
                    showRepair = true
                },
            )
            if (repairStatus != null && !showRepair) {
                Spacer(Modifier.height(12.dp))
                Text(repairStatus.orEmpty(), color = SettingsMuted, fontSize = 13.sp)
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

    if (showRepair) {
        AlertDialog(
            onDismissRequest = { if (!repairing) showRepair = false },
            title = { Text(if (repairing) "Repairing Linux…" else "Repair Linux container?") },
            text = {
                Text(
                    repairError ?: if (repairing) {
                        repairStatus ?: "Preparing repair…"
                    } else {
                        "All Linux sessions will stop. Alpine system files and installed packages will be reset. Files and projects in /root will remain untouched."
                    },
                )
            },
            confirmButton = {
                if (repairing) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        repairing = true
                        repairStatus = "Preparing repair…"
                        repairError = null
                        onStopAllForRepair()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    RootfsInstaller(context.applicationContext).repair { repairStatus = it }
                                }
                            }.onSuccess {
                                repairing = false
                                repairStatus = "Repair complete"
                                showRepair = false
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
                    TextButton(onClick = { showRepair = false }) {
                        Text(if (repairError == null) "Cancel" else "Close")
                    }
                }
            },
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.background else Color.Transparent
    val foreground = if (selected) SettingsInk else SettingsMuted
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(if (selected) SettingsAccent else foreground)
        Spacer(Modifier.width(6.dp))
        Text(label, color = foreground, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SpeechModelRow(
    model: SpeechModel,
    selected: Boolean,
    state: SpeechModelDownload,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloading = state as? SpeechModelDownload.Downloading
    val availability = when (state) {
        SpeechModelDownload.Installed -> "installed"
        SpeechModelDownload.NotInstalled -> "not installed"
        is SpeechModelDownload.Downloading -> "downloading"
        is SpeechModelDownload.Failed -> "download failed"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) SettingsAccent else SettingsLine, RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics {
                stateDescription = buildString {
                    append(if (selected) "Selected, " else "Not selected, ")
                    append(availability)
                }
            }
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(SettingsSoftSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, color = SettingsInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${model.language} · ${formatModelBytes(model.downloadBytes)} download",
                    color = SettingsMuted,
                    fontSize = 12.sp,
                )
            }
            when (state) {
                SpeechModelDownload.Installed -> IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${model.name}", tint = SettingsMuted)
                }
                SpeechModelDownload.NotInstalled, is SpeechModelDownload.Failed -> IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download ${model.name}", tint = SettingsAccent)
                }
                is SpeechModelDownload.Downloading -> CircularProgressIndicator(
                    progress = { (state.bytesRead.toFloat() / state.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp).padding(end = 2.dp),
                    color = SettingsAccent,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(model.description, color = SettingsMuted, fontSize = 12.sp, lineHeight = 17.sp)
        if (downloading != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (downloading.bytesRead.toFloat() / downloading.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = SettingsAccent,
                trackColor = SettingsLine,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${formatModelBytes(downloading.bytesRead)} of ${formatModelBytes(downloading.totalBytes)}",
                color = SettingsMuted,
                fontSize = 11.sp,
            )
        }
        if (state is SpeechModelDownload.Failed) {
            Spacer(Modifier.height(7.dp))
            Text(state.detail, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

private fun dictationDiagnosticsLabel(diagnostics: DictationDiagnostics): String {
    val realTimeFactor = diagnostics.realTimeFactor
        ?: return "No performance measurement yet · run dictation to test this device."
    return buildString {
        append(if (diagnostics.active) "Live" else "Last")
        append(" ${diagnostics.processor.name} run · ")
        append(String.format(Locale.US, "%.2f× realtime", realTimeFactor))
        append(" · ")
        if (diagnostics.droppedAudioMillis == 0L) {
            append("no audio dropped")
        } else {
            append("${diagnostics.droppedAudioMillis} ms dropped")
        }
        if (diagnostics.active && diagnostics.bufferedAudioMillis > 0) {
            append(" · ${diagnostics.bufferedAudioMillis} ms queued")
        }
        append(" · lower is better")
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
            Modifier.size(42.dp).background(SettingsSoftSurface, RoundedCornerShape(13.dp)),
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
