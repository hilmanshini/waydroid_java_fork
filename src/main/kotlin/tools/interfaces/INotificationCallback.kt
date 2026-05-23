/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("INotificationCallback")

const val INOTIFICATION_CALLBACK_INTERFACE = "lineageos.waydroid.INotifications.INotificationCallback"

private const val TRANSACTION_onActionInvoked = 1

/**
 * Client-side proxy for INotificationCallback binder object.
 * gbinder JVM binding required for real implementation.
 */
class INotificationCallback(private val remote: Any) {
    private val deathHandlers = mutableListOf<(INotificationCallback) -> Unit>()

    fun addDeathHandler(handler: (INotificationCallback) -> Unit) {
        deathHandlers.add(handler)
    }

    fun onActionInvoked(notificationId: Int, actionId: String, xdgActivationToken: String) {
        log.warning("INotificationCallback.onActionInvoked: binder not implemented")
    }
}
