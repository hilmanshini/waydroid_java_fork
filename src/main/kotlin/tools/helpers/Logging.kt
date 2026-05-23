/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File
import java.util.logging.*

object Logging {
    /** Custom handler that writes INFO+ to stdout, respecting quiet/verbose flags. */
    class StdoutHandler(private val quiet: Boolean, private val detailsToStdout: Boolean) : StreamHandler(System.out, SimpleFormatter()) {
        override fun publish(record: LogRecord) {
            if (quiet) return
            if (record.level.intValue() < Level.INFO.intValue() && !detailsToStdout) return
            super.publish(record)
            flush()
        }
    }

    fun init(quiet: Boolean, verbose: Boolean, detailsToStdout: Boolean, logFile: String?, action: String?) {
        val root = Logger.getLogger("")
        root.handlers.forEach { root.removeHandler(it) }

        root.level = if (verbose) Level.FINEST else Level.FINE

        val fmt = SimpleFormatter()
        val stdoutHandler = StdoutHandler(quiet, detailsToStdout)
        stdoutHandler.formatter = object : Formatter() {
            override fun format(r: LogRecord) = "[${r.millis.let {
                java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(it))
            }}] ${r.message}\n"
        }
        root.addHandler(stdoutHandler)

        if (logFile != null && action == "container" && !detailsToStdout) {
            runCatching { File(logFile).setReadable(true, false) }
            val fileHandler = FileHandler(logFile, 5 * 1024 * 1024, 1, true)
            fileHandler.formatter = object : Formatter() {
                override fun format(r: LogRecord) = "(${ProcessHandle.current().pid()}) [${
                    java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss").format(java.util.Date(r.millis))
                }] ${r.message}\n"
            }
            root.addHandler(fileHandler)
        }
    }

    fun disable() { Logger.getLogger("").isLoggable(Level.OFF) }

    /** Decorator: logs exceptions before re-throwing. */
    fun <T> logExceptions(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Logger.getLogger("").severe(e.stackTraceToString())
        throw e
    }
}
