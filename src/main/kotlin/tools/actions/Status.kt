/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.config.load
import tools.helpers.Net

object Status {
    fun printStatus(configPath: String, work: String) {
        val cfg = load(configPath)
        val vendorType = cfg["waydroid"]?.get("vendor_type") ?: "UNKNOWN"

        fun printStopped() {
            println("Session:\tSTOPPED")
            println("Vendor type:\t$vendorType")
        }

        try {
            val conn = DBusConnectionBuilder.forSystemBus().build()
            val cm = conn.getRemoteObject("id.waydro.Container", "/ContainerManager",
                IContainerManagerDbus::class.java)
            val session = cm.GetSession()
            if (session.isNotEmpty()) {
                println("Session:\tRUNNING")
                println("Container:\t${session["state"]}")
                println("Vendor type:\t$vendorType")
                println("IP address:\t${Net.getDeviceIpAddress() ?: "UNKNOWN"}")
                println("Session user:\t${session["user_name"]}(${session["user_id"]})")
                println("Wayland display:\t${session["wayland_display"]}")
            } else {
                printStopped()
            }
        } catch (e: Exception) {
            printStopped()
        }
    }
}
