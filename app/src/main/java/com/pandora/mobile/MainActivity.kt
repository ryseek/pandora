package com.pandora.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF20211F)
private val Paper = Color(0xFFF7F7F4)
private val Muted = Color(0xFF73756E)
private val Line = Color(0xFFE3E3DD)
private val Accent = Color(0xFF6D5CE7)
private val Terminal = Color(0xFF111210)
private val TerminalText = Color(0xFFD8E4D1)

private enum class Screen { Home, Chat, Container, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PandoraApp() }
    }
}

@Composable
private fun PandoraApp() {
    var screen by remember { mutableStateOf(Screen.Home) }
    var pendingTerminalCommand by remember { mutableStateOf<String?>(null) }
    MaterialTheme {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "screen"
            ) { target ->
                when (target) {
                    Screen.Home -> HomeScreen(
                        onNewChat = { screen = Screen.Chat },
                        onContainer = {
                            pendingTerminalCommand = null
                            screen = Screen.Container
                        },
                        onSettings = { screen = Screen.Settings },
                    )
                    Screen.Chat -> ChatScreen(onBack = { screen = Screen.Home })
                    Screen.Container -> ContainerScreen(
                        initialCommand = pendingTerminalCommand,
                        onBack = {
                            pendingTerminalCommand = null
                            screen = Screen.Home
                        },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Home },
                        onCodexLogin = {
                            // PRoot shares Android's network namespace, so Chrome can return
                            // directly to Codex's localhost callback. This avoids the device-code
                            // polling request that is unreliable on Android.
                            pendingTerminalCommand = "codex login"
                            screen = Screen.Container
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNewChat: () -> Unit, onContainer: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).background(Ink, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Text("P", color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(11.dp))
            Text("Pandora", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFFECEBE7), CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = Ink, fontSize = 15.sp) }
            Spacer(Modifier.width(8.dp))
            StatusPill()
        }

        Spacer(Modifier.height(54.dp))
        Text(
            "What will you build?",
            color = Ink,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "A private workspace that lives on this phone.",
            color = Muted,
            fontSize = 16.sp,
            lineHeight = 23.sp
        )

        Spacer(Modifier.height(38.dp))
        ActionCard(
            symbol = "✦",
            title = "Start a new chat",
            subtitle = "Open a clean conversation workspace",
            primary = true,
            onClick = onNewChat
        )
        Spacer(Modifier.height(13.dp))
        ActionCard(
            symbol = ">_",
            title = "Open Linux container",
            subtitle = "Alpine Linux · persistent · on-device",
            primary = false,
            onClick = onContainer
        )

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Color(0xFF55A76A), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Workspace stored locally", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusPill() {
    Row(
        modifier = Modifier
            .background(Color(0xFFECEBE7), RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(Color(0xFF55A76A), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("Local", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionCard(
    symbol: String,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val background = if (primary) Ink else Color.Transparent
    val foreground = if (primary) Color.White else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(22.dp))
            .border(if (primary) 0.dp else 1.dp, Line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(19.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(47.dp)
                .background(if (primary) Color(0xFF353633) else Color(0xFFECEBE7), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = if (primary) Color(0xFFBEB3FF) else Accent, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = if (primary) Color(0xFFB7B8B3) else Muted, fontSize = 13.sp)
        }
        Text("→", color = if (primary) Color.White else Muted, fontSize = 20.sp)
    }
}

@Composable
private fun Header(title: String, detail: String? = null, onBack: () -> Unit, dark: Boolean = false) {
    val color = if (dark) Color.White else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (dark) Color(0xFF292A27) else Color(0xFFEAEAE5), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = color, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, color = if (dark) Color(0xFF8E918A) else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChatScreen(onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Header("New chat", "Model not connected", onBack)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(52.dp).background(Color(0xFFE9E6FA), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) { Text("✦", color = Accent, fontSize = 22.sp) }
            Spacer(Modifier.height(18.dp))
            Text("Start with an idea", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Chat transport is intentionally disconnected in this first build.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Row(
            modifier = Modifier.padding(16.dp).border(1.dp, Line, RoundedCornerShape(19.dp)).padding(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Message Pandora…", color = Color(0xFFA2A39E), fontSize = 15.sp)
                    inner()
                }
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(38.dp).background(Color(0xFFDADAD4), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("↑", color = Color.White, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun ContainerScreen(initialCommand: String?, onBack: () -> Unit) {
    TerminalScreen(initialCommand = initialCommand, onBack = onBack)
}
