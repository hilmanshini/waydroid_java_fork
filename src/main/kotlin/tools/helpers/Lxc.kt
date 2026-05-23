/*
 * Copyright 2021 Erfan Abdi
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import tools.config.*
import java.io.File
import java.nio.file.Path
import java.util.logging.Logger

private val log = Logger.getLogger("Lxc")

val ANDROID_ENV = mapOf(
    "PATH" to "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin",
    "ANDROID_ROOT" to "/system",
    "ANDROID_DATA" to "/data",
    "ANDROID_STORAGE" to "/storage",
    "ANDROID_ART_ROOT" to "/apex/com.android.art",
    "ANDROID_I18N_ROOT" to "/apex/com.android.i18n",
    "ANDROID_TZDATA_ROOT" to "/apex/com.android.tzdata",
    "ANDROID_RUNTIME_ROOT" to "/apex/com.android.runtime"
)

object Lxc {
    fun getLxcVersion(work: String): Int {
        if (!which("lxc-info")) return 0
        return runCatching {
            (Run.user(Any(), listOf("lxc-info", "--version"), outputReturn = true) as? String)
                ?.trim()?.split(".")?.firstOrNull()?.toIntOrNull() ?: 0
        }.getOrElse { 0 }
    }

    fun getAppArmorStatus(work: String): Boolean {
        var enabled = which("aa-enabled") && (Run.user(Any(), listOf("aa-enabled", "--quiet"), check = false) as? Int == 0)
        if (!enabled && which("systemctl"))
            enabled = (Run.user(Any(), listOf("systemctl", "is-active", "-q", "apparmor"), check = false) as? Int == 0)
        return enabled && runCatching {
            File("/sys/kernel/security/apparmor/profiles").readText().contains("lxc-waydroid")
        }.getOrElse { false }
    }

    fun setLxcConfig(configPath: String, work: String) {
        val lxcPath = "${defaults["lxc"]}/waydroid"
        val lxcVer = getLxcVersion(work)
        if (lxcVer == 0) throw RuntimeException("LXC is not installed")

        val configBase = "$TOOLS_SRC/data/configs/config_base"
        val snippets = mutableListOf(configBase)
        if (lxcVer <= 2) snippets.add("$TOOLS_SRC/data/configs/config_1")
        else for (v in 3..4) {
            val s = "$TOOLS_SRC/data/configs/config_$v"
            if (lxcVer >= v && File(s).exists()) snippets.add(s)
        }

        Run.user(Any(), listOf("mkdir", "-p", lxcPath))
        val catCmd = snippets.joinToString(" ") { "\"$it\"" }
        Run.user(Any(), listOf("sh", "-c", "cat $catCmd > \"$lxcPath/config\""))
        Run.user(Any(), listOf("sed", "-i", "s/LXCARCH/${System.getProperty("os.arch")}/", "$lxcPath/config"))
        Run.user(Any(), listOf("cp", "-fpr", "$TOOLS_SRC/data/configs/waydroid.seccomp", "$lxcPath/waydroid.seccomp"))

        if (getAppArmorStatus(work)) {
            Run.user(Any(), listOf("sed", "-i", "-E",
                "/lxc.aa_profile|lxc.apparmor.profile/ s/unconfined/lxc-waydroid/g", "$lxcPath/config"))
        }

        val nodes = generateNodesLxcConfig(configPath, work)
        val tmpPath = "$work/config_nodes"
        File(tmpPath).writeText(nodes.joinToString("\n") + "\n")
        Run.user(Any(), listOf("mv", tmpPath, lxcPath))
        File("$lxcPath/config_session").createNewFile()
    }

    fun generateNodesLxcConfig(configPath: String, work: String): List<String> {
        val cfg = load(configPath)
        val nodes = mutableListOf<String>()

        fun entry(src: String, dist: String? = null, type: String = "none",
                  opts: String = "bind,create=file,optional 0 0", check: Boolean = true): Boolean {
            if (check && !File(src).exists()) return false
            val d = dist ?: src.drop(1)
            nodes.add("lxc.mount.entry = $src $d $type $opts")
            return true
        }

        entry("tmpfs", "dev", "tmpfs", "nosuid 0 0", false)
        for (dev in listOf("/dev/zero","/dev/null","/dev/full","/dev/ashmem","/dev/fuse","/dev/ion","/dev/tty"))
            entry(dev)
        entry("/dev/char", opts = "bind,create=dir,optional 0 0")
        for (dev in listOf("/dev/kgsl-3d0","/dev/mali0","/dev/pvr_sync","/dev/pmsg0","/dev/dxg")) entry(dev)

        val (render, _) = Gpu.getDriNode(cfg)
        if (render.isNotEmpty()) entry(render)

        for (glob in listOf("/dev/fb*", "/dev/graphics/fb*", "/dev/video*", "/dev/dma_heap/*"))
            File(glob.substringBeforeLast("/")).listFiles { f -> f.name.matches(Regex(glob.substringAfterLast("/").replace("*", ".*"))) }
                ?.forEach { entry(it.absolutePath) }

        val binder = cfg["waydroid"]?.get("binder") ?: "binder"
        val vndbinder = cfg["waydroid"]?.get("vndbinder") ?: "vndbinder"
        val hwbinder = cfg["waydroid"]?.get("hwbinder") ?: "hwbinder"
        entry("/dev/$binder", "dev/binder", check = false)
        entry("/dev/$vndbinder", "dev/vndbinder", check = false)
        entry("/dev/$hwbinder", "dev/hwbinder", check = false)

        val vendorType = cfg["waydroid"]?.get("vendor_type") ?: "MAINLINE"
        if (vendorType != "MAINLINE") {
            if (!entry("/dev/hwbinder", "dev/host_hwbinder")) throw RuntimeException("Binder node hwbinder of host not found")
            entry("/vendor", "vendor_extra", opts = "rbind,optional 0 0")
        }

        entry("none", "dev/pts", "devpts", "defaults,mode=644,ptmxmode=666,create=dir 0 0", false)
        entry("/dev/uhid")
        entry("/dev/net/tun", "dev/tun")
        entry("/sys/module/lowmemorykiller", opts = "bind,create=dir,optional 0 0")
        entry(defaults["host_perms"]!!, "vendor/etc/host-permissions", opts = "bind,optional 0 0")
        entry("/dev/sw_sync")
        entry("/sys/kernel/debug", opts = "rbind,create=dir,optional 0 0")
        entry("/sys/class/leds/vibrator", opts = "bind,create=dir,optional 0 0")
        entry("/sys/devices/virtual/timed_output/vibrator", opts = "bind,create=dir,optional 0 0")
        for (dev in listOf("/dev/Vcodec","/dev/MTK_SMI","/dev/mdp_sync","/dev/mtk_cmdq")) entry(dev)
        entry("tmpfs", "mnt_extra", "tmpfs", "nodev 0 0", false)
        entry("/mnt/wslg", "mnt_extra/wslg", opts = "rbind,create=dir,optional 0 0")
        for (d in listOf("tmp","var","run")) entry("tmpfs", d, "tmpfs", "nodev 0 0", false)
        entry("/system/etc/libnfc-nci.conf", opts = "bind,optional 0 0")

        return nodes
    }

    fun generateSessionLxcConfig(configPath: String, work: String, session: Map<String, String>) {
        val nodes = mutableListOf<String>()
        val containerXdg = defaults["container_xdg_runtime_dir"]!!

        fun entry(src: String, dist: String? = null, type: String = "none", opts: String = "rbind,create=file 0 0"): Boolean {
            if (src.contains('\n') || src.contains('\r')) { log.warning("Illegal char in mount path: $src"); return false }
            val d = dist ?: src.drop(1)
            nodes.add("lxc.mount.entry = $src $d $type $opts")
            return true
        }

        if (!entry("tmpfs", containerXdg.drop(1), "tmpfs", "create=dir 0 0"))
            throw RuntimeException("Failed to create XDG_RUNTIME_DIR mount point")

        val waylandHost = File(session["xdg_runtime_dir"]!!, session["wayland_display"]!!).canonicalPath
        val waylandContainer = File(containerXdg, defaults["container_wayland_display"]!!).canonicalPath
        if (!entry(waylandHost, waylandContainer.drop(1))) throw RuntimeException("Failed to bind Wayland socket")

        val pulseHost = File(session["pulse_runtime_path"]!!, "native").absolutePath
        val pulseContainer = File(defaults["container_pulse_runtime_path"]!!, "native").absolutePath
        entry(pulseHost, pulseContainer.drop(1))

        if (!entry(session["waydroid_data"]!!, "data", opts = "rbind 0 0"))
            throw RuntimeException("Failed to bind userdata")

        val lxcPath = "${defaults["lxc"]}/waydroid"
        val tmpPath = "$work/config_session"
        File(tmpPath).writeText(nodes.joinToString("\n") + "\n")
        Run.user(Any(), listOf("mv", tmpPath, lxcPath))
    }

    fun makeBaseProps(configPath: String, work: String) {
        val cfg = load(configPath)
        val vendorType = cfg["waydroid"]?.get("vendor_type") ?: "MAINLINE"
        val props = mutableListOf<String>()

        if (!File("/dev/ashmem").exists()) props.add("sys.use_memfd=true")
        props.add("ro.adb.secure=1")
        props.add("ro.debuggable=0")

        val (dri, _) = Gpu.getDriNode(cfg)
        var gralloc = findHal("gralloc")
        if (gralloc.isEmpty() && dri.isNotEmpty()) { gralloc = "gbm"; props.add("gralloc.gbm.device=$dri") }
        else if (gralloc.isEmpty()) { gralloc = "default"; props.add("debug.stagefright.ccodec=0") }
        props.add("ro.hardware.gralloc=$gralloc")

        val egl = Props.hostGet(Any(), "ro.hardware.egl").ifEmpty { if (dri.isNotEmpty()) "mesa" else "swiftshader" }
        if (egl.isNotEmpty()) props.add("ro.hardware.egl=$egl")

        val opengles = Props.hostGet(Any(), "ro.opengles.version").ifEmpty { "196610" }
        props.add("ro.opengles.version=$opengles")

        val imagesPath = cfg["waydroid"]?.get("images_path") ?: defaults["images_path"]!!
        if (imagesPath !in preinstalledImagesPaths) {
            props.add("waydroid.system_ota=${cfg["waydroid"]?.get("system_ota")}")
            props.add("waydroid.vendor_ota=${cfg["waydroid"]?.get("vendor_ota")}")
        } else props.add("waydroid.updater.disabled=true")

        props.add("waydroid.tools_version=$VERSION")
        if (vendorType == "MAINLINE") props.add("ro.vndk.lite=true")

        for (product in listOf("brand","device","manufacturer","model","name")) {
            val v = Props.hostGet(Any(), "ro.product.vendor.$product")
            if (v.isNotEmpty()) props.add("ro.product.waydroid.$product=$v")
            else {
                val dtFile = File("/proc/device-tree/$product")
                if (dtFile.exists()) {
                    val v2 = dtFile.readText().trim().trimEnd('\u0000')
                    if (v2.isNotEmpty()) props.add("ro.product.waydroid.$product=$v2")
                }
            }
        }

        val fp = Props.hostGet(Any(), "ro.vendor.build.fingerprint")
        if (fp.isNotEmpty()) props.add("ro.build.fingerprint=$fp")

        // Override with [properties] section
        cfg["properties"]?.forEach { (k, v) ->
            props.removeAll { it.startsWith("$k=") }
            props.add("$k=$v")
        }

        File("$work/waydroid_base.prop").writeText(props.joinToString("\n") + "\n")
    }

    fun setupHostPerms(work: String) {
        val hostPerms = File(defaults["host_perms"]!!)
        if (!hostPerms.exists()) hostPerms.mkdir()
        if (Props.hostGet(Any(), "ro.treble.enabled") != "true") return
        val sku = Props.hostGet(Any(), "ro.boot.product.hardware.sku")
        val copyList = mutableListOf<File>()
        for (base in listOf("/vendor/etc/permissions", "/odm/etc/permissions")) {
            copyList.addAll(File(base).listFiles { f -> f.name.matches(Regex("android\\.hardware\\.nfc\\..*")) } ?: emptyArray())
            File("$base/android.hardware.consumerir.xml").takeIf { it.exists() }?.let { copyList.add(it) }
        }
        if (sku.isNotEmpty()) {
            for (base in listOf("/odm/etc/permissions/sku_$sku")) {
                copyList.addAll(File(base).listFiles { f -> f.name.matches(Regex("android\\.hardware\\.nfc\\..*")) } ?: emptyArray())
                File("$base/android.hardware.consumerir.xml").takeIf { it.exists() }?.let { copyList.add(it) }
            }
        }
        copyList.forEach { it.copyTo(File(hostPerms, it.name), overwrite = true) }
    }

    fun status(work: String): String = runCatching {
        (Run.user(Any(), listOf("lxc-info", "-P", defaults["lxc"]!!, "-n", "waydroid", "-sH"),
            outputReturn = true) as? String)?.trim() ?: "STOPPED"
    }.getOrElse { "STOPPED" }

    fun waitForRunning(work: String) {
        var timeout = 10
        while (status(work) != "RUNNING" && timeout > 0) {
            log.info("waiting $timeout seconds for container to start...")
            Thread.sleep(1000); timeout--
        }
        if (status(work) != "RUNNING") throw RuntimeException("container failed to start")
    }

    fun start(work: String) {
        Run.user(Any(), listOf("lxc-start", "-P", defaults["lxc"]!!, "-F", "-n", "waydroid", "--", "/init"), output = "background")
        waitForRunning(work)
        runCatching { File(defaults["work"]!! + "/waydroid.log").setReadable(true, false) }
    }

    fun stop(work: String) {
        Run.user(Any(), listOf("lxc-stop", "-P", defaults["lxc"]!!, "-n", "waydroid", "-k"))
    }

    fun freeze(work: String) {
        Run.user(Any(), listOf("lxc-freeze", "-P", defaults["lxc"]!!, "-n", "waydroid"))
    }

    fun unfreeze(work: String) {
        Run.user(Any(), listOf("lxc-unfreeze", "-P", defaults["lxc"]!!, "-n", "waydroid"))
    }

    fun shell(work: String, uid: String?, gid: String?, context: String?,
              nolsm: Boolean, allcaps: Boolean, nocgroup: Boolean, command: List<String>) {
        val state = status(work)
        if (state == "FROZEN") unfreeze(work)
        else if (state != "RUNNING") { log.severe("WayDroid container is $state"); return }

        val cmd = mutableListOf("lxc-attach", "-P", defaults["lxc"]!!, "-n", "waydroid", "--clear-env")
        // env vars
        val envOpts = ANDROID_ENV.flatMap { (k, v) -> listOf("--set-var", "$k=$v") }
        cmd.addAll(envOpts)
        if (uid != null) cmd.add("--uid=$uid")
        if (gid != null) cmd.add("--gid=$gid") else if (uid != null) cmd.add("--gid=$uid")
        if (nolsm || allcaps || nocgroup) {
            val privs = buildList {
                if (nolsm) add("LSM"); if (allcaps) add("CAP"); if (nocgroup) add("CGROUP")
            }.joinToString("|")
            cmd.add("--elevated-privileges=$privs")
        }
        if (context != null && !nolsm) cmd.add("--context=$context")
        cmd.add("--")
        cmd.addAll(if (command.isNotEmpty()) command else listOf("/system/bin/sh"))

        ProcessBuilder(cmd).inheritIO().start().waitFor()
        if (state == "FROZEN") freeze(work)
    }

    fun logcat(work: String, logcatArgs: List<String>) {
        shell(work, null, null, null, false, false, false, listOf("/system/bin/logcat") + logcatArgs)
    }

    private fun findHal(hardware: String): String {
        val props = listOf("ro.hardware.$hardware","ro.hardware","ro.product.board","ro.arch","ro.board.platform")
        for (p in props) {
            val prop = Props.hostGet(Any(), p)
            if (prop.isEmpty()) continue
            for (lib in listOf("/odm/lib","/odm/lib64","/vendor/lib","/vendor/lib64","/system/lib","/system/lib64")) {
                if (File("$lib/hw/$hardware.$prop.so").isFile) return prop
            }
        }
        return ""
    }

    private fun which(cmd: String) = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .any { File(it, cmd).canExecute() }
}
