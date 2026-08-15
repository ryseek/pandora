package com.pandora.mobile.linux

import android.content.Context
import java.io.File
import java.net.URI
import java.util.UUID

/** Installs Agent Skills from a Git repository into Codex's global skill directory. */
class SkillRepositoryInstaller(context: Context) {
    private val installer = RootfsInstaller(context.applicationContext)

    fun install(repositoryUrl: String, onStatus: (String) -> Unit = {}): List<String> {
        val url = validateRepositoryUrl(repositoryUrl)
        installer.installIfNeeded(onStatus)
        val downloadName = "skills-${UUID.randomUUID()}"
        val hostCheckout = File(installer.workspace, ".pandora/skill-downloads/$downloadName")
        val containerCheckout = "/root/.pandora/skill-downloads/$downloadName"
        hostCheckout.parentFile?.mkdirs()
        try {
            onStatus("Downloading skills…")
            val process = installer.startContainerProcess(
                installer.containerCommand(
                    "/usr/bin/git", "clone", "--depth", "1", "--single-branch", url, containerCheckout,
                ),
                mergeError = true,
            )
            val output = process.inputStream.bufferedReader().readText().takeLast(4_000)
            check(process.waitFor() == 0) { output.ifBlank { "Could not download the skill repository" } }

            val skillDirectories = discoverSkillDirectories(hostCheckout)
            check(skillDirectories.isNotEmpty()) { "This repository does not contain any SKILL.md files" }
            val installed = skillDirectories.map { source ->
                val name = source.name
                require(SKILL_NAME.matches(name)) { "Invalid skill folder name: $name" }
                val destination = File(installer.workspace, ".codex/skills/$name")
                destination.mkdirs()
                check(source.copyRecursively(destination, overwrite = true)) { "Could not install $name" }
                name
            }.distinct().sorted()
            onStatus("Installed ${installed.size} skill${if (installed.size == 1) "" else "s"}")
            return installed
        } finally {
            hostCheckout.deleteRecursively()
        }
    }
}

internal fun validateRepositoryUrl(value: String): String {
    val url = value.trim()
    val uri = runCatching { URI(url) }.getOrNull()
    require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
        "Enter an HTTPS Git repository URL"
    }
    return url
}

internal fun discoverSkillDirectories(root: File): List<File> {
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown()
        .maxDepth(5)
        .filter { it.isFile && it.name == "SKILL.md" }
        .mapNotNull { it.parentFile }
        .distinctBy { it.canonicalPath }
        .toList()
}

private val SKILL_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
