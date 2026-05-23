/*
 * Copyright 2025 Alessandro Astone
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("Bugreport")

const val TARBALL = "waydroid-bugreport.tar.xz"

object Bugreport {
    fun bugreport(waydroidBin: String) {
        val tmp = kotlin.io.path.createTempDirectory("waydroid-bugreport").toFile()

        println("""
The following information will be collected:
  - System kernel logs (kmsg)
  - Android system and user logs (logcat)
  - Waydroid container manager logs (/var/lib/waydroid/waydroid.log)
  - Waydroid configuration files (/var/lib/waydroid/*)

Please authenticate as administrator in order to read system logs.
""".trimIndent())

        try {
            ProcessBuilder("sudo", "-v").inheritIO().start().waitFor().also {
                if (it != 0) { println("Authentication failed or was cancelled."); return }
            }
        } catch (e: Exception) {
            println("The 'sudo' command is not available."); return
        }

        val logfiles = mutableListOf<File>()
        fun logfile(name: String) = File(tmp, name).also { logfiles.add(it) }

        var session: Map<String, String>? = null
        runCatching {
            session = DBusConnectionBuilder.forSystemBus().build()
                .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
                .GetSession()
        }

        val procs = mutableListOf<Process>()

        if (session == null) {
            println("Waydroid session not found. Trying to start one...")
            procs.add(ProcessBuilder("waydroid", "session", "start")
                .redirectOutput(logfile("session.txt")).start())
            sleepProgress(10)
            runCatching {
                session = DBusConnectionBuilder.forSystemBus().build()
                    .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
                    .GetSession()
            }
        }

        if (session != null) {
            println("\nPlease try to reproduce the problem now.\nWaydroid will collect logs for up to 5 minutes.\n")
            procs.add(ProcessBuilder("sudo", waydroidBin, "logcat").redirectOutput(logfile("logcat.txt")).start())
            procs.add(ProcessBuilder("sudo", "dmesg", "-T", "-w").redirectOutput(logfile("dmesg.txt")).start())
            sleepProgress(5 * 60)
        } else {
            println("Session did not start\n")
            procs.add(ProcessBuilder("sudo", "dmesg", "-T").redirectOutput(logfile("dmesg.txt")).start())
        }

        procs.forEach { it.destroy() }
        procs.forEach { it.waitFor() }

        println("Creating archive...")
        val files = listOf(
            "/var/lib/waydroid/waydroid.log", "/var/lib/waydroid/waydroid.cfg",
            "/var/lib/waydroid/waydroid_base.prop", "/var/lib/waydroid/waydroid.prop",
            "/var/lib/waydroid/lxc"
        ).map { File(it) } + logfiles

        ProcessBuilder("tar", "-cJf", TARBALL, *files.filter { it.exists() }.map { it.absolutePath }.toTypedArray())
            .start().waitFor()

        tmp.deleteRecursively()
        println("Created \u001B[1m$TARBALL\u001B[0m")
    }

    private fun sleepProgress(seconds: Int) {
        val bar = 50
        val step = 0.1
        var elapsed = 0.0
        try {
            while (elapsed < seconds) {
                val filled = (bar * elapsed / seconds).toInt()
                print("\r[${"=".repeat(filled)}${" ".repeat(bar - filled)}]\r")
                Thread.sleep((step * 1000).toLong())
                elapsed += step
            }
        } catch (_: InterruptedException) {}
        print("\r${" ".repeat(bar + 2)}\r")
    }
}
