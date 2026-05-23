/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("IClipboard")

const val ICLIPBOARD_INTERFACE = "lineageos.waydroid.IClipboard"
const val ICLIPBOARD_SERVICE = "waydroidclipboard"

private const val TRANSACTION_sendClipboardData = 1
private const val TRANSACTION_getClipboardData = 2

/**
 * Server-side binder service for IClipboard.
 * gbinder JVM binding required for real implementation.
 */
fun addClipboardService(
    binderDev: String,
    sendClipboardData: (String) -> Unit,
    getClipboardData: () -> String
) {
    log.warning("IClipboard binder service not implemented. A JVM/JNA gbinder binding is required.")
}
