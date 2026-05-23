/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.services

import tools.config.*
import tools.helpers.Net
import tools.helpers.Props
import tools.interfaces.*
import java.io.File
import java.util.logging.Logger

private val log = Logger.getLogger("UserManager")

@Volatile private var stopping = false
private var thread: Thread? = null

private val systemApps = setOf(
    "com.android.calculator2", "com.android.camera2", "com.android.contacts",
    "com.android.deskclock", "com.android.documentsui", "com.android.email",
    "com.android.gallery3d", "com.android.inputmethod.latin", "com.android.settings",
    "com.google.android.gms", "org.lineageos.aperture", "org.lineageos.eleven",
    "org.lineageos.etar", "org.lineageos.jelly", "org.lineageos.recorder"
)

fun startUserManager(
    configPath: String,
    work: String,
    binderDev: String,
    session: Map<String, String>,
    unlocked_cb: (() -> Unit)? = null
) {
    val appsDir = File(session["xdg_data_home"]!!, "applications").also { it.mkdirs() }
    val iconsDir = File(session["waydroid_data"]!!, "icons")

    fun desktopFilePath(pkg: String) = File(appsDir, "waydroid.$pkg.desktop")

    fun updateDesktopFile(appInfo: AppInfo?) {
        appInfo ?: return
        val pkg = appInfo.packageName
        val path = desktopFilePath(pkg)
        val isLauncher = appInfo.categories.any { it.trim() == "android.intent.category.LAUNCHER" }
        if (!isLauncher) { path.delete(); return }

        val lines = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=${appInfo.name}")
            appendLine("Exec=waydroid app launch $pkg")
            appendLine("Icon=${File(iconsDir, "$pkg.png")}")
            appendLine("Categories=X-WayDroid-App;")
            appendLine("Actions=app-settings;")
            if (pkg in systemApps) appendLine("NoDisplay=true")
            appendLine()
            appendLine("[Desktop Action app-settings]")
            appendLine("Name=App Settings")
            appendLine("Exec=waydroid app intent android.settings.APPLICATION_DETAILS_SETTINGS package:$pkg")
            appendLine("Icon=${File(iconsDir, "com.android.settings.png")}")
        }
        path.writeText(lines)
    }

    fun userUnlocked(uid: Int) {
        log.info("Android with user $uid is ready")
        val cfg = load(configPath)
        if (cfg["waydroid"]?.get("auto_adb") == "True") {
            runCatching { Net.adbConnect(work, binderDev) }
        }
        val platform = IPlatform.getService(binderDev)
        if (platform != null) {
            val apps = platform.getAppsInfo()
            apps.forEach { updateDesktopFile(it) }
            val pkgNames = apps.map { "waydroid.${it.packageName}.desktop" }.toSet()
            appsDir.listFiles { f -> f.name.startsWith("waydroid.") && f.name !in pkgNames }
                ?.forEach { it.delete() }
        }
        unlocked_cb?.invoke()
    }

    fun packageStateChanged(mode: Int, packageName: String, uid: Int) {
        val platform = IPlatform.getService(binderDev) ?: return
        if (mode == PACKAGE_REMOVED) desktopFilePath(packageName).delete()
        else updateDesktopFile(platform.getAppInfo(packageName))
    }

    stopping = false
    thread = Thread {
        while (!stopping) {
            addUserMonitorService(binderDev, ::userUnlocked, ::packageStateChanged)
        }
    }.also { it.isDaemon = true; it.start() }
}

fun stopUserManager() {
    stopping = true
}
