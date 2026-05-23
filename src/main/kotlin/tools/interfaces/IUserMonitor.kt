/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("IUserMonitor")

const val IUSERMONITOR_INTERFACE = "lineageos.waydroid.IUserMonitor"
const val IUSERMONITOR_SERVICE = "waydroidusermonitor"

const val PACKAGE_ADDED = 0
const val PACKAGE_REMOVED = 1
const val PACKAGE_UPDATED = 2

/**
 * Server-side binder service for IUserMonitor.
 * gbinder JVM binding required for real implementation.
 */
fun addUserMonitorService(
    binderDev: String,
    userUnlocked: (Int) -> Unit,
    packageStateChanged: (Int, String, Int) -> Unit
) {
    log.warning("IUserMonitor binder service not implemented. A JVM/JNA gbinder binding is required.")
}
