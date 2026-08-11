package com.pandora.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PluginsScreen(
    state: AdbPluginState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    PluginPage(title = "Plugins", onBack = onBack) {
        Text(
            "Plugins ask before taking control of your device.",
            color = MaterialThemeColors.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialThemeColors.line, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PluginIcon()
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("ADB phone control", color = MaterialThemeColors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("By Pandora · device plugin", color = MaterialThemeColors.muted, fontSize = 12.sp)
                }
                Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(18.dp))
            Text("Let the Pandora agent control this phone", color = MaterialThemeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Uses Android's Wireless debugging connection.",
                color = MaterialThemeColors.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialThemeColors.line))
            Spacer(Modifier.height(15.dp))
            PluginCapability("Tap, type, swipe, and open apps")
            Spacer(Modifier.height(10.dp))
            PluginCapability("Visible frame while control is active")
        }

        if (state.enabled) {
            Spacer(Modifier.height(20.dp))
            StatusRow(state)
            Spacer(Modifier.height(20.dp))
            PrimaryPluginButton(
                label = when (state.stage) {
                    AdbPluginStage.CONNECTED -> "Review connection"
                    AdbPluginStage.ERROR -> "Try setup again"
                    else -> "Set up connection"
                },
                onClick = onOpenSetup,
            )
        } else {
            Spacer(Modifier.height(24.dp))
            PrimaryPluginButton(label = "Enable and set up") {
                onEnabledChange(true)
                onOpenSetup()
            }
        }
    }
}

@Composable
fun AdbSetupScreen(
    state: AdbPluginState,
    onBack: () -> Unit,
    onBeginPairing: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var developerOptionsEnabled by remember { mutableStateOf(developerOptionsEnabled(context)) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                developerOptionsEnabled = developerOptionsEnabled(context)
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            onBeginPairing()
            openDeveloperSettings(context)
        }
    }
    val launchSetup = {
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationsAllowed) {
            onBeginPairing()
            openDeveloperSettings(context)
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    PluginPage(title = "Connect phone", subtitle = "ADB phone control", onBack = onBack) {
        SetupProgress(state.stage, developerOptionsEnabled, overlayPermissionGranted)
        Spacer(Modifier.height(28.dp))
        when {
            !developerOptionsEnabled -> DeveloperOptionsContent {
                openAboutPhone(context)
            }
            !overlayPermissionGranted -> OverlayPermissionContent {
                openOverlayPermission(context)
            }
            state.stage == AdbPluginStage.CONNECTED -> ConnectedContent()
            state.stage == AdbPluginStage.ERROR -> ErrorContent(state.detail, onRetry = launchSetup)
            else -> PairingContent(state = state, onLaunchSetup = launchSetup)
        }
    }
}

