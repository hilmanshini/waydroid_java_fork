/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import java.util.logging.Logger

private val log = Logger.getLogger("IPlatform")

const val IPLATFORM_INTERFACE = "lineageos.waydroid.IPlatform"
const val IPLATFORM_SERVICE = "waydroidplatform"

private const val TRANSACTION_getprop = 1
private const val TRANSACTION_setprop = 2
private const val TRANSACTION_getAppsInfo = 3
private const val TRANSACTION_getAppInfo = 4
private const val TRANSACTION_installApp = 5
private const val TRANSACTION_removeApp = 6
private const val TRANSACTION_launchApp = 7
private const val TRANSACTION_getAppName = 8
private const val TRANSACTION_settingsPutString = 9
private const val TRANSACTION_settingsGetString = 10
private const val TRANSACTION_settingsPutInt = 11
private const val TRANSACTION_settingsGetInt = 12
private const val TRANSACTION_launchIntent = 13

data class AppInfo(
    val name: String,
    val packageName: String,
    val action: String,
    val launchIntent: String,
    val componentPackageName: String,
    val componentClassName: String,
    val categories: List<String>
)

/**
 * Client-side proxy for the lineageos.waydroid.IPlatform binder service.
 * gbinder has no JVM binding; calls are made via lxc-attach shell commands as a fallback.
 * Replace the body of each method with a real binder transport when a JVM gbinder binding exists.
 */
class IPlatform(private val binderDev: String) {

    private fun transact(code: Int, args: List<String>): String {
        // Placeholder: real implementation needs a JVM gbinder binding.
        // For now, surface the limitation clearly.
        throw UnsupportedOperationException(
            "IPlatform binder transport not implemented. " +
            "A JVM/JNA gbinder binding is required for transaction code $code."
        )
    }

    fun getprop(key: String, default: String): String = try { transact(TRANSACTION_getprop, listOf(key, default)) } catch (e: Exception) { log.warning(e.message); default }
    fun setprop(key: String, value: String) { try { transact(TRANSACTION_setprop, listOf(key, value)) } catch (e: Exception) { log.warning(e.message) } }
    fun getAppsInfo(): List<AppInfo> { log.warning("getAppsInfo: binder not implemented"); return emptyList() }
    fun getAppInfo(pkg: String): AppInfo? { log.warning("getAppInfo: binder not implemented"); return null }
    fun installApp(path: String): Int { log.warning("installApp: binder not implemented"); return -1 }
    fun removeApp(pkg: String): Int { log.warning("removeApp: binder not implemented"); return -1 }
    fun launchApp(pkg: String) { log.warning("launchApp: binder not implemented") }
    fun launchIntent(action: String, uri: String): String? { log.warning("launchIntent: binder not implemented"); return null }
    fun getAppName(pkg: String): String? { log.warning("getAppName: binder not implemented"); return null }
    fun settingsPutString(table: Int, key: String, value: String) { log.warning("settingsPutString: binder not implemented") }
    fun settingsGetString(table: Int, key: String): String? { log.warning("settingsGetString: binder not implemented"); return null }
    fun settingsPutInt(table: Int, key: String, value: Int) { log.warning("settingsPutInt: binder not implemented") }
    fun settingsGetInt(table: Int, key: String): Int? { log.warning("settingsGetInt: binder not implemented"); return null }

    companion object {
        fun getService(binderDev: String): IPlatform? {
            return try { IPlatform(binderDev) } catch (e: Exception) { log.severe("Failed to get IPlatform service: ${e.message}"); null }
        }
    }
}
