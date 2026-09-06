package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowRefreshTest {
    @Test fun survivingInspectedPhotoRendersItsNewPair() {
        val refreshed = listOf(portrait("a"), portrait("c"), landscape("b"))

        val target =
            SlideshowRefresh.resolve(
                slides = refreshed,
                inspectedId = "a",
                requestedId = null,
                requestedInstant = false,
            )

        assertEquals(0, target.index)
        assertTrue(target.instant)
        assertEquals(
            listOf("a", "c"),
            SlideshowNavigation.frameSlides(
                slides = refreshed,
                start = target.index,
                pairs = true,
                screenPortrait = false,
            ).map { it.id },
        )
    }

    @Test fun canceledRefreshRenderRemainsQueuedUntilARetryCompletes() {
        val renders = SlideshowRefreshQueue()
        val target = SlideshowRefreshTarget(index = 0, instant = true)

        val canceledRequest = renders.queue(target)
        val retriedRequest = renders.queue(target)
        renders.complete(canceledRequest)

        assertEquals(target, renders.pending)
        renders.complete(retriedRequest)
        assertNull(renders.pending)
    }

    private fun portrait(id: String) = Slide(id, null, portrait = true)

    private fun landscape(id: String) = Slide(id, null, portrait = false)
}
