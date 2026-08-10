package com.pandora.mobile.linux

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBackupTest {
    @Test
    fun roundTripReplacesWorkspaceAndPreservesMetadata() {
        val root = Files.createTempDirectory("pandora-backup-test").toFile()
        try {
            val filesDir = root.resolve("files").apply { mkdirs() }
            val cacheDir = root.resolve("cache").apply { mkdirs() }
            val workspace = filesDir.resolve("linux-workspace").toPath().createDirectories()
            val project = workspace.resolve("project").createDirectories()
            project.resolve("hello.txt").writeText("hello from Pandora")
            val script = project.resolve("run.sh").createFile()
            script.writeText("#!/bin/sh\necho ok\n")
            script.toFile().setExecutable(true)
            Files.createSymbolicLink(project.resolve("hello-link"), project.fileSystem.getPath("hello.txt"))

            val archive = ByteArrayOutputStream()
            val backup = WorkspaceBackup(filesDir, cacheDir)
            backup.write(archive)

            filesDir.resolve("linux-workspace").deleteRecursively()
            filesDir.resolve("linux-workspace").mkdirs()
            filesDir.resolve("linux-workspace/stale.txt").writeText("remove me")
            backup.restore(ByteArrayInputStream(archive.toByteArray()))

            val restored = filesDir.resolve("linux-workspace/project")
            assertEquals("hello from Pandora", restored.resolve("hello.txt").readText())
            assertTrue(restored.resolve("run.sh").canExecute())
            assertTrue(Files.isSymbolicLink(restored.resolve("hello-link").toPath()))
            assertEquals("hello.txt", Files.readSymbolicLink(restored.resolve("hello-link").toPath()).toString())
            assertFalse(filesDir.resolve("linux-workspace/stale.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
