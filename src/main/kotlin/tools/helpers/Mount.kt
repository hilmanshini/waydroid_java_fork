/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import java.io.File

object Mount {
    fun isMount(folder: String): Boolean {
        val real = File(folder).canonicalPath
        return File("/proc/mounts").useLines { lines ->
            lines.any { line ->
                val w = line.split(Regex("\\s+"))
                w.size >= 2 && (w[1] == real || w[0] == real)
            }
        }
    }

    fun bind(args: Any, source: String, destination: String, createFolders: Boolean = true, umount: Boolean = false) {
        if (isMount(destination)) {
            if (umount) umountAll(args, destination) else return
        }
        for (path in listOf(source, destination)) {
            if (!File(path).exists()) {
                if (createFolders) Run.user(args, listOf("mkdir", "-p", path))
                else throw RuntimeException("Mount failed, folder does not exist: $path")
            }
        }
        Run.user(args, listOf("mount", "-o", "bind", source, destination))
        if (!isMount(destination)) throw RuntimeException("Mount failed: $source -> $destination")
    }

    fun bindFile(args: Any, source: String, destination: String, createFolders: Boolean = false) {
        if (isMount(destination)) return
        if (!File(destination).exists()) {
            if (createFolders) {
                val dir = File(destination).parent
                if (dir != null && !File(dir).isDirectory) Run.user(args, listOf("mkdir", "-p", dir))
            }
            Run.user(args, listOf("touch", destination))
        }
        Run.user(args, listOf("mount", "-o", "bind", source, destination))
    }

    fun umountAllList(prefix: String, source: String = "/proc/mounts"): List<String> {
        val real = File(prefix).canonicalPath
        val deleted = """\040(deleted)"""
        val ret = mutableListOf<String>()
        File(source).forEachLine { line ->
            val w = line.split(Regex("\\s+"))
            if (w.size < 2) throw RuntimeException("Failed to parse line in $source: $line")
            var mp = w[1]
            if (mp.startsWith(real)) {
                if (mp.endsWith(deleted)) mp = mp.dropLast(deleted.length)
                ret.add(mp)
            }
        }
        return ret.sortedDescending()
    }

    fun umountAll(args: Any, folder: String) {
        val list = umountAllList(folder)
        list.forEach { Run.user(args, listOf("umount", it)) }
        list.forEach { if (isMount(it)) throw RuntimeException("Failed to umount: $it") }
    }

    fun mount(
        args: Any, source: String, destination: String,
        createFolders: Boolean = true, umount: Boolean = false,
        readonly: Boolean = true, mountType: String? = null,
        options: List<String>? = null, force: Boolean = true
    ) {
        if (isMount(destination)) {
            if (umount) umountAll(args, destination)
            else if (!force) return
        }
        if (!File(destination).exists()) {
            if (createFolders) Run.user(args, listOf("mkdir", "-p", destination))
            else throw RuntimeException("Mount failed, folder does not exist: $destination")
        }
        val extraArgs = mutableListOf<String>()
        val optArgs = mutableListOf<String>()
        if (mountType != null) extraArgs.addAll(listOf("-t", mountType))
        if (readonly) optArgs.add("ro")
        if (options != null) optArgs.addAll(options)
        if (optArgs.isNotEmpty()) extraArgs.addAll(listOf("-o", optArgs.joinToString(",")))
        Run.user(args, listOf("mount") + extraArgs + listOf(source, destination))
        if (!isMount(destination)) throw RuntimeException("Mount failed: $source -> $destination")
    }

    fun mountOverlay(
        args: Any, lowerDirs: List<String>, destination: String,
        upperDir: String? = null, workDir: String? = null,
        createFolders: Boolean = true, readonly: Boolean = true
    ) {
        val dirs = lowerDirs.toMutableList()
        val opts = mutableListOf("lowerdir=${lowerDirs.joinToString(":")}")
        if (upperDir != null && workDir != null) {
            dirs += listOf(upperDir, workDir)
            opts += listOf("upperdir=$upperDir", "workdir=$workDir")
        }
        if (Version.kernelVersion().zip(Version.versionTuple("4.17")).all { (a, b) -> a >= b }) opts.add("xino=off")
        dirs.forEach { dir ->
            if (!File(dir).exists()) {
                if (createFolders) Run.user(args, listOf("mkdir", "-p", dir))
                else throw RuntimeException("Mount failed, folder does not exist: $dir")
            }
        }
        mount(args, "overlay", destination, mountType = "overlay", options = opts, readonly = readonly, createFolders = createFolders, force = true)
    }
}
