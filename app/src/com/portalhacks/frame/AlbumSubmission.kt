package com.portalhacks.frame

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses and applies one album URL submitted by the LAN web page. */
internal object AlbumSubmission {
    enum class Result {
        ADDED,
        DUPLICATE,
        INVALID,
    }

    fun process(
        body: ByteArray,
        supports: (String) -> Boolean,
        add: (String) -> Boolean,
    ): Result {
        val url = formValue(body, "url")?.trim().orEmpty()
        if (url.isEmpty() || !supports(url)) return Result.INVALID
        return if (add(url)) Result.ADDED else Result.DUPLICATE
    }

    private fun formValue(
        body: ByteArray,
        name: String,
    ): String? {
        val form = String(body, StandardCharsets.UTF_8)
        return form.split('&').firstNotNullOfOrNull { field ->
            val eq = field.indexOf('=')
            if (eq < 0) {
                null
            } else {
                val key = decode(field.substring(0, eq))
                if (key == name) decode(field.substring(eq + 1)) else null
            }
        }
    }

    private fun decode(value: String): String? =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            null
        }
}
