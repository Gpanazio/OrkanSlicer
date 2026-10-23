package ru.ytkab0bp.slicebeam.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets

/** Reads Moonraker's acknowledgement before a second request can start the print. */
object MoonrakerResponseInspector {
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    data class UploadAcknowledgement(val path: String, val printStarted: Boolean, val printQueued: Boolean)

    data class PrintStatus(val state: String, val filename: String?) {
        fun confirmsStartedFile(path: String): Boolean =
            (state.equals("printing", ignoreCase = true) || state.equals("paused", ignoreCase = true)) &&
                sameFile(filename, path)
    }

    /** Parse one lightweight print_stats query after a start request with an unclear result. */
    @JvmStatic
    fun printStatus(body: ByteArray?): PrintStatus? {
        val root = parseObject(body) ?: return null
        val result = childObject(root, "result") ?: return null
        val status = childObject(result, "status") ?: return null
        val printStats = childObject(status, "print_stats") ?: return null
        val state = string(printStats, "state")?.takeIf { it.isNotBlank() } ?: return null
        return PrintStatus(state, string(printStats, "filename")?.takeIf { it.isNotBlank() })
    }

    private fun sameFile(actual: String?, expected: String): Boolean {
        if (actual.isNullOrBlank()) return false
        return actual.trim().replace('\\', '/') == expected.trim().replace('\\', '/')
    }

    @JvmStatic
    fun uploadedFile(body: ByteArray?, expectedName: String? = null): UploadAcknowledgement? {
        val root = parseObject(body) ?: return null
        // File uploads are normally unwrapped; accept the result wrapper used by some proxies.
        val payload = childObject(root, "result") ?: root
        val item = childObject(payload, "item") ?: return null
        if (string(item, "root") != "gcodes") return null
        val path = string(item, "path")?.takeIf { it.isNotBlank() && safeRelativePath(it) } ?: return null
        // A valid-looking acknowledgement for another file must never be used for print/start.
        if (expectedName != null && path.substringAfterLast('/') != expectedName) return null
        val action = string(payload, "action")
        if (action != null && action !in setOf("create_file", "modify_file")) return null
        return UploadAcknowledgement(
            path,
            string(payload, "print_started") == "true" || string(root, "print_started") == "true",
            string(payload, "print_queued") == "true" || string(root, "print_queued") == "true",
        )
    }

    @JvmStatic
    fun printStartConfirmed(body: ByteArray?): Boolean {
        val parsed = parse(body) ?: return false
        val value = if (parsed.isJsonObject) parsed.asJsonObject.get("result") else parsed
        return value != null && value.isJsonPrimitive && value.asString.equals("ok", ignoreCase = true)
    }

    private fun safeRelativePath(path: String): Boolean = path.isNotEmpty() &&
        !path.startsWith('/') && !path.contains('\\') &&
        path.none { it.code < 32 || it.code == 127 } &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun childObject(root: JsonObject, key: String): JsonObject? =
        root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun string(root: JsonObject, key: String): String? =
        root.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun parseObject(body: ByteArray?): JsonObject? =
        parse(body)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun parse(body: ByteArray?): JsonElement? {
        if (body == null || body.isEmpty() || body.size > MAX_RESPONSE_BYTES) return null
        return try {
            JsonParser.parseString(body.toString(StandardCharsets.UTF_8))
        } catch (_: RuntimeException) {
            null
        }
    }
}
