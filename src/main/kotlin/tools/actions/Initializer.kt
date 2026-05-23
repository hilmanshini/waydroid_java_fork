/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.config.*
import tools.helpers.*
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("Initializer")

object Initializer {
    fun isInitialized(configPath: String): Boolean =
        File(configPath).isFile && File(defaults["rootfs"]!!).isDirectory

    fun getVendorType(work: String): String {
        val vndkStr = Props.hostGet(Any(), "ro.vndk.version")
        if (vndkStr.isEmpty()) return "MAINLINE"
        val vndk = vndkStr.toIntOrNull() ?: return "MAINLINE"
        if (vndk <= 19) return "MAINLINE"
        var haliumVer = vndk - 19
        if (vndk > 31) haliumVer--
        return "HALIUM_$haliumVer${if (vndk == 32) "L" else ""}"
    }

    fun setupConfig(configPath: String, work: String, args: WaydroidArgs): Boolean {
        val cfg = load(configPath)

        args.arch = Arch.host()
        cfg.getOrPut("waydroid") { mutableMapOf() }["arch"] = args.arch

        args.vendorType = getVendorType(work)
        cfg["waydroid"]!!["vendor_type"] = args.vendorType

        Drivers.setupBinderNodes(cfg, args)
        cfg["waydroid"]!!["binder"] = args.binderDriver
        cfg["waydroid"]!!["vndbinder"] = args.vndBinderDriver
        cfg["waydroid"]!!["hwbinder"] = args.hwBinderDriver

        // Check for pre-installed images
        for (path in preinstalledImagesPaths) {
            val sysImg = File("$path/system.img")
            val vndImg = File("$path/vendor.img")
            if (sysImg.exists() && vndImg.exists()) {
                args.imagesPath = path
                cfg["waydroid"]!!["images_path"] = path
                cfg["waydroid"]!!["system_ota"] = "None"
                cfg["waydroid"]!!["vendor_ota"] = "None"
                cfg["waydroid"]!!["system_datetime"] = defaults["system_datetime"]!!
                cfg["waydroid"]!!["vendor_datetime"] = defaults["vendor_datetime"]!!
                save(configPath, cfg)
                return true
            }
        }

        if (args.imagesPath.isEmpty()) args.imagesPath = defaults["images_path"]!!
        cfg["waydroid"]!!["images_path"] = args.imagesPath

        val channelsCfg = loadChannels()
        if (args.systemChannel.isEmpty()) args.systemChannel = channelsCfg["channels"]?.get("system_channel") ?: ""
        if (args.vendorChannel.isEmpty()) args.vendorChannel = channelsCfg["channels"]?.get("vendor_channel") ?: ""
        if (args.romType.isEmpty()) args.romType = channelsCfg["channels"]?.get("rom_type") ?: "lineage"
        if (args.systemType.isEmpty()) args.systemType = channelsCfg["channels"]?.get("system_type") ?: "VANILLA"

        args.systemOta = "${args.systemChannel}/${args.romType}/waydroid_${args.arch}/${args.systemType}.json"
        val sysReq = Http.retrieve(args.systemOta)
        if (sysReq.first != 200) throw IllegalStateException("Failed to get system OTA: ${args.systemOta}, error: ${sysReq.first}")

        val deviceCodename = Props.hostGet(Any(), "ro.product.device")
        for (vendor in listOf(deviceCodename, getVendorType(work))) {
            val vendorOta = "${args.vendorChannel}/waydroid_${args.arch}/${vendor.replace(" ", "_")}.json"
            if (Http.retrieve(vendorOta).first == 200) {
                args.vendorType = vendor
                args.vendorOta = vendorOta
                break
            }
        }
        if (args.vendorOta.isEmpty()) throw IllegalStateException("Failed to get vendor OTA channel")

        if (args.systemOta != cfg["waydroid"]?.get("system_ota")) cfg["waydroid"]!!["system_datetime"] = defaults["system_datetime"]!!
        if (args.vendorOta != cfg["waydroid"]?.get("vendor_ota")) cfg["waydroid"]!!["vendor_datetime"] = defaults["vendor_datetime"]!!
        cfg["waydroid"]!!["vendor_type"] = args.vendorType
        cfg["waydroid"]!!["system_ota"] = args.systemOta
        cfg["waydroid"]!!["vendor_ota"] = args.vendorOta
        save(configPath, cfg)
        return true
    }

    fun init(configPath: String, work: String, args: WaydroidArgs) {
        if (isInitialized(configPath) && !args.force) { log.info("Already initialized"); return }
        if (!setupConfig(configPath, work, args)) return

        if (args.imagesPath !in preinstalledImagesPaths) Images.get(configPath, work)
        else Images.removeOverlay()

        for (dir in listOf(defaults["rootfs"]!!, defaults["overlay"]!!, "${defaults["overlay"]}/vendor",
                           defaults["overlay_rw"]!!, "${defaults["overlay_rw"]}/system", "${defaults["overlay_rw"]}/vendor")) {
            File(dir).mkdirs()
        }

        Drivers.probeAshmemDriver(work)
        Lxc.setupHostPerms(work)
        Lxc.setLxcConfig(configPath, work)
        Lxc.makeBaseProps(configPath, work)
    }
}

/** Mutable argument bag passed through the init/upgrade flow. */
data class WaydroidArgs(
    var arch: String = "",
    var vendorType: String = "",
    var binderDriver: String = "",
    var vndBinderDriver: String = "",
    var hwBinderDriver: String = "",
    var imagesPath: String = "",
    var systemChannel: String = "",
    var vendorChannel: String = "",
    var romType: String = "",
    var systemType: String = "",
    var systemOta: String = "",
    var vendorOta: String = "",
    var force: Boolean = false,
    var offline: Boolean = false
)
