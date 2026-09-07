package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder

class GooglePhotosPaginationTest {
    @Test
    fun `extracts album keys and first continuation token`() {
        val html =
            """
            <script>
            var AF_dataServiceRequests = {
              'ds:1': {id:'snAcKc',ext:7.1E7,request:["album-key",null,null,"auth-key"]}
            };
            </script>
            <script>AF_initDataCallback({key:'ds:unrelated',data:[null,[],null],sideChannel:{}});</script>
            <script>AF_initDataCallback({key:'ds:also-unrelated',data:[null,[],"wrong-token"],sideChannel:{}});</script>
            <script>AF_initDataCallback({key:'ds:1',data:[null,[],"page-2"],sideChannel:{}});</script>
            """.trimIndent()

        assertEquals(
            GooglePhotosPagination.Request("ds:1", "album-key", "auth-key"),
            GooglePhotosPagination.extractRequest(html),
        )
        assertEquals("page-2", GooglePhotosPagination.extractFirstPageToken(html))
    }

    @Test
    fun `returns no continuation token for an unpaginated album`() {
        val html =
            """
            <script>
            var AF_dataServiceRequests = {
              'ds:1': {id:'snAcKc',request:["album-key",null,null,"auth-key"]}
            };
            </script>
            <script>AF_initDataCallback({key:'ds:1',data:[null,[],null],sideChannel:{}});</script>
            """.trimIndent()

        assertNull(GooglePhotosPagination.extractFirstPageToken(html))
    }

    @Test
    fun `rejects malformed pagination data from the matching callback`() {
        val html =
            """
            <script>
            var AF_dataServiceRequests = {
              'ds:1': {id:'snAcKc',request:["album-key",null,null,"auth-key"]}
            };
            </script>
            <script>AF_initDataCallback({key:'ds:1',data:[not-json],sideChannel:{}});</script>
            """.trimIndent()

        assertThrows(IOException::class.java) {
            GooglePhotosPagination.extractFirstPageToken(html)
        }
    }

    @Test
    fun `parses a batchexecute page and its next token`() {
        val item =
            "[\"AF1Qip-photo-2\",[\"https://lh3.googleusercontent.com/photo-2\",1920,1080]," +
                "1700000000000,\"dedup\",0,1700000000001]"
        val payload = "[null,[$item],\"page-3\"]"
        val outer = "[[\"wrb.fr\",\"snAcKc\",${jsonString(payload)},null,null,\"generic\"]]"

        val page = GooglePhotosPagination.parseBatchResponse(")]}'\n\n$outer")

        assertEquals("page-3", page?.nextToken)
        assertEquals("[$item]", page?.itemsJson)
    }

    @Test
    fun `accepts only null as a terminal continuation token`() {
        val terminal = GooglePhotosPagination.parseBatchResponse(batchResponse("[null,[],null]"))

        assertNull(terminal?.nextToken)
        assertEquals("[]", terminal?.itemsJson)

        for (payload in listOf("[null,[]]", "[null,[],\"\"]", "[null,[],7]")) {
            assertNull(GooglePhotosPagination.parseBatchResponse(batchResponse(payload)))
        }
    }

    @Test
    fun `builds the snAcKc continuation request body`() {
        val body =
            GooglePhotosPagination.buildFormBody(
                GooglePhotosPagination.Request("ds:1", "album-key", "auth-key"),
                "page-2",
            )

        assertEquals("f.req", body.substringBefore('='))
        assertEquals(
            "[[[\"snAcKc\",\"[\\\"album-key\\\",\\\"page-2\\\",null,\\\"auth-key\\\"]\",null,\"generic\"]]]",
            URLDecoder.decode(body.substringAfter('='), "UTF-8"),
        )
    }

    @Test
    fun `consumes every page and rejects a repeated continuation token`() {
        val pages =
            mapOf(
                "page-2" to GooglePhotosPagination.Page("[\"second\"]", "page-3"),
                "page-3" to GooglePhotosPagination.Page("[\"third\"]", null),
            )
        val consumed = ArrayList<String>()

        GooglePhotosPagination.collectSlides(
            firstPage = emptyList(),
            firstToken = "page-2",
            fetch = { pages.getValue(it) },
            parse = {
                consumed.add(it)
                emptyList()
            },
        )

        assertEquals(listOf("[\"second\"]", "[\"third\"]"), consumed)

        assertThrows(IOException::class.java) {
            GooglePhotosPagination.collectSlides(
                firstPage = emptyList(),
                firstToken = "loop",
                fetch = { GooglePhotosPagination.Page("[]", "loop") },
                parse = { emptyList() },
            )
        }
    }

    @Test
    fun `merges continuation pages in order without repeating photos`() {
        val first = Slide("photo-1", null)
        val repeated = Slide("photo-1", null)
        val second = Slide("photo-2", null)
        val third = Slide("photo-3", null)
        val parsed =
            mapOf(
                "page-2-items" to listOf(repeated, second),
                "page-3-items" to listOf(third),
            )

        val pages =
            mapOf(
                "page-2" to GooglePhotosPagination.Page("page-2-items", "page-3"),
                "page-3" to GooglePhotosPagination.Page("page-3-items", null),
            )

        val merged =
            GooglePhotosPagination.collectSlides(
                firstPage = listOf(first),
                firstToken = "page-2",
                fetch = { pages.getValue(it) },
                parse = { parsed.getValue(it) },
            )

        assertEquals(listOf("photo-1", "photo-2", "photo-3"), merged.map { it.id })
    }

    @Test
    fun `stops at the configured page safety limit`() {
        assertThrows(IOException::class.java) {
            GooglePhotosPagination.collectSlides(
                firstPage = emptyList(),
                firstToken = "page-1",
                maxPages = 2,
                fetch = { token ->
                    val number = token.substringAfter('-').toInt()
                    GooglePhotosPagination.Page("[]", "page-${number + 1}")
                },
                parse = { emptyList() },
            )
        }
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    else -> append(c)
                }
            }
            append('"')
        }

    private fun batchResponse(payload: String): String = ")]}'\n\n[[\"wrb.fr\",\"snAcKc\",${jsonString(payload)},null,null,\"generic\"]]"
}
