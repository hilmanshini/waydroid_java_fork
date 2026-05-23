/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import tools.config.*
import java.io.File
import java.security.MessageDigest
import java.util.logging.Logger
import java.util.zip.ZipFile

private val log = Logger.getLogger("Images")

object Images {
    fun sha256sum(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(128 * 1024)
            var n: Int
            while (input.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun get(configPath: String, work: String) {
        val cfg = load(configPath)
        val imagesPath = cfg["waydroid"]?.get("images_path") ?: defaults["images_path"]!!

        for (channel in listOf("system", "vendor")) {
            val otaUrl = cfg["waydroid"]?.get("${channel}_ota") ?: continue
            val (code, body) = Http.retrieve(otaUrl)
            if (code != 200) throw IllegalStateException("Failed to get $channel OTA channel: $otaUrl, error: $code")

            val responses = org.json.JSONObject(String(body)).getJSONArray("response")
            val currentDatetime = cfg["waydroid"]?.get("${channel}_datetime")?.toLongOrNull() ?: 0L

            for (i in 0 until responses.length()) {
                val resp = responses.getJSONObject(i)
                if (resp.getLong("datetime") > currentDatetime) {
                    val zipPath = Http.download(Any().apply {}, otaUrl, resp.getString("filename"), cache = false)
                        ?: continue
                    log.info("Validating $channel image")
                    val zipFile = File(zipPath)
                    if (sha256sum(zipFile) != resp.getString("id")) {
                        zipFile.delete()
                        throw IllegalStateException("Downloaded $channel image hash doesn't match")
                    }
                    log.info("Extracting to $imagesPath")
                    ZipFile(zipFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            val dest = File(imagesPath, entry.name)
                            if (entry.isDirectory) dest.mkdirs()
                            else zip.getInputStream(entry).use { it.copyTo(dest.outputStream()) }
                        }
                    }
                    cfg["waydroid"]!![" ${channel}_datetime"] = resp.getLong("datetime").toString()
                    save(configPath, cfg)
                    zipFile.delete()
                    break
                }
            }
        }
        removeOverlay()
    }

    fun validate(configPath: String, channel: String, file: File): Boolean {
        val cfg = load(configPath)
        val channelUrl = cfg["waydroid"]?.get(channel) ?: return false
        val (code, body) = Http.retrieve(channelUrl)
        if (code != 200) return false
        val chksum = sha256sum(file)
        val responses = org.json.JSONObject(String(body)).getJSONArray("response")
        for (i in 0 until responses.length()) {
            if (chksum == responses.getJSONObject(i).getString("id")) return true
        }
        log.warning("Could not verify ${file.name} against $channelUrl")
        return false
    }

    fun replace(configPath: String, work: String, systemZip: String, systemTime: Long, vendorZip: String, vendorTime: Long) {
        val cfg = load(configPath)
        val imagesPath = cfg["waydroid"]?.get("images_path") ?: defaults["images_path"]!!
        for ((zip, time, channel) in listOf(
            Triple(systemZip, systemTime, "system_ota"),
            Triple(vendorZip, vendorTime, "vendor_ota")
        )) {
            val f = File(zip)
            if (!f.exists()) continue
            if (validate(configPath, channel, f)) {
                ZipFile(f).use { z ->
                    z.entries().asSequence().forEach { e ->
                        val dest = File(imagesPath, e.name)
                        if (e.isDirectory) dest.mkdirs()
                        else z.getInputStream(e).use { it.copyTo(dest.outputStream()) }
                    }
                }
                cfg["waydroid"]!![channel.replace("_ota", "_datetime")] = time.toString()
            } else log.warning("Failed to validate $channel image, ignoring")
            f.delete()
        }
        save(configPath, cfg)
        removeOverlay()
    }

    fun removeOverlay() {
        File(defaults["overlay_rw"]!!).takeIf { it.isDirectory }?.deleteRecursively()
        File(defaults["overlay_work"]!!).takeIf { it.isDirectory }?.deleteRecursively()
    }

    fun makeProp(configPath: String, work: String, session: Map<String, String>, fullPropsPath: String) {
        val baseProp = File("$work/waydroid_base.prop")
        if (!baseProp.isFile) throw RuntimeException("waydroid_base.prop Not found")
        val props = baseProp.readLines().toMutableList()

        fun addProp(key: String, value: String?) {
            if (value != null && value != "None") {
                props.add("$key=${value.replace("/mnt/", "/mnt_extra/")}")
            }
        }

        addProp("waydroid.host.user", session["user_name"])
        addProp("waydroid.host.uid", session["user_id"])
        addProp("waydroid.host.gid", session["group_id"])
        addProp("waydroid.host_data_path", session["waydroid_data"])
        addProp("waydroid.background_start", session["background_start"])
        props.add("waydroid.xdg_runtime_dir=${defaults["container_xdg_runtime_dir"]}")
        props.add("waydroid.pulse_runtime_path=${defaults["container_pulse_runtime_path"]}")
        props.add("waydroid.wayland_display=${defaults["container_wayland_display"]}")
        if (!which("waydroid-sensord")) props.add("waydroid.stub_sensors_hal=1")
        val dpi = session["lcd_density"] ?: "0"
        if (dpi != "0") props.add("ro.sf.lcd_density=$dpi")

        File(fullPropsPath).writeText(props.joinToString("\n") + "\n")
        File(fullPropsPath).setReadable(true, false)
    }

    fun mountRootfs(work: String, imagesDir: String, session: Map<String, String>) {
        val cfg = load("$work/waydroid.cfg")
        Mount.mount(Any(), "$imagesDir/system.img", defaults["rootfs"]!!, umount = true)
        if (cfg["waydroid"]?.get("mount_overlays") == "True") {
            runCatching {
                Mount.mountOverlay(Any(), listOf(defaults["overlay"]!!, defaults["rootfs"]!!), defaults["rootfs"]!!,
                    upperDir = "${defaults["overlay_rw"]}/system", workDir = "${defaults["overlay_work"]}/system")
            }.onFailure {
                cfg["waydroid"]!!["mount_overlays"] = "False"
                save("$work/waydroid.cfg", cfg)
                log.warning("Mounting overlays failed. The feature has been disabled.")
            }
        }
        Mount.mount(Any(), "$imagesDir/vendor.img", "${defaults["rootfs"]}/vendor")
        if (cfg["waydroid"]?.get("mount_overlays") == "True") {
            Mount.mountOverlay(Any(), listOf("${defaults["overlay"]}/vendor", "${defaults["rootfs"]}/vendor"),
                "${defaults["rootfs"]}/vendor",
                upperDir = "${defaults["overlay_rw"]}/vendor", workDir = "${defaults["overlay_work"]}/vendor")
        }
        for (eglPath in listOf("/vendor/lib/egl", "/vendor/lib64/egl")) {
            if (File(eglPath).isDirectory) Mount.bind(Any(), eglPath, "${defaults["rootfs"]}$eglPath")
        }
        if (Mount.isMount("/odm")) Mount.bind(Any(), "/odm", "${defaults["rootfs"]}/odm_extra")
        else if (File("/vendor/odm").isDirectory) Mount.bind(Any(), "/vendor/odm", "${defaults["rootfs"]}/odm_extra")

        makeProp("$work/waydroid.cfg", work, session, "$work/waydroid.prop")
        Mount.bindFile(Any(), "$work/waydroid.prop", "${defaults["rootfs"]}/vendor/waydroid.prop")
    }

    fun umountRootfs(work: String) {
        Mount.umountAll(Any(), defaults["rootfs"]!!)
    }

    private fun which(cmd: String) = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .any { File(it, cmd).canExecute() }
}
