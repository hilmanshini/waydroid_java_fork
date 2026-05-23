/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import tools.config.*
import tools.helpers.*
import tools.services.*
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("ContainerManager")

object ContainerManager {
    private fun systemConn() = DBusConnectionBuilder.forSystemBus().build()

    fun start(configPath: String, work: String) {
        // Registers D-Bus service and runs the main loop — handled by Waydroid.kt
        log.info("ContainerManager.start: D-Bus service registration handled by main entry point")
    }

    fun doStart(configPath: String, work: String, session: MutableMap<String, String>) {
        if (!Initializer.isInitialized(configPath)) throw RuntimeException("Waydroid is not initialized")

        val cfg = load(configPath)
        val binderDev = cfg["waydroid"]?.get("binder") ?: throw RuntimeException("binder not configured")
        val vndBinderDev = cfg["waydroid"]?.get("vndbinder") ?: ""
        val hwBinderDev = cfg["waydroid"]?.get("hwbinder") ?: ""

        log.info("Starting up container for a new session")

        // Networking
        Run.user(Any(), listOf("$TOOLS_SRC/data/scripts/waydroid-net.sh", "start"))

        // Sensors
        if (which("waydroid-sensord")) Run.user(Any(), listOf("waydroid-sensord", "/dev/$hwBinderDev"), output = "background")

        // Cgroup hacks
        if (which("start")) Run.user(Any(), listOf("start", "cgroup-lite"), check = false)

        // NFC hacks
        if (which("stop")) Run.user(Any(), listOf("stop", "nfcd"), check = false)

        setPermissions(work, listOf("/dev/$binderDev", "/dev/$vndBinderDev", "/dev/$hwBinderDev"), "666")
        setPermissions(work)

        Lxc.generateSessionLxcConfig(configPath, work, session)
        Images.mountRootfs(work, cfg["waydroid"]?.get("images_path") ?: defaults["images_path"]!!, session)
        Protocol.setAidlVersion(configPath, work, cfg)
        Lxc.start(work)
        startHardwareManager(configPath, work, binderDev, cfg["waydroid"]?.get("images_path") ?: "", session)
    }

    fun stop(configPath: String, work: String, quitSession: Boolean = true) {
        if (!Initializer.isInitialized(configPath)) throw RuntimeException("Waydroid is not initialized")
        log.info("Stopping container")
        runCatching { stopHardwareManager() }
        if (Lxc.status(work) != "STOPPED") {
            Lxc.stop(work)
            while (Lxc.status(work) != "STOPPED") Thread.sleep(100)
        }
        Run.user(Any(), listOf("$TOOLS_SRC/data/scripts/waydroid-net.sh", "stop"), check = false)
        if (which("start")) Run.user(Any(), listOf("start", "nfcd"), check = false)
        Images.umountRootfs(work)
    }

    fun restart(work: String) {
        val status = Lxc.status(work)
        if (status == "RUNNING") { Lxc.stop(work); Lxc.start(work) }
        else log.severe("WayDroid container is $status")
    }

    fun freeze(work: String) {
        val status = Lxc.status(work)
        if (status == "RUNNING") { Lxc.freeze(work); while (Lxc.status(work) == "RUNNING") Thread.sleep(100) }
        else log.severe("WayDroid container is $status")
    }

    fun unfreeze(work: String) {
        val status = Lxc.status(work)
        if (status == "FROZEN") { Lxc.unfreeze(work); while (Lxc.status(work) == "FROZEN") Thread.sleep(100) }
        else log.severe("WayDroid container is $status")
    }

    private fun setPermissions(work: String, paths: List<String>? = null, mode: String = "777") {
        val list = paths ?: buildList {
            addAll(listOf("/dev/ashmem", "/dev/sw_sync", "/dev/graphics", "/dev/pvr_sync", "/dev/ion",
                "/dev/Vcodec", "/dev/MTK_SMI", "/dev/mdp_sync", "/dev/mtk_cmdq"))
            addAll(File("/dev/dri").listFiles { f -> f.name.startsWith("renderD") }?.map { it.absolutePath } ?: emptyList())
            addAll(File("/dev").listFiles { f -> f.name.startsWith("fb") }?.map { it.absolutePath } ?: emptyList())
            addAll(File("/dev").listFiles { f -> f.name.startsWith("video") }?.map { it.absolutePath } ?: emptyList())
        }
        list.forEach { path ->
            if (File(path).exists()) Run.user(Any(), listOf("chmod", mode, "-R", path), check = false)
        }
    }

    private fun which(cmd: String) = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .any { File(it, cmd).canExecute() }
}
