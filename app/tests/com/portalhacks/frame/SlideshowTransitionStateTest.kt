package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowTransitionStateTest {
    @Test fun openingDetailsInvalidatesAnAutomaticTransition() {
        val transitions = SlideshowTransitionState()
        val displayed = listOf(slide("a"), slide("b"))
        val request = transitions.begin(TransitionOrigin.AUTOMATIC, displayed)

        val restore = transitions.pauseForDetails()

        assertFalse(transitions.isCurrent(request))
        assertEquals(listOf("a", "b"), restore?.map { it.id })
    }

    @Test fun openingDetailsKeepsAManualTransition() {
        val transitions = SlideshowTransitionState()
        val request = transitions.begin(TransitionOrigin.USER, listOf(slide("a")))

        val restore = transitions.pauseForDetails()

        assertTrue(transitions.isCurrent(request))
        assertEquals(null, restore)
    }

    @Test fun reopeningDetailsInvalidatesADeferredRefreshRender() {
        val transitions = SlideshowTransitionState()
        val displayed = listOf(slide("a"), slide("b"))
        val request = transitions.begin(TransitionOrigin.REFRESH, displayed)

        val restore = transitions.pauseForDetails()

        assertFalse(transitions.isCurrent(request))
        assertEquals(listOf("a", "b"), restore?.map { it.id })
    }

    private fun slide(id: String) = Slide(id, null, portrait = true)
}
