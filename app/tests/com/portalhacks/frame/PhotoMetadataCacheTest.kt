package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PhotoMetadataCacheTest {
    @Test fun storesTheGoogleFilenameInTheMatchingMd5JsonFile() {
        val dir = Files.createTempDirectory("photo-metadata").toFile()
        try {
            val id = "https://lh3.googleusercontent.com/photo=w1280"

            PhotoMetadataCache.writeName(dir, id, "P1070925.JPG")

            val metadata = dir.resolve("f6ae2385f1f4adc400a73a856bcb8bd2.json")
            assertEquals("{\"name\":\"P1070925.JPG\"}", metadata.readText())
            assertEquals("P1070925.JPG", PhotoMetadataCache.readName(dir, id))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun extractsTheFilenameFromGoogleContentDisposition() {
        assertEquals(
            "P1070925.JPG",
            PhotoMetadataCache.filenameFrom("inline;filename=\"P1070925.JPG\""),
        )
    }

    @Test fun ignoresResponsesWithoutAFilename() {
        assertNull(PhotoMetadataCache.filenameFrom("inline"))
    }

    @Test fun identifiesOnlyGooglePhotoCdnUrlsForMetadataStorage() {
        assertTrue(PhotoMetadataCache.isGooglePhoto("https://lh3.googleusercontent.com/photo=w1280"))
        assertFalse(PhotoMetadataCache.isGooglePhoto("https://cvws.icloud-content.com/photo"))
    }
}
