/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File

object Props {
    private fun which(cmd: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, cmd).canExecute() }

    fun hostGet(args: Any, prop: String): String {
        if (!which("getprop")) return ""
        return ProcessBuilder("getprop", prop).start()
            .inputStream.bufferedReader().readText().trim()
    }

    fun hostSet(args: Any, prop: String, value: String) {
        if (which("setprop")) Run.user(args, listOf("setprop", prop, value))
    }

    fun fileGet(args: Any, file: String, prop: String): String =
        File(file).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .firstOrNull { val eq = it.indexOf('='); eq > 0 && it.substring(0, eq) == prop }
                ?.let { it.substring(it.indexOf('=') + 1) } ?: ""
        }
}
