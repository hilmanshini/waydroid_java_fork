/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import tools.config.Cfg
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("Gpu")

private val unsupported = setOf("nvidia")

object Gpu {
    private fun sysFile(dev: String, suffix: String) = "/sys/class/drm/$dev/$suffix"

    fun getMinor(dev: String): String = Props.fileGet(Any(), sysFile(dev, "uevent"), "MINOR")
    fun getKernelDriver(dev: String): String = Props.fileGet(Any(), sysFile(dev, "device/uevent"), "DRIVER")

    fun getCardFromRender(dev: String): String = runCatching {
        "/dev/dri/" + File("/sys/class/drm/$dev/device/drm").listFiles()
            ?.filter { it.name.startsWith("card") }?.minByOrNull { it.name }?.name!!
    }.getOrElse { "" }

    fun getDriNode(cfg: Cfg): Pair<String, String> {
        val node = cfg["waydroid"]?.get("drm_device")
        if (node != null) {
            if (!File(node).exists()) throw RuntimeException("The specified drm_device $node does not exist")
            val dev = File(node).name
            return if (getKernelDriver(dev) !in unsupported) node to getCardFromRender(dev) else "" to ""
        }
        for (n in (File("/dev/dri").listFiles { f -> f.name.startsWith("renderD") }?.sortedBy { it.name } ?: emptyList())) {
            val dev = n.name
            if (getKernelDriver(dev) !in unsupported) return n.absolutePath to getCardFromRender(dev)
        }
        return "" to ""
    }

    fun getVulkanDriver(dev: String): String {
        val mapping = mapOf(
            "i915" to "intel", "xe" to "intel", "amdgpu" to "radeon",
            "panfrost" to "panfrost", "msm" to "freedreno", "msm_dpu" to "freedreno",
            "vc4" to "broadcom", "nouveau" to "nouveau"
        )
        val driver = getKernelDriver(dev)
        if (driver == "i915") {
            runCatching {
                val card = File(getCardFromRender(dev)).name
                val gen = Run.user(Any(), listOf("awk", "/^graphics version:|^gen:/ {print \$NF}",
                    "/sys/kernel/debug/dri/${getMinor(card)}/i915_capabilities"),
                    outputReturn = true) as? String ?: ""
                if ((gen.trim().toIntOrNull() ?: 99) < 9) return "intel_hasvk"
            }
        }
        return mapping[driver] ?: ""
    }
}