@Composable
private fun OverlayPermissionContent(onAllowOverlay: () -> Unit) {
    Text("Show the safety frame", color = MaterialThemeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text(
        "Allow Pandora to appear over other apps while an agent controls this phone.",
        color = MaterialThemeColors.muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Spacer(Modifier.height(22.dp))
    Column(Modifier.fillMaxWidth().background(MaterialThemeColors.soft, RoundedCornerShape(15.dp)).padding(16.dp)) {
        Text("Always visible during control", color = MaterialThemeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "The thin Pandora frame shows when control is active. Its Stop button immediately disconnects the agent, even above Chrome or another app.",
            color = MaterialThemeColors.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
    Spacer(Modifier.height(26.dp))
    PrimaryPluginButton("Allow control overlay", onClick = onAllowOverlay)
    Spacer(Modifier.height(16.dp))
    Text(
        "Android will open Display over other apps. Enable Pandora, then return here.",
        color = MaterialThemeColors.muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun DeveloperOptionsContent(onOpenAboutPhone: () -> Unit) {
    Text("Enable Developer options", color = MaterialThemeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text(
        "Android hides Wireless debugging until Developer options are unlocked.",
        color = MaterialThemeColors.muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Spacer(Modifier.height(22.dp))
    SetupInstruction(number = "1", title = "Open About phone", detail = "Pandora will open Android’s device information screen.")
    Spacer(Modifier.height(15.dp))
    SetupInstruction(number = "2", title = "Find Build number", detail = "Some phones place it under Software information or Version.")
    Spacer(Modifier.height(15.dp))
    SetupInstruction(number = "3", title = "Tap Build number seven times", detail = "Confirm your screen lock if Android asks, then return to Pandora.")
    Spacer(Modifier.height(26.dp))
    PrimaryPluginButton("Open About phone", onClick = onOpenAboutPhone)
    Spacer(Modifier.height(16.dp))
    Text(
        "Pandora will detect Developer options when you return.",
        color = MaterialThemeColors.muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun PairingContent(state: AdbPluginState, onLaunchSetup: () -> Unit) {
    Text("Pair with Wireless debugging", color = MaterialThemeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text(
        "Android requires this once. After pairing, Pandora can reconnect without another code.",
        color = MaterialThemeColors.muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Spacer(Modifier.height(22.dp))

    SetupInstruction(number = "1", title = "Open Developer options", detail = "Pandora will take you directly to Android settings.")
    Spacer(Modifier.height(15.dp))
    SetupInstruction(number = "2", title = "Open Wireless debugging", detail = "Turn it on, then tap Pair device with pairing code.")
    Spacer(Modifier.height(15.dp))
    SetupInstruction(number = "3", title = "Enter the code from the notification", detail = "Pull down the notification shade. No split screen is needed.")

    Spacer(Modifier.height(26.dp))
    if (state.stage in setOf(
            AdbPluginStage.PREPARING,
            AdbPluginStage.WAITING_FOR_PAIRING,
            AdbPluginStage.WAITING_FOR_CODE,
            AdbPluginStage.PAIRING,
            AdbPluginStage.CONNECTING,
        )
    ) {
        PairingStatus(state)
    } else {
        PrimaryPluginButton("Open Android settings", onClick = onLaunchSetup)
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Keep the pairing screen open while entering the six-digit code from Pandora’s notification.",
        color = MaterialThemeColors.muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun ConnectedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(62.dp).background(MaterialThemeColors.accentSurface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialThemeColors.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Phone connected", color = MaterialThemeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            "Pandora can now start an ADB control session when you ask the agent to operate this phone.",
            color = MaterialThemeColors.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().background(MaterialThemeColors.soft, RoundedCornerShape(15.dp)).padding(16.dp)) {
            Text("When control starts", color = MaterialThemeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("A Pandora color frame appears around the screen, with a Stop button at the bottom.", color = MaterialThemeColors.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ErrorContent(detail: String?, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(58.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn’t finish pairing", color = MaterialThemeColors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(detail ?: "Android wireless debugging did not respond.", color = MaterialThemeColors.muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(24.dp))
        PrimaryPluginButton("Try again", onClick = onRetry)
    }
}

@Composable
private fun PairingStatus(state: AdbPluginState) {
    Row(
        Modifier.fillMaxWidth().background(MaterialThemeColors.accentSurface, RoundedCornerShape(15.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(21.dp), color = MaterialThemeColors.accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                when (state.stage) {
                    AdbPluginStage.WAITING_FOR_CODE -> "Pairing code detected"
                    AdbPluginStage.PAIRING -> "Checking pairing code"
                    AdbPluginStage.CONNECTING -> "Connecting"
                    else -> "Waiting for Android"
                },
                color = MaterialThemeColors.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(state.detail.orEmpty(), color = MaterialThemeColors.muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SetupProgress(stage: AdbPluginStage, developerOptionsEnabled: Boolean, overlayPermissionGranted: Boolean) {
    val current = when {
        !developerOptionsEnabled -> 0
        !overlayPermissionGranted -> 1
        stage == AdbPluginStage.CONNECTED -> 4
        stage in setOf(AdbPluginStage.PAIRING, AdbPluginStage.CONNECTING, AdbPluginStage.WAITING_FOR_CODE) -> 3
        else -> 2
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ProgressStep("1", "Developer", current >= 1, Modifier.weight(1f))
        ProgressLine(current >= 2)
        ProgressStep("2", "Overlay", current >= 2, Modifier.weight(1f))
        ProgressLine(current >= 3)
        ProgressStep("3", "Pair", current >= 3, Modifier.weight(1f))
        ProgressLine(current >= 4)
        ProgressStep("4", "Ready", current >= 4, Modifier.weight(1f))
    }
}

@Composable
private fun ProgressStep(number: String, label: String, active: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(28.dp).background(if (active) MaterialThemeColors.accent else MaterialThemeColors.soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (active && number.toInt() < 4) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                Text(number, color = if (active) Color.White else MaterialThemeColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (active) MaterialThemeColors.ink else MaterialThemeColors.muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ProgressLine(active: Boolean) {
    Box(Modifier.padding(top = 13.dp).width(18.dp).height(2.dp).background(if (active) MaterialThemeColors.accent else MaterialThemeColors.line))
}

@Composable
private fun SetupInstruction(number: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(MaterialThemeColors.soft, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = MaterialThemeColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialThemeColors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = MaterialThemeColors.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun StatusRow(state: AdbPluginState) {
    val connected = state.stage == AdbPluginStage.CONNECTED
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).background(if (connected) Color(0xFF4E9A67) else MaterialThemeColors.muted, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                connected -> "Connected"
                state.stage == AdbPluginStage.ERROR -> "Setup needs attention"
                else -> "Not connected"
            },
            color = MaterialThemeColors.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PluginCapability(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(MaterialThemeColors.accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, color = MaterialThemeColors.ink, fontSize = 12.sp)
    }
}

@Composable
private fun PluginIcon() {
    Box(Modifier.size(50.dp).background(MaterialThemeColors.accentSurface, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DeveloperMode, contentDescription = null, tint = MaterialThemeColors.accent, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PrimaryPluginButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialThemeColors.accent, RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PluginPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(MaterialThemeColors.soft, CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialThemeColors.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = MaterialThemeColors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = MaterialThemeColors.muted, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialThemeColors.line))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), content = { content(); Spacer(Modifier.height(28.dp)) })
    }
}

private fun openDeveloperSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openAboutPhone(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openOverlayPermission(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun developerOptionsEnabled(context: android.content.Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

private object MaterialThemeColors {
    val ink: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    val muted: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val line: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val accent: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val soft: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val accentSurface: Color @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
}
