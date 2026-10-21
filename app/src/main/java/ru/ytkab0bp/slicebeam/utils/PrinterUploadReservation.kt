package ru.ytkab0bp.slicebeam.utils

import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Serializes send/start flows for the same origin, even through different proxy paths. */
class PrinterUploadReservation {
    private val activeOrigins = ConcurrentHashMap.newKeySet<String>()

    fun reserve(configuredHost: String): String? {
        val base = PrinterUploadUrl.baseUrl(configuredHost) ?: return null
        val uri = URI(base)
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
        val origin = "$scheme://${uri.host.lowercase(Locale.ROOT)}:$port"
        return origin.takeIf(activeOrigins::add)
    }

    fun release(origin: String) { activeOrigins.remove(origin) }
}
