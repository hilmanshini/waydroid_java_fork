/*
 * Copyright 2023 Maximilian Wende
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("Net")

object Net {
    fun adbConnect(work: String, binderDev: String) {
        if (!which("adb")) throw RuntimeException("Could not find adb")
        Run.user(Any(), listOf("adb", "start-server"))
        val ip = getDeviceIpAddress() ?: throw RuntimeException("Unknown container IP address. Is Waydroid running?")
        Run.user(Any(), listOf("adb", "connect", ip))
        log.info("Established ADB connection to Waydroid device at $ip.")
    }

    fun adbDisconnect(work: String) {
        if (!which("adb")) throw RuntimeException("Could not find adb")
        val ip = getDeviceIpAddress() ?: throw RuntimeException("Unknown container IP address.")
        Run.user(Any(), listOf("adb", "disconnect", ip))
    }

    fun getDeviceIpAddress(): String? {
        return runCatching {
            val text = File("/var/lib/misc/dnsmasq.waydroid0.leases").readText()
            Regex("""(\d{1,3}\.){3}\d{1,3}\s""").find(text)?.value?.trim()
        }.getOrNull()
    }

    private fun which(cmd: String) = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .any { File(it, cmd).canExecute() }
}
