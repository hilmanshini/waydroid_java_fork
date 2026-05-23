/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("Arch")

object Arch {
    private interface CLib : Library {
        fun personality(persona: Long): Int
    }

    private val clib: CLib by lazy { Native.load("c", CLib::class.java) }

    fun is32BitCapable(): Boolean {
        val PER_LINUX32 = 0x0008L
        val pers = clib.personality(PER_LINUX32)
        if (pers != -1) {
            clib.personality(pers.toLong())
            return true
        }
        return false
    }

    fun host(): String {
        val machine = System.getProperty("os.arch") ?: ""
        val mapping = mapOf(
            "i686"    to "x86",
            "x86_64"  to "x86_64",
            "amd64"   to "x86_64",
            "aarch64" to "arm64",
            "armv7l"  to "arm",
            "armv8l"  to "arm"
        )
        val target = mapping[machine]
            ?: throw IllegalArgumentException("platform.machine '$machine' architecture is not supported")
        return maybeRemap(target)
    }

    private fun maybeRemap(target: String): String {
        if (target.startsWith("x86")) {
            val cpuinfo = File("/proc/cpuinfo").readText()
            if ("ssse3" !in cpuinfo) throw IllegalStateException("x86/x86_64 CPU must support SSSE3!")
            if (target == "x86_64" && "sse4_2" !in cpuinfo) {
                log.info("x86_64 CPU does not support SSE4.2, falling back to x86...")
                return "x86"
            }
        } else if (target == "arm64") {
            if (System.getProperty("sun.arch.data.model") == "32") return "arm"
            if (!is32BitCapable()) {
                log.info("AArch64 CPU does not appear to support AArch32, assuming arm64_only...")
                return "arm64_only"
            }
        }
        return target
    }
}
