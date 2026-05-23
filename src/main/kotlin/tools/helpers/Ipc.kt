/*
 * Copyright 2022 Alessandro Astone
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

object Ipc {
    fun getContainerService(
        objectPath: String = "/ContainerManager",
        intf: Class<out DBusInterface>
    ): DBusInterface =
        DBusConnectionBuilder.forSystemBus().build()
            .getRemoteObject("id.waydro.Container", objectPath, intf)

    fun getSessionService(
        objectPath: String = "/SessionManager",
        intf: Class<out DBusInterface>
    ): DBusInterface =
        DBusConnectionBuilder.forSessionBus().build()
            .getRemoteObject("id.waydro.Session", objectPath, intf)
}
