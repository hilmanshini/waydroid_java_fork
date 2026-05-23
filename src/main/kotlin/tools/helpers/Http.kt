/*
 * Copyright 2021 Oliver Smith
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package tools.helpers

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.logging.Logger

private val log = Logger.getLogger("Http")

object Http {
    private val client = OkHttpClient()

    fun download(
        args: Any,
        url: String,
        prefix: String,
        cache: Boolean = true,
        allow404: Boolean = false
    ): String? {
        val workDir = (args as? Map<*, *>)?.get("work")?.toString()
            ?: throw IllegalArgumentException("args must contain 'work' key")
        val cacheDir = File("$workDir/cache_http").also { it.mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val path = File(cacheDir, "${prefix.replace("/", "_")}_$hash")

        if (path.exists()) {
            if (cache) return path.absolutePath
            path.delete()
        }

        log.info("Downloading $url")
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404 && allow404) {
                    log.warning("WARNING: file not found: $url")
                    return null
                }
                throw RuntimeException("HTTP ${response.code}: $url")
            }
            val total = response.header("content-length")?.toLongOrNull() ?: 0L
            var downloaded = 0L
            response.body!!.byteStream().use { input ->
                path.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) print("\r[Downloading] ${"%.2f".format(downloaded / 1e6)} MB / ${"%.2f".format(total / 1e6)} MB")
                    }
                }
            }
            if (total > 0) println()
        }
        return path.absolutePath
    }

    fun retrieve(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, ByteArray> {
        return try {
            val req = Request.Builder().url(url)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
            client.newCall(req).execute().use { r ->
                r.code to (r.body?.bytes() ?: ByteArray(0))
            }
        } catch (e: IllegalArgumentException) { -1 to ByteArray(0) }
          catch (e: Exception) { -2 to ByteArray(0) }
    }
}
