/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.services

import tools.interfaces.addClipboardService
import java.util.logging.Logger

private val log = Logger.getLogger("ClipboardManager")

@Volatile private var stopping = false
private var thread: Thread? = null

/**
 * Clipboard integration between Android and the host.
 * Requires a host clipboard implementation (e.g. xclip/wl-clipboard via subprocess).
 */
fun startClipboardManager(binderDev: String) {
    fun sendClipboardData(value: String) {
        runCatching {
            ProcessBuilder("wl-copy", value).start().waitFor()
        }.onFailure { log.fine("sendClipboardData failed: ${it.message}") }
    }

    fun getClipboardData(): String = runCatching {
        ProcessBuilder("wl-paste").start().inputStream.bufferedReader().readText().trim()
    }.getOrElse { log.fine("getClipboardData failed: ${it.message}"); "" }

    stopping = false
    thread = Thread {
        while (!stopping) {
            addClipboardService(binderDev, ::sendClipboardData, ::getClipboardData)
        }
    }.also { it.isDaemon = true; it.start() }
}

fun stopClipboardManager() {
    stopping = true
}
