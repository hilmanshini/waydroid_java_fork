/*
 * Copyright 2026 Hilman
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.interfaces

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

private interface GObject : Library {
    fun g_type_init()
}

/** GBinderReader is a stack-allocated struct (2 pointers + 2 size_t = 32 bytes on x86_64) */
@Structure.FieldOrder("data", "end", "ptr", "base")
open class GBinderReader : Structure() {
    @JvmField var data: Pointer? = null
    @JvmField var end: Pointer? = null
    @JvmField var ptr: Pointer? = null
    @JvmField var base: Pointer? = null
    class ByValue : GBinderReader(), Structure.ByValue
}

interface LibGBinder : Library {
    companion object {
        val INSTANCE: LibGBinder by lazy {
//            runCatching { Native.load("gobject-2.0", GObject::class.java).g_type_init() }
            // use system libgbinder directly, skip bundled extraction
            Native.load("gbinder", LibGBinder::class.java)
        }
//        val INSTANCE: LibGBinder by lazy {
//            runCatching { Native.load("gobject-2.0", GObject::class.java).g_type_init() }
//
//            // Extract bundled .so files from jar resources to a temp dir and load from there
//            val tmpDir = java.io.File(System.getProperty("java.io.tmpdir"), "waydroid-native").also { it.mkdirs() }
//            val arch = "linux-x86-64" // extend for aarch64 if needed
//
//            listOf("libglib-2.0.so", "libglibutil.so", "libgobject-2.0.so", "libgbinder.so").forEach { name ->
//                val dest = java.io.File(tmpDir, name)
//                if (!dest.exists()) {
//                    val stream = LibGBinder::class.java.getResourceAsStream("/native/$arch/$name")
//                        ?: error("Bundled native lib not found: /native/$arch/$name")
//                    stream.use { it.copyTo(dest.outputStream()) }
//                }
//                // Pre-load dependencies in order so the linker resolves them
//                if (name != "libgbinder.so") System.load(dest.absolutePath)
//            }
//
//            val gbinderPath = java.io.File(tmpDir, "libgbinder.so").absolutePath
//            Native.load(gbinderPath, LibGBinder::class.java)
//        }
    }

    // ServiceManager
    // replace single-arg version with 3-arg version
    // add both versions
    fun gbinder_servicemanager_new(dev: String): Pointer?
    fun gbinder_servicemanager_new2(dev: String, smProtocol: String, binderProtocol: String): Pointer?
    fun gbinder_servicemanager_unref(sm: Pointer)
    fun gbinder_servicemanager_is_present(sm: Pointer): Boolean
    fun gbinder_servicemanager_get_service_sync(sm: Pointer, name: String, status: IntArray?): Pointer?

    fun gbinder_servicemanager_list(sm: Pointer): com.sun.jna.ptr.PointerByReference?

    // Client
    fun gbinder_client_new(remote: Pointer, iface: String): Pointer?
    fun gbinder_client_unref(client: Pointer)
    fun gbinder_client_new_request(client: Pointer): Pointer?
    fun gbinder_client_transact_sync_reply(client: Pointer, code: Int, req: Pointer?, status: IntArray?): Pointer?

    // LocalRequest (write)
    fun gbinder_local_request_unref(req: Pointer)
    fun gbinder_local_request_append_int32(req: Pointer, value: Int)
    fun gbinder_local_request_append_string16(req: Pointer, value: String)

    // RemoteReply (read) — uses GBinderReader
    fun gbinder_remote_reply_unref(reply: Pointer)
    fun gbinder_remote_reply_init_reader(reply: Pointer, reader: GBinderReader)

    // Reader
    fun gbinder_reader_read_int32(reader: GBinderReader, out: IntArray): Boolean
    fun gbinder_reader_read_string16(reader: GBinderReader): String?

    // RemoteObject
    fun gbinder_remote_object_unref(obj: Pointer)
}
