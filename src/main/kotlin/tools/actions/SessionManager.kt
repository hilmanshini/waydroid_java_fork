/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import tools.config.*
import tools.helpers.Props
import tools.services.*
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("SessionManager")

object SessionManager {
    private fun systemConn() = DBusConnectionBuilder.forSystemBus().build()
    private fun sessionConn() = DBusConnectionBuilder.forSessionBus().build()

    fun start(binderDev: String, unlockedCb: (() -> Unit)? = null, background: Boolean = true) {
        val session = sessionDefaults()

        var waylandDisplay = session["wayland_display"] ?: "wayland-1"
        if (waylandDisplay == "null" || waylandDisplay.isEmpty()) waylandDisplay = "wayland-1"
        session["wayland_display"] = waylandDisplay

        val xdgRuntimeDir = session["xdg_runtime_dir"] ?: ""
        val waylandSocket = if (File(waylandDisplay).isAbsolute) waylandDisplay
                           else File(xdgRuntimeDir, waylandDisplay).absolutePath
        if (!File(waylandSocket).exists()) {
            log.severe("Wayland socket '$waylandSocket' doesn't exist; are you running a Wayland compositor?")
            return
        }

        val waydroidData = session["waydroid_data"]!!
        File(waydroidData).mkdirs()

        var dpi = Props.hostGet(Any(), "ro.sf.lcd_density")
        if (dpi.isEmpty()) dpi = System.getenv("GRID_UNIT_PX")?.let { (it.toIntOrNull() ?: 0) * 20 }?.toString() ?: "0"
        session["lcd_density"] = dpi
        session["background_start"] = if (background) "true" else "false"

        try {
            val cm = systemConn().getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
            cm.Start(session)
        } catch (e: Exception) {
            log.severe("WayDroid container is not listening: ${e.message}")
            return
        }

        startUserManager("", "", binderDev, session, unlockedCb)
        startClipboardManager(binderDev)
        startNotificationManager(binderDev)
    }

    fun stop() {
        try {
            sessionConn().getRemoteObject("id.waydro.Session", "/SessionManager", ISessionManagerDbus::class.java).Stop()
        } catch (e: Exception) {
            stopContainer(quitSession = true)
        }
    }

    fun doStop() {
        stopUserManager()
        stopClipboardManager()
        stopNotificationManager()
    }

    fun stopContainer(quitSession: Boolean) {
        runCatching {
            DBusConnectionBuilder.forSystemBus().build()
                .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
                .Stop(quitSession)
        }
    }
}
