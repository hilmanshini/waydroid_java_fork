/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.services

import tools.config.load
import tools.helpers.Images
import tools.helpers.Lxc
import tools.helpers.Protocol
import tools.interfaces.addHardwareService
import java.util.logging.Logger

private val log = Logger.getLogger("HardwareManager")

@Volatile private var stopping = false
private var thread: Thread? = null

fun startHardwareManager(
    configPath: String,
    work: String,
    binderDev: String,
    imagesPath: String,
    session: MutableMap<String, String>
) {
    fun enableNFC(enable: Boolean): Int { log.fine("enableNFC not implemented"); return 0 }
    fun enableBluetooth(enable: Boolean): Int { log.fine("enableBluetooth not implemented"); return 0 }

    fun suspend() {
        val cfg = load(configPath)
        if (cfg["waydroid"]?.get("suspend_action") == "stop") {
            // session stop handled by container manager
        } else {
            Lxc.freeze(work)
        }
    }

    fun reboot() {
        Lxc.stop(work)
        Lxc.start(work)
    }

    fun upgrade(systemZip: String, systemTime: Long, vendorZip: String, vendorTime: Long) {
        Lxc.stop(work)
        Images.umountRootfs(work)
        Images.replace(configPath, work, systemZip, systemTime, vendorZip, vendorTime)
        session["background_start"] = "false"
        Images.mountRootfs(work, imagesPath, session)
        Protocol.setAidlVersion(configPath, work, load(configPath))
        Lxc.start(work)
    }

    fun shutdownRequest(reason: String) {
        val isReboot = reason.startsWith("1")
        var tries = 0
        while (Lxc.status(work) != "STOPPED") {
            if (tries >= 30) { log.fine("Android still not stopped after $tries s"); return }
            log.fine("Waiting for Android to shutdown")
            Thread.sleep(1000)
            tries++
        }
        if (isReboot) Lxc.start(work)
        // else container stop handled externally
    }

    stopping = false
    thread = Thread {
        while (!stopping) {
            addHardwareService(binderDev, ::enableNFC, ::enableBluetooth, ::suspend, ::reboot, ::upgrade, ::shutdownRequest)
        }
    }.also { it.isDaemon = true; it.start() }
}

fun stopHardwareManager() {
    stopping = true
}
