/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("RunCore")

object RunCore {
    fun background(cmd: List<String>, workingDir: String? = null): Process {
        val proc = ProcessBuilder(cmd).apply {
            workingDir?.let { directory(File(it)) }
        }.start()
        Thread { proc.inputStream.bufferedReader().forEachLine { log.fine(it) } }.also { it.isDaemon = true }.start()
        Thread { proc.errorStream.bufferedReader().forEachLine { log.fine(it) } }.also { it.isDaemon = true }.start()
        return proc
    }

    fun core(
        logMessage: String,
        cmd: List<String>,
        workingDir: String? = null,
        output: String = "log",
        outputReturn: Boolean = false,
        check: Boolean? = null,
        sudo: Boolean = false
    ): Any {
        val validOutputs = setOf("log", "stdout", "interactive", "tui", "background", "pipe")
        require(output in validOutputs) { "Invalid output value: $output" }
        require(!(check != null && output == "background")) { "Can't use check with output: background" }
        require(!(outputReturn && output in listOf("tui", "background"))) { "Can't use output_return with output: $output" }

        log.fine(logMessage)

        if (output == "background") return background(cmd, workingDir)

        val pb = ProcessBuilder(cmd).apply {
            workingDir?.let { directory(File(it)) }
            redirectErrorStream(true)
        }
        val proc = pb.start()

        if (output == "tui") {
            val code = proc.waitFor()
            if (check != false && code != 0) throw RuntimeException("Command failed: $logMessage")
            return code
        }

        val buf = StringBuilder()
        proc.inputStream.bufferedReader().forEachLine { line ->
            log.fine(line)
            if (output in listOf("stdout", "interactive")) println(line)
            if (outputReturn) buf.appendLine(line)
        }
        val code = proc.waitFor()
        if (check != false && code != 0) throw RuntimeException("Command failed: $logMessage")
        return if (outputReturn) buf.toString() else code
    }
}
