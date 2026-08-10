package com.pandora.mobile.linux

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class PandoraProject(
    val path: String,
    val customName: String? = null,
    val pinned: Boolean = false,
) {
    val name: String = customName?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').ifBlank { "root" }
    val displayPath: String = path.replaceFirst("/root", "~")
}

/** Owns the small amount of state that turns Codex working directories into projects. */
class ProjectCatalog(context: Context) {
    private val installer = RootfsInstaller(context.applicationContext)
    private val registry = File(installer.workspace, ".pandora/projects.json")

    fun readRegistered(): List<PandoraProject> = synchronized(LOCK) {
        runCatching {
            val paths = JSONArray(registry.readText())
            buildList {
                for (index in 0 until paths.length()) {
                    when (val item = paths.opt(index)) {
                        is String -> item.takeIf(::isProjectPath)?.let { add(PandoraProject(it)) }
                        is JSONObject -> item.optString("path").takeIf(::isProjectPath)?.let { path ->
                            add(
                                PandoraProject(
                                    path = path,
                                    customName = if (item.isNull("name")) null else item.optString("name").ifBlank { null },
                                    pinned = item.optBoolean("pinned"),
                                ),
                            )
                        }
                    }
                }
            }.distinctBy { it.path }
        }.getOrDefault(emptyList())
    }

    /** Shows real, top-level Linux workspace folders without exposing Pandora's hidden state. */
    fun discoverFolders(): List<PandoraProject> {
        installer.workspace.mkdirs()
        return installer.workspace.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .map { PandoraProject("/root/${it.name}") }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun register(path: String): PandoraProject = synchronized(LOCK) {
        require(isProjectPath(path)) { "Choose a folder inside /root" }
        val existing = readRegistered()
        val project = existing.firstOrNull { it.path == path } ?: PandoraProject(path)
        write((existing + project).distinctBy { it.path })
        project
    }

    fun rename(path: String, proposedName: String): PandoraProject = synchronized(LOCK) {
        val name = proposedName.trim().take(64)
        require(name.isNotBlank()) { "Enter a project name" }
        val projects = readRegistered()
        val current = projects.firstOrNull { it.path == path } ?: PandoraProject(path)
        val updated = current.copy(customName = name)
        write((projects.filterNot { it.path == path } + updated).distinctBy { it.path })
        updated
    }

    fun setPinned(path: String, pinned: Boolean): PandoraProject = synchronized(LOCK) {
        val projects = readRegistered()
        val current = projects.firstOrNull { it.path == path } ?: PandoraProject(path)
        val updated = current.copy(pinned = pinned)
        write((projects.filterNot { it.path == path } + updated).distinctBy { it.path })
        updated
    }

    fun remove(path: String) = synchronized(LOCK) {
        write(readRegistered().filterNot { it.path == path })
    }

    fun createFolder(name: String): PandoraProject {
        val folderName = validateFolderName(name)
        val directory = File(installer.workspace, folderName)
        require(!directory.exists()) { "A folder named $folderName already exists" }
        check(directory.mkdirs()) { "Could not create the folder" }
        return register("/root/$folderName")
    }

    fun cloneRepository(url: String): PandoraProject {
        val repositoryUrl = url.trim()
        require(repositoryUrl.isNotBlank()) { "Enter a repository URL" }
        val folderName = repositoryUrl
            .trimEnd('/')
            .substringAfterLast('/')
            .substringAfterLast(':')
            .removeSuffix(".git")
            .let(::validateFolderName)
        val destination = File(installer.workspace, folderName)
        require(!destination.exists()) { "A folder named $folderName already exists" }

        installer.installIfNeeded { }
        val process = installer.startContainerProcess(
            installer.containerCommand("/usr/bin/git", "clone", "--", repositoryUrl, "/root/$folderName"),
            mergeError = true,
        )
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            destination.deleteRecursively()
            error(output.lineSequence().lastOrNull { it.isNotBlank() } ?: "Could not clone repository")
        }
        return register("/root/$folderName")
    }

    private fun write(projects: List<PandoraProject>) {
        registry.parentFile?.mkdirs()
        val data = JSONArray()
        projects.forEach { project ->
            data.put(
                JSONObject()
                    .put("path", project.path)
                    .put("name", project.customName ?: JSONObject.NULL)
                    .put("pinned", project.pinned),
            )
        }
        val temporary = File(registry.parentFile, "${registry.name}.tmp")
        temporary.writeText(data.toString())
        if (!temporary.renameTo(registry)) temporary.copyTo(registry, overwrite = true)
        temporary.delete()
    }

    private fun validateFolderName(value: String): String {
        val name = value.trim()
        require(name.isNotBlank()) { "Enter a folder name" }
        require(name != "." && name != ".." && '/' !in name && '\u0000' !in name) {
            "Use a folder name without slashes"
        }
        return name
    }

    private fun isProjectPath(path: String): Boolean =
        path.startsWith("/root/") && path.length > "/root/".length && "/../" !in "$path/"

    private companion object {
        val LOCK = Any()
    }
}
