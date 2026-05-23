/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import tools.actions.WaydroidArgs
import tools.config.Cfg
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.logging.Logger

private val log = Logger.getLogger("Drivers")

val BINDER_DRIVERS   = listOf("anbox-binder",   "puddlejumper", "bonder",   "binder")
val VNDBINDER_DRIVERS = listOf("anbox-vndbinder","vndpuddlejumper","vndbonder","vndbinder")
val HWBINDER_DRIVERS  = listOf("anbox-hwbinder", "hwpuddlejumper","hwbonder", "hwbinder")

object Drivers {
    fun isBinderfsLoaded(): Boolean =
        File("/proc/filesystems").readLines().any { it.split(Regex("\\s+")).getOrNull(1) == "binder" }

    fun allocBinderNodes(nodes: List<String>) {
        val NRBITS = 8; val TYPEBITS = 8; val SIZEBITS = 14
        val NRSHIFT = 0; val TYPESHIFT = NRSHIFT + NRBITS; val SIZESHIFT = TYPESHIFT + TYPEBITS; val DIRSHIFT = SIZESHIFT + SIZEBITS
        val BINDER_CTL_ADD = ((3 shl DIRSHIFT) or (98 shl TYPESHIFT) or (1 shl NRSHIFT) or (264 shl SIZESHIFT)).toLong()
        RandomAccessFile("/dev/binderfs/binder-control", "r").use { f ->
            for (node in nodes) {
                val buf = ByteBuffer.allocate(264).order(ByteOrder.nativeOrder())
                buf.put(node.toByteArray(Charsets.UTF_8).copyOf(256))
                buf.putInt(256, 0); buf.putInt(260, 0)
                runCatching {
                    val ioctl = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ioctl /dev/binderfs/binder-control $BINDER_CTL_ADD"))
                    ioctl.waitFor()
                }
            }
        }
    }

    fun probeBinderDriver(work: String): Int {
        val missing = mutableListOf<String>()
        if (BINDER_DRIVERS.none { File("/dev/$it").exists() }) missing.add(BINDER_DRIVERS[0])
        if (VNDBINDER_DRIVERS.none { File("/dev/$it").exists() }) missing.add(VNDBINDER_DRIVERS[0])
        if (HWBINDER_DRIVERS.none { File("/dev/$it").exists() }) missing.add(HWBINDER_DRIVERS[0])

        if (missing.isNotEmpty()) {
            if (!isBinderfsLoaded()) {
                val out = Run.user(Any(), listOf("modprobe", "binder_linux",
                    "devices=\"${missing.joinToString(",")}\""), check = false, outputReturn = true) as? String ?: ""
                if (out.isNotEmpty()) { log.severe("Failed to load binder driver\n$out") }
            }
            if (isBinderfsLoaded()) {
                Run.user(Any(), listOf("mkdir", "-p", "/dev/binderfs"), check = false)
                Run.user(Any(), listOf("mount", "-t", "binder", "binder", "/dev/binderfs"), check = false)
                allocBinderNodes(missing)
                val links = File("/dev/binderfs").listFiles()?.map { it.absolutePath } ?: emptyList()
                if (links.isNotEmpty()) Run.user(Any(), listOf("ln", "-sf") + links + listOf("/dev/"), check = false)
            }
        }
        return 0
    }

    fun probeAshmemDriver(work: String): Int {
        if (!File("/dev/ashmem").exists()) Run.user(Any(), listOf("modprobe", "-q", "ashmem_linux"), check = false)
        return if (File("/dev/ashmem").exists()) 0 else -1
    }

    fun setupBinderNodes(cfg: Cfg, args: WaydroidArgs) {
        val vendorType = cfg["waydroid"]?.get("vendor_type") ?: "MAINLINE"
        val drivers = if (vendorType == "MAINLINE") {
            probeBinderDriver("")
            Triple(BINDER_DRIVERS, VNDBINDER_DRIVERS, HWBINDER_DRIVERS)
        } else {
            Triple(BINDER_DRIVERS.dropLast(1), VNDBINDER_DRIVERS.dropLast(1), HWBINDER_DRIVERS.dropLast(1))
        }
        args.binderDriver = drivers.first.firstOrNull { File("/dev/$it").exists() }
            ?: throw RuntimeException("Binder node not found")
        args.vndBinderDriver = drivers.second.firstOrNull { File("/dev/$it").exists() }
            ?: throw RuntimeException("vndbinder node not found")
        args.hwBinderDriver = drivers.third.firstOrNull { File("/dev/$it").exists() }
            ?: throw RuntimeException("hwbinder node not found")
    }

    fun loadBinderNodes(cfg: Cfg) {
        // Reads binder node names from config — callers use cfg["waydroid"]["binder"] etc. directly
    }
}
