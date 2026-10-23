package ru.ytkab0bp.slicebeam.utils

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Validates a configured printer API address before a file upload starts. */
object PrinterUploadUrl {
    @JvmStatic
    fun baseUrl(configuredHost: String?): String? {
        val host = configuredHost?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val candidate = if (host.contains("://")) host else "http://$host"
        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
                uri.port > 65535 || uri.port == 0
            ) null else uri.toString().trimEnd('/')
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun endpoint(configuredHost: String?, apiPath: String): String? =
        baseUrl(configuredHost)?.let { "$it/${apiPath.trimStart('/')}" }
}
