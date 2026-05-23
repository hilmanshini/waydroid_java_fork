/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.config

import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("ConfigSave")

fun save(configPath: String, cfg: Cfg) {
    log.fine("Save config: $configPath")
    val dir = File(configPath).parentFile
    dir?.mkdirs()
    File(configPath).bufferedWriter().use { w ->
        for ((section, entries) in cfg) {
            w.write("[$section]\n")
            for ((k, v) in entries) w.write("$k = $v\n")
            w.write("\n")
        }
    }
}
