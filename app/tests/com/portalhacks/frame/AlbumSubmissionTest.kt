package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlbumSubmissionTest {
    @Test
    fun `valid form submission decodes trims and adds album URL`() {
        var addedUrl: String? = null

        val result =
            AlbumSubmission.process(
                "url=+https%3A%2F%2Fphotos.app.goo.gl%2Fabc123+".toByteArray(),
                supports = { it.startsWith("https://photos.app.goo.gl/") },
                add = {
                    addedUrl = it
                    true
                },
            )

        assertEquals("https://photos.app.goo.gl/abc123", addedUrl)
        assertEquals(AlbumSubmission.Result.ADDED, result)
    }

    @Test
    fun `unsupported URL is rejected without adding it`() {
        var addCalled = false

        val result =
            AlbumSubmission.process(
                "url=https%3A%2F%2Fexample.com%2Fnot-an-album".toByteArray(),
                supports = { false },
                add = {
                    addCalled = true
                    true
                },
            )

        assertFalse(addCalled)
        assertEquals(AlbumSubmission.Result.INVALID, result)
    }

    @Test
    fun `malformed form encoding is rejected without adding it`() {
        var addCalled = false

        val result =
            AlbumSubmission.process(
                "url=https%3A%2F%2Fphotos.app.goo.gl%2Fbad%ZZ".toByteArray(),
                supports = { true },
                add = {
                    addCalled = true
                    true
                },
            )

        assertFalse(addCalled)
        assertEquals(AlbumSubmission.Result.INVALID, result)
    }

    @Test
    fun `existing album URL is reported as duplicate`() {
        val result =
            AlbumSubmission.process(
                "url=https%3A%2F%2Fwww.icloud.com%2Fsharedalbum%2F%23B0abc".toByteArray(),
                supports = { true },
                add = { false },
            )

        assertEquals(AlbumSubmission.Result.DUPLICATE, result)
    }
}
