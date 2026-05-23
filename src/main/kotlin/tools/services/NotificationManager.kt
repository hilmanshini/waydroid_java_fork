/*
 * Copyright 2025 Alessandro Astone
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.services

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.interfaces.*
import java.util.logging.Logger

private val log = Logger.getLogger("NotificationManager")

@Volatile private var stopping = false
private var thread: Thread? = null

private interface FdoNotifications : org.freedesktop.dbus.interfaces.DBusInterface {
    fun Notify(appName: String, replacesId: Int, appIcon: String, summary: String, body: String,
               actions: List<String>, hints: Map<String, Any>, expireTimeout: Int): Int
    fun CloseNotification(id: Int)
}

fun startNotificationManager(binderDev: String) {
    val conn = try {
        DBusConnectionBuilder.forSessionBus().build()
    } catch (e: Exception) {
        log.info("Skipping notification manager: could not connect to session bus: ${e.message}")
        return
    }

    val dbus = try {
        conn.getRemoteObject("org.freedesktop.Notifications", "/org/freedesktop/Notifications", FdoNotifications::class.java)
    } catch (e: Exception) {
        log.info("Skipping notification manager: ${e.message}")
        return
    }

    val listeners = mutableListOf<INotificationCallback>()
    val pendingTokens = mutableMapOf<Int, String>()

    fun registerListener(cb: INotificationCallback) {
        cb.addDeathHandler { listeners.remove(it) }
        listeners.add(cb)
    }

    fun notify(replacesId: Int, appName: String, packageName: String, summary: String, body: String,
               actions: List<NotificationAction>, imageData: ImageData?, category: String,
               suppressSound: Boolean, expireTimeout: Int, resident: Boolean, transient: Boolean, urgency: Int): Int {
        val actionsFlat = actions.flatMap { listOf(it.id, it.label) }
        val hints = mutableMapOf<String, Any>(
            "desktop-entry" to "waydroid.$packageName",
            "resident" to resident,
            "transient" to transient,
            "urgency" to urgency.toByte(),
            "suppress-sound" to suppressSound
        )
        if (category.isNotEmpty()) hints["category"] = category
        return try { dbus.Notify(appName, replacesId, "", summary, body, actionsFlat, hints, expireTimeout) }
        catch (e: Exception) { log.warning("Failed to post notification: ${e.message}"); ID_NONE }
    }

    fun closeNotification(id: Int) {
        try { dbus.CloseNotification(id) } catch (e: Exception) { log.warning("Failed to close notification: ${e.message}") }
    }

    stopping = false
    thread = Thread {
        while (!stopping) {
            addNotificationsService(binderDev, ::registerListener, ::notify, ::closeNotification)
        }
    }.also { it.isDaemon = true; it.start() }
}

fun stopNotificationManager() {
    stopping = true
}
