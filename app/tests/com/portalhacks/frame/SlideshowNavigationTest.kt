package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideshowNavigationTest {
    @Test fun nextThenPreviousReturnsToPortraitPair() {
        val slides = listOf(portrait("a"), portrait("b"), landscape("c"))

        val next =
            SlideshowNavigation.nextStart(
                slides = slides,
                current = 0,
                pairs = true,
                screenPortrait = false,
            )

        assertEquals(2, next)
        assertEquals(
            0,
            SlideshowNavigation.previousStart(
                slides = slides,
                current = next,
                pairs = true,
                screenPortrait = false,
            ),
        )
    }

    @Test fun previousWrapsToFinalFrameRatherThanFinalPairMember() {
        val slides =
            listOf(
                portrait("a"),
                portrait("b"),
                landscape("c"),
                portrait("d"),
                portrait("e"),
            )

        assertEquals(
            3,
            SlideshowNavigation.previousStart(
                slides = slides,
                current = 0,
                pairs = true,
                screenPortrait = false,
            ),
        )
    }

    @Test fun normalFramesAdvanceAndReverseOneAtATime() {
        val slides = listOf(landscape("a"), landscape("b"), landscape("c"))

        assertEquals(
            2,
            SlideshowNavigation.nextStart(
                slides = slides,
                current = 1,
                pairs = true,
                screenPortrait = false,
            ),
        )
        assertEquals(
            0,
            SlideshowNavigation.previousStart(
                slides = slides,
                current = 1,
                pairs = true,
                screenPortrait = false,
            ),
        )
    }

    @Test fun disabledPairingTreatsEveryPhotoAsAFrame() {
        val slides = listOf(portrait("a"), portrait("b"), portrait("c"))

        assertEquals(
            1,
            SlideshowNavigation.nextStart(
                slides = slides,
                current = 0,
                pairs = false,
                screenPortrait = false,
            ),
        )
        assertEquals(
            2,
            SlideshowNavigation.previousStart(
                slides = slides,
                current = 0,
                pairs = false,
                screenPortrait = false,
            ),
        )
    }

    @Test fun actualHistoryRestoresANonCanonicalPairStart() {
        val history = SlideshowNavigationHistory()
        history.recordAdvance(from = 1)

        assertEquals(1, history.previousOr(fallback = 2))

        history.recordPrevious(displayed = 1)
        assertEquals(2, history.previousOr(fallback = 2))
    }

    private fun portrait(id: String) = Slide(id, null, portrait = true)

    private fun landscape(id: String) = Slide(id, null, portrait = false)
}
