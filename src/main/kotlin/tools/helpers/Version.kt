/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File

object Version {
    fun versionTuple(v: String): List<Int> = v.split(".").map { it.toInt() }

    fun kernelVersion(): List<Int> {
        val release = File("/proc/sys/kernel/osrelease").readText().trim()
        val match = Regex("""(\d+)\.(\d+)""").find(release)
            ?: return listOf(0, 0)
        return match.destructured.toList().map { it.toInt() }
    }
}
