package ru.ytkab0bp.slicebeam.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets

/** Interprets the file-upload acknowledgement, including whether printing actually began. */
object OctoPrintResponseInspector {
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    enum class UploadOutcome { UPLOADED, PROCESSING, PRINT_STARTED, SELECTED_NOT_STARTED, PRINT_NOT_STARTED }

    data class UploadAcknowledgement(
        val path: String,
        val processingDone: Boolean?,
        val selected: Boolean?,
        val printStarted: Boolean?,
    ) {
        fun outcome(printRequested: Boolean): UploadOutcome = when {
            printStarted == true -> UploadOutcome.PRINT_STARTED
            processingDone == false -> UploadOutcome.PROCESSING
            printRequested && selected == true -> UploadOutcome.SELECTED_NOT_STARTED
            printRequested -> UploadOutcome.PRINT_NOT_STARTED
            else -> UploadOutcome.UPLOADED
        }
    }

    @JvmStatic
    fun uploadedFile(body: ByteArray?): UploadAcknowledgement? {
        if (body == null || body.isEmpty() || body.size > MAX_RESPONSE_BYTES) return null
        val root = try {
            JsonParser.parseString(body.toString(StandardCharsets.UTF_8)).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val files = child(root, "files") ?: return null
        val local = child(files, "local") ?: return null
        if (string(local, "origin")?.let { it != "local" } == true) return null
        val path = (string(local, "path") ?: string(local, "name"))
            ?.takeIf(::safeRelativePath) ?: return null
        return UploadAcknowledgement(
            path,
            flag(root, "done"),
            flag(root, "effectiveSelect"),
            flag(root, "effectivePrint"),
        )
    }

    private fun child(root: JsonObject, key: String): JsonObject? =
        root.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun string(root: JsonObject, key: String): String? =
        root.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun flag(root: JsonObject, key: String): Boolean? =
        root.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private fun safeRelativePath(path: String): Boolean = path.isNotBlank() &&
        !path.startsWith('/') && !path.contains('\\') &&
        path.none { it.code < 32 || it.code == 127 } &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
