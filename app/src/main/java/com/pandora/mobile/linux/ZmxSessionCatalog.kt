package com.pandora.mobile.linux

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

class ZmxSessionCatalog(context: Context) {
    private val installer = RootfsInstaller(context.applicationContext)

    fun read(): List<String> {
        val zmx = File(installer.workspace, ".local/bin/zmx")
        if (!zmx.isFile || !installer.rootfs.isDirectory) return emptyList()
        val process = installer.startContainerProcess(
            installer.containerCommand("/root/.local/bin/zmx", "list", "--short"),
            mergeError = true,
        )
        return try {
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) emptyList()
            else process.inputStream.bufferedReader().readLines().map(String::trim).filter(String::isNotBlank)
        } finally {
            if (process.isAlive) installer.terminateProcessTree(process)
        }
    }
}
