/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.config

import java.io.File
import java.util.Properties
import java.util.logging.Logger

private val log = Logger.getLogger("ConfigLoad")

typealias Cfg = MutableMap<String, MutableMap<String, String>>

fun load(configPath: String): Cfg {
    val cfg: Cfg = mutableMapOf()
    val file = File(configPath)

    if (file.isFile) {
        var section = ""
        file.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("[") && line.endsWith("]") -> section = line.drop(1).dropLast(1)
                line.isNotEmpty() && !line.startsWith("#") && !line.startsWith(";") -> {
                    val eq = line.indexOf('=')
                    if (eq > 0 && section.isNotEmpty()) {
                        cfg.getOrPut(section) { mutableMapOf() }[line.substring(0, eq).trim()] =
                            line.substring(eq + 1).trim()
                    }
                }
            }
        }
    }

    val waydroid = cfg.getOrPut("waydroid") { mutableMapOf() }
    for (key in configKeys) {
        if (key !in waydroid) waydroid[key] = defaults[key] ?: continue
    }
    // Remove non-configurable keys that may have been saved previously
    for (key in defaults.keys) {
        if (key !in configKeys && key in waydroid) {
            log.fine("Ignored unconfigurable default value from config: ${waydroid[key]}")
            waydroid.remove(key)
        }
    }

    cfg.getOrPut("properties") { mutableMapOf() }
    return cfg
}

fun loadChannels(): Cfg {
    val configPath = channelsDefaults["config_path"]!!
    val cfg: Cfg = mutableMapOf()
    val file = File(configPath)

    if (file.isFile) {
        var section = ""
        file.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("[") && line.endsWith("]") -> section = line.drop(1).dropLast(1)
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val eq = line.indexOf('=')
                    if (eq > 0 && section.isNotEmpty())
                        cfg.getOrPut(section) { mutableMapOf() }[line.substring(0, eq).trim()] =
                            line.substring(eq + 1).trim()
                }
            }
        }
    }

    val channels = cfg.getOrPut("channels") { mutableMapOf() }
    for (key in channelsConfigKeys) {
        if (key !in channels) channels[key] = channelsDefaults[key] ?: continue
    }
    for (key in channelsDefaults.keys) {
        if (key !in channelsConfigKeys && key in channels) {
            log.fine("Ignored unconfigurable default value from channels config: ${channels[key]}")
            channels.remove(key)
        }
    }
    return cfg
}
