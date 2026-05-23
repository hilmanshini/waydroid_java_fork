/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.util.logging.Logger

private val log = Logger.getLogger("Protocol")

object Protocol {
    fun setAidlVersion(args: Any, rootfsPath: String, cfg: MutableMap<String, MutableMap<String, String>>) {
        val androidApi = try {
            Props.fileGet(args, "$rootfsPath/system/build.prop", "ro.build.version.sdk").toInt()
        } catch (e: Exception) {
            log.severe("Failed to parse android version from system.img: $e")
            0
        }

        val (binder, sm) = when {
            androidApi < 28 -> "aidl"  to "aidl"
            androidApi < 30 -> "aidl2" to "aidl2"
            androidApi < 31 -> "aidl3" to "aidl3"
            androidApi < 33 -> "aidl4" to "aidl3"
            androidApi < 35 -> "aidl3" to "aidl3"
            androidApi < 36 -> "aidl3" to "aidl5"
            else            -> "aidl3" to "aidl6"
        }

        cfg.getOrPut("waydroid") { mutableMapOf() }["binder_protocol"] = binder
        cfg.getOrPut("waydroid") { mutableMapOf() }["service_manager_protocol"] = sm
    }
}
