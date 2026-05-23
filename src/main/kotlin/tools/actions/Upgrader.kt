/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.config.*
import tools.helpers.*
import java.util.logging.Logger

private val log = Logger.getLogger("Upgrader")

object Upgrader {
    fun upgrade(configPath: String, work: String, offline: Boolean) {
        val cfg = load(configPath)
        val imagesPath = cfg["waydroid"]?.get("images_path") ?: defaults["images_path"]!!
        val vendorType = cfg["waydroid"]?.get("vendor_type") ?: "MAINLINE"

        var status = "STOPPED"
        var session: Map<String, String>? = null

        if (java.io.File("${defaults["lxc"]}/waydroid").exists()) {
            status = Lxc.status(work)
        }

        if (status != "STOPPED") {
            log.info("Stopping container")
            try {
                val cm = DBusConnectionBuilder.forSystemBus().build()
                    .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
                session = cm.GetSession()
                cm.Stop(false)
            } catch (e: Exception) {
                log.fine(e.message)
                ContainerManager.stop(configPath, work, false)
            }
        }

        Drivers.loadBinderNodes(cfg)

        if (!offline) {
            if (imagesPath !in preinstalledImagesPaths) {
                Images.get(configPath, work)
            } else {
                log.info("Upgrade refused: pre-installed image at $imagesPath")
            }
        }

        Drivers.probeAshmemDriver(work)
        Lxc.setupHostPerms(work)
        Lxc.setLxcConfig(configPath, work)
        Lxc.makeBaseProps(configPath, work)

        if (status != "STOPPED" && session != null) {
            log.info("Starting container")
            try {
                DBusConnectionBuilder.forSystemBus().build()
                    .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
                    .Start(session)
            } catch (e: Exception) {
                log.severe("Failed to restart container. Please do so manually.")
            }
        }
    }
}
