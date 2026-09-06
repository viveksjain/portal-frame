package com.portalhacks.frame

import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern

/** Small sidecar cache for metadata learned while a remote photo is downloaded. */
internal object PhotoMetadataCache {
    private val FILENAME: Pattern =
        Pattern.compile(
            "(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))",
            Pattern.CASE_INSENSITIVE,
        )

    fun key(id: String): String =
        try {
            val digest = MessageDigest.getInstance("MD5").digest(id.toByteArray(Charsets.UTF_8))
            String.format(Locale.ROOT, "%032x", BigInteger(1, digest))
        } catch (_: Exception) {
            Integer.toHexString(id.hashCode())
        }

    fun filenameFrom(contentDisposition: String?): String? {
        val matcher = FILENAME.matcher(contentDisposition ?: return null)
        if (!matcher.find()) return null
        val raw = matcher.group(1) ?: matcher.group(2) ?: return null
        val name = raw.trim().substringAfterLast('/').substringAfterLast('\\').trim()
        return name.ifEmpty { null }
    }

    fun isGooglePhoto(id: String): Boolean = id.startsWith("https://lh3.googleusercontent.com/")

    fun finishDownload(
        cacheDir: File,
        id: String,
        connection: HttpURLConnection,
        temporary: File,
        destination: File,
    ): Boolean {
        val saved = temporary.renameTo(destination)
        val filename = filenameFrom(connection.getHeaderField("Content-Disposition"))
        if (saved && filename != null && isGooglePhoto(id)) writeName(cacheDir, id, filename)
        return saved
    }

    fun writeName(
        cacheDir: File,
        id: String,
        name: String,
    ) {
        val clean = name.trim().ifEmpty { return }
        val destination = File(cacheDir, key(id) + ".json")
        val temporary = File(destination.absolutePath + ".tmp")
        try {
            cacheDir.mkdirs()
            FileOutputStream(temporary).use { out ->
                out.write(("{\"name\":\"" + escape(clean) + "\"}").toByteArray(Charsets.UTF_8))
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
            }
        } catch (_: Exception) {
            temporary.delete()
        }
    }

    fun readName(
        cacheDir: File,
        id: String,
    ): String? {
        val file = File(cacheDir, key(id) + ".json")
        return try {
            val json = file.readText(Charsets.UTF_8)
            if (!json.startsWith(PREFIX) || !json.endsWith(SUFFIX)) return null
            unescape(json.substring(PREFIX.length, json.length - SUFFIX.length)).ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun escape(value: String): String =
        buildString(value.length) {
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
                }
            }
        }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\' || index >= value.length) {
                result.append(char)
                continue
            }
            val escaped = value[index++]
            when {
                escaped == 'u' -> {
                    if (index + 4 > value.length) return ""
                    val code = value.substring(index, index + 4).toIntOrNull(16) ?: return ""
                    result.append(code.toChar())
                    index += 4
                }
                ESCAPES.containsKey(escaped) -> result.append(ESCAPES.getValue(escaped))
                else -> return ""
            }
        }
        return result.toString()
    }

    private const val PREFIX = "{\"name\":\""
    private const val SUFFIX = "\"}"
    private val ESCAPES =
        mapOf(
            '\\' to '\\',
            '"' to '"',
            'b' to '\b',
            'f' to '\u000C',
            'n' to '\n',
            'r' to '\r',
            't' to '\t',
        )
}
