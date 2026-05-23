/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface

/** D-Bus proxy interface for the container manager service. */
@DBusInterfaceName("id.waydro.ContainerManager")
interface IContainerManagerDbus : DBusInterface {
    fun Start(session: Map<String, String>)
    fun Stop(quitSession: Boolean)
    fun Freeze()
    fun Unfreeze()
    fun GetSession(): Map<String, String>
}

/** D-Bus proxy interface for the session manager service. */
@DBusInterfaceName("id.waydro.SessionManager")
interface ISessionManagerDbus : DBusInterface {
    fun Stop()
}

/** D-Bus proxy interface for the initializer service. */
@DBusInterfaceName("id.waydro.Initializer")
interface IInitializerDbus : DBusInterface {
    fun Init(params: Map<String, String>)
    fun Cancel()
}
