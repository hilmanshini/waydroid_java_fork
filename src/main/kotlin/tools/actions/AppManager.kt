/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.actions

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import tools.config.sessionDefaults
import tools.interfaces.IPlatform
import tools.interfaces.IStatusBarService
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("AppManager")

object AppManager {
    private fun systemConn() = DBusConnectionBuilder.forSystemBus().build()
    private fun sessionConn() = DBusConnectionBuilder.forSessionBus().build()

//    private fun withSession(binderDev: String, block: (cm: IContainerManagerDbus, platform: IPlatform) -> Unit) {
//        try {
//            sessionConn() // verify session exists
//            val cm = systemConn().getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java)
//            val session = cm.GetSession()
//            val wasFrozen = session["state"] == "FROZEN"
//            if (wasFrozen) cm.Unfreeze()
//            val platform = IPlatform.getService(binderDev)
//            if (platform != null) block(cm, platform)
//            else log.severe("Failed to access IPlatform service")
//            if (wasFrozen) cm.Freeze()
//        } catch (e: Exception) {
//            log.severe("WayDroid session is stopped")
//        }
//    }
private fun withSession(binderDev: String, block: (cm: IContainerManagerDbus, platform: IPlatform) -> Unit) {
    var retryCount = 0
    val maxRetries = 2

    while (retryCount <= maxRetries) {
        val sysBus = systemConn()
        val sessBus = sessionConn()

        var cmInstance: IContainerManagerDbus? = null
        var activeSessionMap: Map<String, String> = emptyMap()

        try {
//            sessBus.connectionId // Verify session bus is alive

            val cm = sysBus.getRemoteObject(
                "id.waydro.Container",
                "/ContainerManager",
                IContainerManagerDbus::class.java
            )
            cmInstance = cm

            activeSessionMap = cm.GetSession() ?: emptyMap()
            val wasFrozen = activeSessionMap["state"] == "FROZEN"
            if (wasFrozen) cm.Unfreeze()

            // Fail-fast service discovery loop
            val platform = IPlatform.getService(binderDev, retries = 3, retryDelayMs = 500)

            if (platform != null) {
                block(cm, platform)
                if (wasFrozen) cm.Freeze()
                return // Success! Exit cleanly.
            } else {
                throw IllegalStateException("IPlatform service discovery returned null.")
            }

        } catch (e: Exception) {
            log.severe("WayDroid session exception or binder failure: ${e.message}")

            if (retryCount < maxRetries) {
                log.info("Triggering rapid state recovery (Attempt ${retryCount + 1})...")

                try {
                    val cmRecovery = cmInstance ?: sysBus.getRemoteObject(
                        "id.waydro.Container",
                        "/ContainerManager",
                        IContainerManagerDbus::class.java
                    )

                    log.warning("Nuking stale container processes safely...")
                    cmRecovery.Stop(true)
                    Thread.sleep(1500) // Allow system sockets to fully release /dev/binder

                    log.info("Delegating to SessionManager to restart under correct user UID context...")

                    // Crucial Step: Close connections BEFORE calling SessionManager
                    // to prevent lockups and pass execution out of the current loop cleanly.
                    runCatching { sysBus.close() }
                    runCatching { sessBus.close() }

                    // Use your already existing SessionManager entry point which handles the
                    // UID, background flags, and Wayland socket discovery natively.
                    var recoveryComplete = false
                    SessionManager.start(binderDev, unlockedCb = {
                        recoveryComplete = true
                    }, background = (activeSessionMap["background_start"] == "true"))

                    // Wait for SessionManager to finish spinning up the user container instance
                    var waitTime = 0
                    while (!recoveryComplete && waitTime < 10) {
                        Thread.sleep(500)
                        waitTime++
                    }

                } catch (recoveryError: Exception) {
                    log.severe("Failed to safely delegate recovery sequence: ${recoveryError.message}")
                }

                retryCount++
            } else {
                log.severe("WayDroid programmatic recovery exhausted all attempts.")
                break
            }
        } finally {
            // Ensured fallback cleanup
            runCatching { sysBus.close() }
            runCatching { sessBus.close() }
        }
    }
}



    fun install(binderDev: String, packagePath: String) {
        withSession(binderDev) { _, platform ->
            val tmpDir = File(sessionDefaults()["waydroid_data"]!!, "waydroid_tmp").also { it.mkdirs() }
            val dest = File(tmpDir, "base.apk")
            File(packagePath).copyTo(dest, overwrite = true)
            platform.installApp("/data/waydroid_tmp/base.apk")
            dest.delete()
        }
    }

    fun remove(binderDev: String, packageName: String) {
        withSession(binderDev) { _, platform ->
            val ret = platform.removeApp(packageName)
            if (ret != 0) log.severe("Failed to uninstall package: $packageName")
        }
    }

    fun launch(binderDev: String, packageName: String) {
        fun justLaunch() {
            val platform = IPlatform.getService(binderDev) ?: run { log.severe("Failed to access IPlatform service"); return }
            platform.setprop("waydroid.active_apps", packageName)
            platform.launchApp(packageName)
            val multiwin = platform.getprop("persist.waydroid.multi_windows", "false")
            val policy = if (multiwin == "false") "immersive.status=*" else "immersive.full=*"
            platform.settingsPutString(2, "policy_control", policy)
        }
        maybeLaunchLater(binderDev, ::justLaunch)
    }

    fun list(binderDev: String) {
        val platform = IPlatform.getService(binderDev, retries = 5, retryDelayMs = 500)
        if (platform == null) { log.severe("Failed to access IPlatform service"); return }
        val apps = platform.getAppsInfo()
        log.info("getAppsInfo returned ${apps.size} apps")
        apps.forEach { app ->
            println("Name: ${app.name}")
            println("packageName: ${app.packageName}")
            println("categories:")
            app.categories.forEach { println("\t$it") }
        }
    }

    fun showFullUI(binderDev: String) {
        fun justShow() {
            val platform = IPlatform.getService(binderDev) ?: run { log.severe("Failed to access IPlatform service"); return }
            platform.setprop("waydroid.active_apps", "Waydroid")
            platform.settingsPutString(2, "policy_control", "null*")
            val statusBar = IStatusBarService.getService(binderDev)
            if (statusBar != null) {
                statusBar.expand()
                Thread.sleep(500)
                statusBar.collapse()
            }
        }
        maybeLaunchLater(binderDev, ::justShow)
    }

    fun intent(binderDev: String, action: String, uri: String) {
        fun justLaunch() {
            val platform = IPlatform.getService(binderDev) ?: run { log.severe("Failed to access IPlatform service"); return }
            val ret = platform.launchIntent(action, uri) ?: return
            if (ret.isEmpty()) return
            val pkg = if (ret != "android") ret else "Waydroid"
            platform.setprop("waydroid.active_apps", pkg)
            val multiwin = platform.getprop("persist.waydroid.multi_windows", "false")
            platform.settingsPutString(2, "policy_control", if (multiwin == "false") "immersive.status=*" else "immersive.full=*")
        }
        maybeLaunchLater(binderDev, ::justLaunch)
    }

    private fun maybeLaunchLater(binderDev: String, launchNow: () -> Unit) {
        try {
            sessionConn()
            runCatching { systemConn().getRemoteObject("id.waydro.Container", "/ContainerManager", IContainerManagerDbus::class.java).Unfreeze() }
            launchNow()
        } catch (e: Exception) {
            log.severe("Starting waydroid session")
            SessionManager.start(binderDev, launchNow, background = false)
        }
    }
}
