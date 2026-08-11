package com.pandora.mobile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandora.mobile.linux.ArchiveCatalog
import com.pandora.mobile.linux.CodexThreadCatalog
import com.pandora.mobile.linux.CodexThreadSummary
import com.pandora.mobile.linux.PandoraProject
import com.pandora.mobile.linux.ProjectCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ArchiveDeleteTarget {
    data class Project(val project: PandoraProject) : ArchiveDeleteTarget
    data class Chat(val chat: CodexThreadSummary) : ArchiveDeleteTarget
}

@Composable
fun ArchiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val archiveCatalog = remember(context) { ArchiveCatalog(context) }
    val projectCatalog = remember(context) { ProjectCatalog(context) }
    val chatCatalog = remember(context) { CodexThreadCatalog(context) }
    var archive by remember { mutableStateOf(archiveCatalog.read()) }
    var projects by remember { mutableStateOf(projectCatalog.readRegistered()) }
    var chats by remember { mutableStateOf(chatCatalog.readCached()) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchiveDeleteTarget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val archivedProjects = remember(archive, projects) {
        val registered = projects.associateBy { it.path }
        archive.projectPaths.map { registered[it] ?: PandoraProject(it) }.sortedBy { it.name.lowercase() }
    }
    val archivedChats = remember(archive, chats) {
        chats.filter { it.id in archive.chatIds && it.cwd !in archive.projectPaths }
            .sortedByDescending { it.updatedAtMillis }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Settings")
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Archive", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Restore anything you want back on Home", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

        if (archivedProjects.isEmpty() && archivedChats.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(58.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Your archive is empty", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Archive a chat or project from its menu to keep Home focused without deleting anything.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (error != null) {
                    item("error") {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                }
                if (archivedProjects.isNotEmpty()) {
                    item("projects-heading") { ArchiveHeading("PROJECTS") }
                    items(archivedProjects, key = { "project:${it.path}" }) { project ->
                        val chatCount = chats.count { it.cwd == project.path }
                        ArchiveRow(
                            icon = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                            title = project.name,
                            subtitle = "${project.displayPath} · $chatCount ${if (chatCount == 1) "chat" else "chats"}",
                            busy = busyKey == "project:${project.path}",
                            onRestore = {
                                busyKey = "project:${project.path}"
                                error = null
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { archiveCatalog.setProjectArchived(project.path, false) } }
                                        .onSuccess { archive = it }
                                        .onFailure { error = it.message ?: "Could not restore this project" }
                                    busyKey = null
                                }
                            },
                            onDelete = { deleteTarget = ArchiveDeleteTarget.Project(project) },
                        )
                    }
                    item("project-gap") { Spacer(Modifier.height(18.dp)) }
                }
                if (archivedChats.isNotEmpty()) {
                    item("chats-heading") { ArchiveHeading("CHATS") }
                    items(archivedChats, key = { "chat:${it.id}" }) { chat ->
                        ArchiveRow(
                            icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            title = chat.title,
                            subtitle = chat.preview.ifBlank { chat.cwd.replaceFirst("/root", "~") },
                            busy = busyKey == "chat:${chat.id}",
                            onRestore = {
                                busyKey = "chat:${chat.id}"
                                error = null
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { archiveCatalog.setChatArchived(chat.id, false) } }
                                        .onSuccess { archive = it }
                                        .onFailure { error = it.message ?: "Could not restore this chat" }
                                    busyKey = null
                                }
                            },
                            onDelete = { deleteTarget = ArchiveDeleteTarget.Chat(chat) },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val isProject = target is ArchiveDeleteTarget.Project
        val title = when (target) {
            is ArchiveDeleteTarget.Project -> target.project.name
            is ArchiveDeleteTarget.Chat -> target.chat.title
        }
        AlertDialog(
            onDismissRequest = { if (busyKey == null) deleteTarget = null },
            title = { Text("Delete $title permanently?") },
            text = {
                Text(
                    if (isProject) {
                        "This permanently deletes the project's chats from Pandora. The directory and its files stay untouched. This cannot be undone."
                    } else {
                        "This permanently deletes the conversation. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = busyKey == null,
                    onClick = {
                        val key = when (target) {
                            is ArchiveDeleteTarget.Project -> "project:${target.project.path}"
                            is ArchiveDeleteTarget.Chat -> "chat:${target.chat.id}"
                        }
                        busyKey = key
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    when (target) {
                                        is ArchiveDeleteTarget.Project -> {
                                            val ids = chats.filter { it.cwd == target.project.path }.map { it.id }
                                            ids.forEach(chatCatalog::delete)
                                            projectCatalog.remove(target.project.path)
                                            archiveCatalog.setProjectArchived(target.project.path, false)
                                            archiveCatalog.removeChats(ids)
                                        }
                                        is ArchiveDeleteTarget.Chat -> {
                                            chatCatalog.delete(target.chat.id)
                                            archiveCatalog.setChatArchived(target.chat.id, false)
                                        }
                                    }
                                }
                            }
                            result.onSuccess { updated ->
                                archive = updated
                                when (target) {
                                    is ArchiveDeleteTarget.Project -> {
                                        val ids = chats.filter { it.cwd == target.project.path }.mapTo(mutableSetOf()) { it.id }
                                        chats = chats.filterNot { it.id in ids }
                                        projects = projects.filterNot { it.path == target.project.path }
                                    }
                                    is ArchiveDeleteTarget.Chat -> chats = chats.filterNot { it.id == target.chat.id }
                                }
                                deleteTarget = null
                            }.onFailure { error = it.message ?: "Could not permanently delete this item" }
                            busyKey = null
                        }
                    },
                ) {
                    if (busyKey != null) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = busyKey == null, onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ArchiveHeading(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ArchiveRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRestore, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("Restore")
        }
        IconButton(onClick = onDelete, enabled = !busy) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete $title permanently",
                tint = if (busy) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
