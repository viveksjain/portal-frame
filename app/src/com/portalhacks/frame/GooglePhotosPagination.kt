package com.portalhacks.frame

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

private data class ContinuationToken(val value: String?)

private fun parseContinuationToken(rawToken: Any?): ContinuationToken? =
    when {
        rawToken == JSONObject.NULL -> ContinuationToken(null)
        rawToken is String -> ContinuationToken(rawToken.ifEmpty { null })
        else -> null
    }

/** Helpers for following the undocumented continuation pages of a public Google Photos album. */
internal object GooglePhotosPagination {
    data class Request(
        val dataKey: String,
        val albumKey: String,
        val authKey: String,
    )

    data class Page(val itemsJson: String, val nextToken: String?)

    private data class InitialPage(val token: String?)

    private const val RPC_ID = "snAcKc"
    private const val DEFAULT_MAX_PAGES = 1_000
    private val requestPattern =
        Pattern.compile(
            "['\"]([^'\"]+)['\"]\\s*:\\s*\\{\\s*id\\s*:\\s*['\"]$RPC_ID['\"]" +
                "[^}]*?request:\\s*\\[\\s*\"([A-Za-z0-9_-]+)\"\\s*," +
                "\\s*null\\s*,\\s*null\\s*,\\s*\"([A-Za-z0-9_-]+)\"",
            Pattern.DOTALL,
        )
    private val callbackKeyPattern = Pattern.compile("key:\\s*['\"]([^'\"]+)['\"]")

    fun extractRequest(html: String): Request? {
        val match = requestPattern.matcher(html)
        return if (match.find()) Request(match.group(1)!!, match.group(2)!!, match.group(3)!!) else null
    }

    fun extractFirstPageToken(html: String): String? {
        val dataKey = extractRequest(html)?.dataKey ?: return null
        var searchFrom = 0
        while (true) {
            val callback = html.indexOf("AF_initDataCallback(", searchFrom)
            if (callback < 0) return null
            initialPageAt(html, callback, dataKey)?.let { return it.token }
            searchFrom = callback + 1
        }
    }

    private fun initialPageAt(
        html: String,
        callback: Int,
        expectedDataKey: String,
    ): InitialPage? {
        val scriptEnd = html.indexOf("</script>", callback).takeIf { it >= 0 } ?: html.length
        val nextCallback = html.indexOf("AF_initDataCallback(", callback + 1).takeIf { it >= 0 } ?: html.length
        val callbackEnd = minOf(scriptEnd, nextCallback)
        val keyMatch = callbackKeyPattern.matcher(html).apply { region(callback, callbackEnd) }
        if (!keyMatch.find() || keyMatch.group(1) != expectedDataKey) return null

        val data = html.indexOf("data:", callback)
        if (data < 0 || data >= callbackEnd) {
            paginationFailure("callback has no data")
        }
        val array =
            extractArray(html, data + "data:".length, callbackEnd)
                ?: paginationFailure("callback data is malformed")
        return parseInitialPage(array)
    }

    private fun parseInitialPage(array: String): InitialPage {
        val payload =
            try {
                JSONArray(array)
            } catch (exception: JSONException) {
                paginationFailure("callback data is malformed", exception)
            }
        if (payload.length() <= 2 || payload.optJSONArray(1) == null) {
            paginationFailure("callback has an unexpected shape")
        }
        val token =
            parseContinuationToken(payload.opt(2))
                ?: paginationFailure("callback token is malformed")
        return InitialPage(token.value)
    }

    private fun paginationFailure(
        detail: String,
        cause: Throwable? = null,
    ): Nothing = throw IOException("Google Photos pagination $detail", cause)

    fun parseBatchResponse(body: String): Page? {
        val responseLine =
            body.lineSequence().firstOrNull { it.trimStart().startsWith("[[") }
                ?: return null
        return try {
            val outer = JSONArray(responseLine.trim())
            val payload = findRpcPayload(outer) ?: return null
            val data = JSONArray(payload)
            val items = data.optJSONArray(1) ?: return null
            if (data.length() <= 2) return null
            val token = parseContinuationToken(data.opt(2)) ?: return null
            Page(items.toString(), token.value)
        } catch (ignored: JSONException) {
            null
        }
    }

    private fun findRpcPayload(outer: JSONArray): String? {
        for (i in 0 until outer.length()) {
            val entry = outer.optJSONArray(i)
            if (entry != null && entry.optString(0) == "wrb.fr" && entry.optString(1) == RPC_ID) {
                return entry.optString(2, "").ifEmpty { null }
            }
        }
        return null
    }

    fun buildFormBody(
        request: Request,
        pageToken: String,
    ): String {
        val inner = JSONArray().put(request.albumKey).put(pageToken).put(null).put(request.authKey).toString()
        val rpc = JSONArray().put(RPC_ID).put(inner).put(null).put("generic")
        val envelope = JSONArray().put(JSONArray().put(rpc)).toString()
        return "f.req=" + URLEncoder.encode(envelope, "UTF-8")
    }

    fun collectSlides(
        firstPage: List<Slide>,
        firstToken: String,
        maxPages: Int = DEFAULT_MAX_PAGES,
        fetch: (String) -> Page,
        parse: (String) -> List<Slide>,
    ): List<Slide> {
        require(maxPages > 0) { "maxPages must be positive" }
        val merged = ArrayList<Slide>()
        val seenIds = HashSet<String>()
        val seenTokens = HashSet<String>()

        fun addUnique(slides: List<Slide>) {
            for (slide in slides) {
                if (seenIds.add(slide.id)) merged.add(slide)
            }
        }

        addUnique(firstPage)
        var token: String? = firstToken
        var pageCount = 0
        while (token != null && pageCount < maxPages) {
            if (!seenTokens.add(token)) {
                throw IOException("Google Photos pagination token repeated")
            }
            val page = fetch(token)
            addUnique(parse(page.itemsJson.replace("\\/", "/")))
            token = page.nextToken
            pageCount++
        }
        if (token != null) {
            throw IOException("Google Photos album exceeded the $maxPages-page safety limit")
        }
        return merged
    }

    private fun extractArray(
        text: String,
        start: Int,
        end: Int,
    ): String? {
        val open = text.indexOf('[', start)
        if (open < 0 || open >= end) return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in open until end) {
            val c = text[i]
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    quoted = false
                }
                continue
            }
            when (c) {
                '"' -> quoted = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(open, i + 1)
                }
            }
        }
        return null
    }
}
