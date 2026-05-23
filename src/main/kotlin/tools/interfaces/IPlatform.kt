/*
 * Copyright 2021 Oliver Smith
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import com.sun.jna.Pointer
import java.util.logging.Logger

private val log = Logger.getLogger("IPlatform")

const val IPLATFORM_INTERFACE = "lineageos.waydroid.IPlatform"
const val IPLATFORM_SERVICE   = "waydroidplatform"

private const val TRANSACTION_getprop           = 1
private const val TRANSACTION_setprop           = 2
private const val TRANSACTION_getAppsInfo       = 3
private const val TRANSACTION_getAppInfo        = 4
private const val TRANSACTION_installApp        = 5
private const val TRANSACTION_removeApp         = 6
private const val TRANSACTION_launchApp         = 7
private const val TRANSACTION_getAppName        = 8
private const val TRANSACTION_settingsPutString = 9
private const val TRANSACTION_settingsGetString = 10
private const val TRANSACTION_settingsPutInt    = 11
private const val TRANSACTION_settingsGetInt    = 12
private const val TRANSACTION_launchIntent      = 13

data class AppInfo(
    val name: String,
    val packageName: String,
    val action: String,
    val launchIntent: String,
    val componentPackageName: String,
    val componentClassName: String,
    val categories: List<String>
)

class IPlatform private constructor(
    private val sm: Pointer,
    private val remote: Pointer,
    private val client: Pointer
) {
    private val g = LibGBinder.INSTANCE

    private fun transact(code: Int, build: ((Pointer) -> Unit)? = null): Pair<Boolean, GBinderReader>? {
        val req = g.gbinder_client_new_request(client)
        println("DEBUG transact: code=$code req=$req")
        if (req != null && build != null) build(req)
        val statusArr = IntArray(1)
        val reply = g.gbinder_client_transact_sync_reply(client, code, req, statusArr)
        println("DEBUG transact: reply=$reply status=${statusArr[0]}")
        if (req != null) g.gbinder_local_request_unref(req)
        if (reply == null || statusArr[0] != 0) return null
        val reader = GBinderReader()
        g.gbinder_remote_reply_init_reader(reply, reader)
        val exArr = IntArray(1)
        g.gbinder_reader_read_int32(reader, exArr)
        println("DEBUG transact: exception=${exArr[0]}")
        if (exArr[0] != 0) { g.gbinder_remote_reply_unref(reply); return null }
        return true to reader
    }

    fun getprop(key: String, default: String): String {
        val (_, reader) = transact(TRANSACTION_getprop) {
            g.gbinder_local_request_append_string16(it, key)
            g.gbinder_local_request_append_string16(it, default)
        } ?: return default
        return g.gbinder_reader_read_string16(reader) ?: default
    }

    fun setprop(key: String, value: String) {
        transact(TRANSACTION_setprop) {
            g.gbinder_local_request_append_string16(it, key)
            g.gbinder_local_request_append_string16(it, value)
        }
    }

    fun getAppsInfo(): List<AppInfo> {
        println("DEBUG: calling getAppsInfo...")
        println("DEBUG: calling TRANSACTION_getAppsInfo (code=$TRANSACTION_getAppsInfo)")
        val result = transact(TRANSACTION_getAppsInfo)
        println("DEBUG: getAppsInfo transact = $result")
//        val result = transact(TRANSACTION_getAppsInfo)
        println("DEBUG: transact result = $result")
        if (result == null) {
            println("DEBUG: transact returned null — transaction failed")
            return emptyList()
        }
        val (_, reader) = result
        val countArr = IntArray(1)
        val readOk = g.gbinder_reader_read_int32(reader, countArr)
        println("DEBUG: readOk=$readOk count=${countArr[0]}")
        if (!readOk) return emptyList()
        return (0 until countArr[0]).mapNotNull {
            val hasValueArr = IntArray(1)
            if (!g.gbinder_reader_read_int32(reader, hasValueArr) || hasValueArr[0] != 1) null
            else readAppInfo(reader)
        }
    }

    fun getAppInfo(pkg: String): AppInfo? {
        val (_, reader) = transact(TRANSACTION_getAppInfo) {
            g.gbinder_local_request_append_string16(it, pkg)
        } ?: return null
        val hasValueArr = IntArray(1)
        if (!g.gbinder_reader_read_int32(reader, hasValueArr) || hasValueArr[0] != 1) return null
        return readAppInfo(reader)
    }

    private fun readAppInfo(reader: GBinderReader): AppInfo {
        val name      = g.gbinder_reader_read_string16(reader) ?: ""
        val pkg       = g.gbinder_reader_read_string16(reader) ?: ""
        val action    = g.gbinder_reader_read_string16(reader) ?: ""
        val intent    = g.gbinder_reader_read_string16(reader) ?: ""
        val compPkg   = g.gbinder_reader_read_string16(reader) ?: ""
        val compClass = g.gbinder_reader_read_string16(reader) ?: ""
        val catArr = IntArray(1)
        g.gbinder_reader_read_int32(reader, catArr)
        val cats = (0 until catArr[0]).map { g.gbinder_reader_read_string16(reader) ?: "" }
        return AppInfo(name, pkg, action, intent, compPkg, compClass, cats)
    }

    fun installApp(path: String): Int {
        val (_, reader) = transact(TRANSACTION_installApp) {
            g.gbinder_local_request_append_string16(it, path)
        } ?: return -1
        val arr = IntArray(1)
        return if (g.gbinder_reader_read_int32(reader, arr)) arr[0] else -1
    }

    fun removeApp(pkg: String): Int {
        val (_, reader) = transact(TRANSACTION_removeApp) {
            g.gbinder_local_request_append_string16(it, pkg)
        } ?: return -1
        val arr = IntArray(1)
        return if (g.gbinder_reader_read_int32(reader, arr)) arr[0] else -1
    }

    fun launchApp(pkg: String) {
        transact(TRANSACTION_launchApp) { g.gbinder_local_request_append_string16(it, pkg) }
    }

    fun launchIntent(action: String, uri: String): String? {
        val (_, reader) = transact(TRANSACTION_launchIntent) {
            g.gbinder_local_request_append_string16(it, action)
            g.gbinder_local_request_append_string16(it, uri)
        } ?: return null
        return g.gbinder_reader_read_string16(reader)
    }

    fun getAppName(pkg: String): String? {
        val (_, reader) = transact(TRANSACTION_getAppName) {
            g.gbinder_local_request_append_string16(it, pkg)
        } ?: return null
        return g.gbinder_reader_read_string16(reader)
    }

    fun settingsPutString(table: Int, key: String, value: String) {
        transact(TRANSACTION_settingsPutString) {
            g.gbinder_local_request_append_int32(it, table)
            g.gbinder_local_request_append_string16(it, key)
            g.gbinder_local_request_append_string16(it, value)
        }
    }

    fun settingsGetString(table: Int, key: String): String? {
        val (_, reader) = transact(TRANSACTION_settingsGetString) {
            g.gbinder_local_request_append_int32(it, table)
            g.gbinder_local_request_append_string16(it, key)
        } ?: return null
        return g.gbinder_reader_read_string16(reader)
    }

    fun settingsPutInt(table: Int, key: String, value: Int) {
        transact(TRANSACTION_settingsPutInt) {
            g.gbinder_local_request_append_int32(it, table)
            g.gbinder_local_request_append_string16(it, key)
            g.gbinder_local_request_append_int32(it, value)
        }
    }

    fun settingsGetInt(table: Int, key: String): Int? {
        val (_, reader) = transact(TRANSACTION_settingsGetInt) {
            g.gbinder_local_request_append_int32(it, table)
            g.gbinder_local_request_append_string16(it, key)
        } ?: return null
        val arr = IntArray(1)
        return if (g.gbinder_reader_read_int32(reader, arr)) arr[0] else null
    }

    fun free() {
        g.gbinder_client_unref(client)
        g.gbinder_remote_object_unref(remote)
        g.gbinder_servicemanager_unref(sm)
    }

    companion object {
        private fun dumpContainerLogs() {
            // Try logcat first (container running but service missing)
            val logcat = runCatching {
                ProcessBuilder("lxc-attach", "-P", "/var/lib/waydroid/lxc", "-n", "waydroid",
                    "--clear-env", "--", "/system/bin/logcat", "-d", "-t", "50",
                    "-s", "ServiceManager", "waydroidplatform", "AndroidRuntime", "system_server")
                    .redirectErrorStream(true).start()
                    .also { it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
                    .inputStream.bufferedReader().readText().trim()
            }.getOrNull()

            if (!logcat.isNullOrBlank()) {
                println("\n--- Container logs (last 50 lines) ---")
                println(logcat)
                println("--------------------------------------")
                return
            }

            // Container not running — show waydroid.log tail instead
            val waydroidLog = java.io.File("/var/lib/waydroid/waydroid.log")
            if (waydroidLog.exists()) {
                println("\n--- /var/lib/waydroid/waydroid.log (last 30 lines) ---")
                waydroidLog.readLines().takeLast(30).forEach(::println)
                println("------------------------------------------------------")
            } else {
                println("Container is not running and no waydroid.log found. Run: sudo waydroid container start")
            }
        }
        // In your IPlatform companion object
        fun getService(
            binderDev: String = "binder",
            smProtocol: String = "aidl3",
            binderProtocol: String = "aidl3",
            retries: Int = 5,
            retryDelayMs: Long = 500
        ): IPlatform? {
            val devPath = if (binderDev.startsWith("/")) binderDev else "/dev/$binderDev"
            val g = LibGBinder.INSTANCE
            val sm = g.gbinder_servicemanager_new2(devPath, smProtocol, binderProtocol) ?: return null

            if (!g.gbinder_servicemanager_is_present(sm)) {
                var waited = 0
                while (!g.gbinder_servicemanager_is_present(sm) && waited < 10) {
                    Thread.sleep(500); waited++
                }
                if (!g.gbinder_servicemanager_is_present(sm)) {
                    g.gbinder_servicemanager_unref(sm)
                    return null
                }
            }

            var remote: Pointer? = null
            for (i in 0 until retries) {
                remote = g.gbinder_servicemanager_get_service_sync(sm, IPLATFORM_SERVICE, null)
                if (remote != null) break
                if (i < retries - 1) {
                    log.warning("Failed to get service $IPLATFORM_SERVICE, trying again (Attempt ${i+1}/$retries)...")
                    Thread.sleep(retryDelayMs)
                }
            }

            if (remote == null) {
                g.gbinder_servicemanager_unref(sm)
                throw IllegalStateException("Service $IPLATFORM_SERVICE not found")
            }

            val client = g.gbinder_client_new(remote, IPLATFORM_INTERFACE) ?: run {
                g.gbinder_remote_object_unref(remote)
                g.gbinder_servicemanager_unref(sm)
                return null
            }

            return IPlatform(sm, remote, client)
        }

    }
}
