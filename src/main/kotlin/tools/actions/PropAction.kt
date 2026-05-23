/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.helpers.Props
import java.util.logging.Logger

private val log = Logger.getLogger("PropAction")

object PropAction {
    private fun withSession(block: (cm: IContainerManagerDbus) -> Unit) {
        try {
            DBusConnectionBuilder.forSessionBus().build() // verify session exists
            val cm = DBusConnectionBuilder.forSystemBus().build()
                .getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
            val session = cm.GetSession()
            val wasFrozen = session["state"] == "FROZEN"
            if (wasFrozen) cm.Unfreeze()
            block(cm)
            if (wasFrozen) cm.Freeze()
        } catch (e: Exception) {
            log.severe("WayDroid session is stopped")
        }
    }

    fun get(binderDev: String, key: String) {
        withSession {
            val ret = Props.hostGet(Any(), key)
            if (ret.isNotEmpty()) println(ret)
        }
    }

    fun set(binderDev: String, key: String, value: String) {
        withSession {
            Props.hostSet(Any(), key, value)
        }
    }
}
