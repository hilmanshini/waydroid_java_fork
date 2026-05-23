/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import tools.config.VERSION

/** Parsed command-line arguments. */
data class ParsedArgs(
    val log: String? = null,
    val detailsToStdout: Boolean = false,
    val verbose: Boolean = false,
    val quiet: Boolean = false,
    val action: String? = null,
    val subaction: String? = null,
    // init
    val imagesPath: String = "",
    val force: Boolean = false,
    val systemChannel: String = "",
    val vendorChannel: String = "",
    val romType: String = "",
    val systemType: String = "",
    val client: Boolean = false,
    // upgrade
    val offline: Boolean = false,
    // log
    val lines: String = "60",
    val clearLog: Boolean = false,
    // app
    val packageArg: String = "",
    val actionArg: String = "",
    val uriArg: String = "",
    // prop
    val key: String = "",
    val value: String = "",
    // shell
    val uid: String? = null,
    val gid: String? = null,
    val context: String? = null,
    val nolsm: Boolean = false,
    val allcaps: Boolean = false,
    val nocgroup: Boolean = false,
    val command: List<String> = emptyList(),
    // logcat
    val logcatArgs: List<String> = emptyList()
)

object Arguments {
    fun parse(argv: Array<String>): ParsedArgs {
        val args = argv.toMutableList()
        var log: String? = null
        var detailsToStdout = false
        var verbose = false
        var quiet = false

        fun consume(flag: String): Boolean = args.remove(flag)
        fun consumeValue(flag: String): String? {
            val i = args.indexOf(flag)
            return if (i >= 0 && i + 1 < args.size) { args.removeAt(i); args.removeAt(i) } else null
        }

        if (consume("--version") || consume("-V")) { println("waydroid $VERSION"); return ParsedArgs() }
        log = consumeValue("--log") ?: consumeValue("-l")
        detailsToStdout = consume("--details-to-stdout")
        verbose = consume("--verbose") || consume("-v")
        quiet = consume("--quiet") || consume("-q")

        val action = args.removeFirstOrNull()
        val subaction = if (action in listOf("session","container","app","prop","adb")) args.removeFirstOrNull() else null

        return when (action) {
            "init" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action,
                imagesPath = consumeValue("-i") ?: consumeValue("--images_path") ?: "",
                force = consume("-f") || consume("--force"),
                systemChannel = consumeValue("-c") ?: consumeValue("--system_channel") ?: "",
                vendorChannel = consumeValue("-v") ?: consumeValue("--vendor_channel") ?: "",
                romType = consumeValue("-r") ?: consumeValue("--rom_type") ?: "",
                systemType = consumeValue("-s") ?: consumeValue("--system_type") ?: "",
                client = consume("--client"))
            "upgrade" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action,
                offline = consume("-o") || consume("--offline"))
            "log" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action,
                lines = consumeValue("-n") ?: consumeValue("--lines") ?: "60",
                clearLog = consume("-c") || consume("--clear"))
            "app" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action, subaction,
                packageArg = args.removeFirstOrNull() ?: "",
                actionArg = if (subaction == "intent") args.removeFirstOrNull() ?: "" else "",
                uriArg = if (subaction == "intent") args.removeFirstOrNull() ?: "" else "")
            "prop" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action, subaction,
                key = args.removeFirstOrNull() ?: "",
                value = args.removeFirstOrNull() ?: "")
            "shell" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action,
                uid = consumeValue("-u") ?: consumeValue("--uid"),
                gid = consumeValue("-g") ?: consumeValue("--gid"),
                context = consumeValue("-s") ?: consumeValue("--context"),
                nolsm = consume("-L") || consume("--nolsm"),
                allcaps = consume("-C") || consume("--allcaps"),
                nocgroup = consume("-G") || consume("--nocgroup"),
                command = args.toList())
            "logcat" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action, logcatArgs = args.toList())
            "adb" -> ParsedArgs(log, detailsToStdout, verbose, quiet, action, subaction)
            else -> ParsedArgs(log, detailsToStdout, verbose, quiet, action, subaction)
        }
    }
}
