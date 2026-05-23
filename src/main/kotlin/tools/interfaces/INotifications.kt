/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("INotifications")

const val INOTIFICATIONS_INTERFACE = "lineageos.waydroid.INotifications"
const val INOTIFICATIONS_SERVICE = "waydroidnotifications"

const val ID_NONE = 0

object Urgency {
    const val LOW = 0
    const val NORMAL = 1
    const val CRITICAL = 2
}

data class NotificationAction(val id: String, val label: String)

data class ImageData(
    val width: Int,
    val height: Int,
    val rowstride: Int,
    val hasAlpha: Boolean,
    val data: ByteArray
)

/**
 * Server-side binder service for INotifications.
 * gbinder JVM binding required for real implementation.
 */
fun addNotificationsService(
    binderDev: String,
    registerListener: (INotificationCallback) -> Unit,
    notify: (Int, String, String, String, String, List<NotificationAction>, ImageData?, String, Boolean, Int, Boolean, Boolean, Int) -> Int,
    closeNotification: (Int) -> Unit
) {
    log.warning("INotifications binder service not implemented. A JVM/JNA gbinder binding is required.")
}
