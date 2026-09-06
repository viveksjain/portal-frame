package com.portalhacks.frame

import java.util.ArrayDeque

/** Frame-level navigation for a slideshow whose frames may contain two consecutive photos. */
internal object SlideshowNavigation {
    fun pairIndex(
        slides: List<Slide>,
        start: Int,
        pairs: Boolean,
        screenPortrait: Boolean,
    ): Int {
        if (!pairs || slides.size < 2 || start !in slides.indices) {
            return -1
        }
        if (slides[start].portrait == screenPortrait) {
            return -1
        }
        val paired = start + 1
        return if (paired in slides.indices && slides[paired].portrait != screenPortrait) {
            paired
        } else {
            -1
        }
    }

    fun nextStart(
        slides: List<Slide>,
        current: Int,
        pairs: Boolean,
        screenPortrait: Boolean,
    ): Int {
        if (slides.isEmpty()) {
            return -1
        }
        val start = current.coerceIn(slides.indices)
        val step = if (pairIndex(slides, start, pairs, screenPortrait) >= 0) 2 else 1
        val next = start + step
        return if (next >= slides.size) 0 else next
    }

    fun previousStart(
        slides: List<Slide>,
        current: Int,
        pairs: Boolean,
        screenPortrait: Boolean,
    ): Int {
        if (slides.isEmpty()) {
            return -1
        }
        val target = current.coerceIn(slides.indices)
        var candidate = 0
        repeat(slides.size) {
            val next = nextStart(slides, candidate, pairs, screenPortrait)
            if (next == target) {
                return candidate
            }
            if (next == 0) {
                return (target - 1 + slides.size) % slides.size
            }
            candidate = next
        }
        return (target - 1 + slides.size) % slides.size
    }
}

/** Records displayed frame starts so Previous can undo advances from any starting index. */
internal class SlideshowNavigationHistory {
    private val starts = ArrayDeque<Int>()

    fun recordAdvance(from: Int) {
        starts.addLast(from)
        while (starts.size > MAX_ENTRIES) {
            starts.removeFirst()
        }
    }

    fun previousOr(fallback: Int): Int = starts.peekLast() ?: fallback

    fun recordPrevious(displayed: Int) {
        if (starts.peekLast() == displayed) {
            starts.removeLast()
        }
    }

    fun clear() {
        starts.clear()
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
