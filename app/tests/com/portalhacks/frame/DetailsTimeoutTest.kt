package com.portalhacks.frame

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsTimeoutTest {
    @Test fun openingPausesUntilInactivityTimeout() {
        val details = DetailsTimeout()
        details.open(100)
        assertTrue(details.isOpen)
        assertFalse(details.expired(10099))
        assertTrue(details.expired(10100))
    }

    @Test fun interactionExtendsThePause() {
        val details = DetailsTimeout()
        details.open(0)
        details.interact(9000)
        assertFalse(details.expired(10000))
        assertFalse(details.expired(18999))
        assertTrue(details.expired(19000))
    }

    @Test fun closingCancelsTheTimeoutAndDoesNotReopenOnInteraction() {
        val details = DetailsTimeout()
        details.open(0)
        details.close()
        details.interact(9000)
        assertFalse(details.isOpen)
        assertFalse(details.expired(20000))
    }

    @Test fun reopeningDoesNotInheritAnOldDeadline() {
        val details = DetailsTimeout()
        details.open(0)
        details.close()
        details.open(20000)
        assertFalse(details.expired(20000))
        assertTrue(details.expired(30000))
    }
}
