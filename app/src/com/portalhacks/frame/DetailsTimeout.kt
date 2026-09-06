package com.portalhacks.frame

/** Inactivity deadline for the photo details overlay; times use a monotonic clock. */
internal class DetailsTimeout {
    var isOpen = false
        private set

    private var deadline = 0L

    fun open(now: Long) {
        isOpen = true
        deadline = now + TIMEOUT_MS
    }

    fun interact(now: Long) {
        if (isOpen) open(now)
    }

    fun expired(now: Long): Boolean = isOpen && now >= deadline

    fun close() {
        isOpen = false
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
