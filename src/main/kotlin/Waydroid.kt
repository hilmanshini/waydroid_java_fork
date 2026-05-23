/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.sun.jna.Library
import com.sun.jna.Native
import tools.actions.*
import tools.config.*
import tools.helpers.*
import java.io.File
import java.util.logging.Logger
import kotlin.system.exitProcess

private val log = Logger.getLogger("Waydroid")

private interface LibC : Library {
    fun umask(mask: Int): Int
}

fun main(args: Array<String>) {
    Native.load("c", LibC::class.java).umask(0b000_010_010) // 0o022

    val parsed = ParsedArgs(action = "init", detailsToStdout = true)

    val work = defaults["work"]!!
    val configPath = "$work/waydroid.cfg"
    val logPath = if (File(work).exists()) "$work/waydroid.log" else "/tmp/waydroid.log"

    Logging.init(parsed.quiet, parsed.verbose, parsed.detailsToStdout, logPath, parsed.action)

    val action = parsed.action ?: "first-launch"

    try {
        if (!Initializer.isInitialized(configPath) &&
            action !in setOf("init", "container", "first-launch", "log", "bugreport")) {
            println("Waydroid is not initialized, run \"waydroid init\"")
            exitProcess(0)
        }

        val cfg = load(configPath)
        val binderDev = cfg["waydroid"]?.get("binder") ?: ""

        when (action) {
            "init" -> {
                requireRoot(action)
                val wArgs = WaydroidArgs(
                    imagesPath = parsed.imagesPath,
                    force = parsed.force,
                    systemChannel = parsed.systemChannel,
                    vendorChannel = parsed.vendorChannel,
                    romType = parsed.romType,
                    systemType = parsed.systemType
                )
                if (parsed.client) {
                    log.info("Remote init client not yet implemented in Kotlin port")
                } else {
                    Initializer.init(configPath, work, wArgs)
                }
            }
            "upgrade" -> {
                requireRoot(action)
                Upgrader.upgrade(configPath, work, parsed.offline)
            }
            "session" -> when (parsed.subaction) {
                "start" -> SessionManager.start(binderDev)
                "stop"  -> SessionManager.stop()
                else    -> log.info("Run waydroid session -h for usage information.")
            }
            "container" -> {
                requireRoot(action)
                when (parsed.subaction) {
                    "start"    -> ContainerManager.start(configPath, work)
                    "stop"     -> ContainerManager.stop(configPath, work)
                    "restart"  -> ContainerManager.restart(work)
                    "freeze"   -> ContainerManager.freeze(work)
                    "unfreeze" -> ContainerManager.unfreeze(work)
                    else       -> log.info("Run waydroid container -h for usage information.")
                }
            }
            "app" -> when (parsed.subaction) {
                "install" -> AppManager.install(binderDev, parsed.packageArg)
                "remove"  -> AppManager.remove(binderDev, parsed.packageArg)
                "launch"  -> AppManager.launch(binderDev, parsed.packageArg)
                "intent"  -> AppManager.intent(binderDev, parsed.actionArg, parsed.uriArg)
                "list"    -> AppManager.list(binderDev)
                else      -> log.info("Run waydroid app -h for usage information.")
            }
            "prop" -> when (parsed.subaction) {
                "get" -> PropAction.get(binderDev, parsed.key)
                "set" -> PropAction.set(binderDev, parsed.key, parsed.value)
                else  -> log.info("Run waydroid prop -h for usage information.")
            }
            "shell" -> {
                requireRoot(action)
                Lxc.shell(work, parsed.uid, parsed.gid, parsed.context,
                    parsed.nolsm, parsed.allcaps, parsed.nocgroup, parsed.command)
            }
            "logcat" -> {
                requireRoot(action)
                Lxc.logcat(work, parsed.logcatArgs)
            }
            "show-full-ui"   -> AppManager.showFullUI(binderDev)
            "first-launch"   -> {
                if (!Initializer.isInitialized(configPath)) {
                    log.info("Remote init client not yet implemented in Kotlin port")
                }
                if (Initializer.isInitialized(configPath)) AppManager.showFullUI(binderDev)
            }
            "status"         -> Status.printStatus(configPath, work)
            "adb" -> when (parsed.subaction) {
                "connect"    -> Net.adbConnect(work, binderDev)
                "disconnect" -> Net.adbDisconnect(work)
                else         -> log.info("Run waydroid adb -h for usage information.")
            }
            "log" -> {
                if (parsed.clearLog) Run.user(Any(), listOf("truncate", "-s", "0", logPath))
                try {
                    Run.user(Any(), listOf("tail", "-n", parsed.lines, "-F", logPath), output = "tui")
                } catch (_: Exception) {}
            }
            "bugreport" -> Bugreport.bugreport(args.firstOrNull() ?: "waydroid")
            else -> log.info("Run waydroid -h for usage information.")
        }
    } catch (e: Exception) {
        log.severe("ERROR: ${e.message}")
        log.fine(e.stackTraceToString())
        if (!parsed.detailsToStdout) {
            println("Use '--details-to-stdout' to get more details:\n  waydroid --details-to-stdout ${args.joinToString(" ")}")
        }
        exitProcess(1)
    }
}

private fun requireRoot(action: String) {
    if (ProcessHandle.current().pid() != 0L &&
        runCatching { Runtime.getRuntime().exec(arrayOf("id", "-u")).inputStream.bufferedReader().readText().trim() != "0" }.getOrElse { true }) {
        // Best-effort check; real uid check requires JNA or /proc/self/status
        log.fine("Action \"$action\" may need root access")
    }
}
