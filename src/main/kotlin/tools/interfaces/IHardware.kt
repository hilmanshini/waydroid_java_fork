/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("IHardware")

const val IHARDWARE_INTERFACE = "lineageos.waydroid.IHardware"
const val IHARDWARE_SERVICE = "waydroidhardware"

private const val TRANSACTION_enableNFC = 1
private const val TRANSACTION_enableBluetooth = 2
private const val TRANSACTION_suspend = 3
private const val TRANSACTION_reboot = 4
private const val TRANSACTION_upgrade = 5
private const val TRANSACTION_upgrade2 = 6
private const val TRANSACTION_shutdownRequest = 7

/**
 * Server-side binder service for IHardware.
 * Registers callbacks that Android calls into the host.
 * gbinder JVM binding required for real implementation.
 */
fun addHardwareService(
    binderDev: String,
    enableNFC: (Boolean) -> Int,
    enableBluetooth: (Boolean) -> Int,
    suspend: () -> Unit,
    reboot: () -> Unit,
    upgrade: (String, Long, String, Long) -> Unit,
    shutdownRequest: (String) -> Unit
) {
    // Placeholder: real implementation needs a JVM gbinder binding.
    log.warning("IHardware binder service not implemented. A JVM/JNA gbinder binding is required.")
}
