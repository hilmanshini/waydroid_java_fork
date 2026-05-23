/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("IStatusBarService")

const val ISTATUSBAR_INTERFACE = "com.android.internal.statusbar.IStatusBarService"
const val ISTATUSBAR_SERVICE = "statusbar"

private const val TRANSACTION_expand = 1
private const val TRANSACTION_collapse = 2

/**
 * Client-side proxy for IStatusBarService binder.
 * gbinder JVM binding required for real implementation.
 */
class IStatusBarService(private val binderDev: String) {
    fun expand() { log.warning("IStatusBarService.expand: binder not implemented") }
    fun collapse() { log.warning("IStatusBarService.collapse: binder not implemented") }

    companion object {
        fun getService(binderDev: String): IStatusBarService? = try {
            IStatusBarService(binderDev)
        } catch (e: Exception) {
            log.severe("Failed to get IStatusBarService: ${e.message}")
            null
        }
    }
}
