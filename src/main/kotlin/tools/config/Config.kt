/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.config

import java.io.File

const val VERSION = "1.6.2"
val TOOLS_SRC: String = File(object {}.javaClass.protectionDomain.codeSource.location.toURI()).parentFile.parentFile.absolutePath

val configKeys = listOf(
    "arch", "images_path", "vendor_type",
    "system_datetime", "vendor_datetime",
    "suspend_action", "mount_overlays", "auto_adb"
)

val defaults: Map<String, String> = mutableMapOf(
    "arch"             to "arm64",
    "work"             to "/var/lib/waydroid",
    "vendor_type"      to "MAINLINE",
    "system_datetime"  to "0",
    "vendor_datetime"  to "0",
    "suspend_action"   to "freeze",
    "mount_overlays"   to "True",
    "auto_adb"         to "False",
    "container_xdg_runtime_dir"    to "/run/xdg",
    "container_wayland_display"    to "wayland-0",
).also { m ->
    val work = m["work"]!!
    m["images_path"]   = "$work/images"
    m["rootfs"]        = "$work/rootfs"
    m["overlay"]       = "$work/overlay"
    m["overlay_rw"]    = "$work/overlay_rw"
    m["overlay_work"]  = "$work/overlay_work"
    m["data"]          = "$work/data"
    m["lxc"]           = "$work/lxc"
    m["host_perms"]    = "$work/host-permissions"
    m["container_pulse_runtime_path"] = "${m["container_xdg_runtime_dir"]}/pulse"
}

val preinstalledImagesPaths = listOf(
    "/etc/waydroid-extra/images",
    "/usr/share/waydroid-extra/images"
)

fun sessionDefaults(): MutableMap<String, String> {
    val uid = ProcessHandle.current().pid().toInt() // approximation; real uid via native
    val home = System.getProperty("user.home") ?: "/root"
    val xdgDataHome = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
    val xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR") ?: ""
    val waylandDisplay = System.getenv("WAYLAND_DISPLAY") ?: "wayland-0"
    var pulseRuntimePath = System.getenv("PULSE_RUNTIME_PATH") ?: ""
    if (pulseRuntimePath.isEmpty()) pulseRuntimePath = "$xdgRuntimeDir/pulse"

    val waydroidUserState = "$xdgDataHome/waydroid"
    return mutableMapOf(
        "user_name"          to (System.getProperty("user.name") ?: ""),
        "user_id"            to uid.toString(),
        "group_id"           to uid.toString(),
        "host_user"          to home,
        "pid"                to ProcessHandle.current().pid().toString(),
        "xdg_data_home"      to xdgDataHome,
        "xdg_runtime_dir"    to xdgRuntimeDir,
        "wayland_display"    to waylandDisplay,
        "pulse_runtime_path" to pulseRuntimePath,
        "state"              to "STOPPED",
        "lcd_density"        to "0",
        "background_start"   to "true",
        "waydroid_user_state" to waydroidUserState,
        "waydroid_data"      to "$waydroidUserState/data"
    )
}

val channelsDefaults = mapOf(
    "config_path"     to "/usr/share/waydroid-extra/channels.cfg",
    "system_channel"  to "https://ota.waydro.id/system",
    "vendor_channel"  to "https://ota.waydro.id/vendor",
    "rom_type"        to "lineage",
    "system_type"     to "VANILLA"
)

val channelsConfigKeys = listOf("system_channel", "vendor_channel", "rom_type", "system_type")
