package com.portalhacks.frame

/** Generation gate for asynchronous image transitions. */
internal enum class TransitionOrigin {
    USER,
    AUTOMATIC,
    REFRESH,
    SYSTEM,
}

internal class SlideshowTransitionState {
    private var generation = 0L
    private var restoreFrame: List<Slide>? = null

    val current: Long
        get() = generation

    fun begin(
        origin: TransitionOrigin,
        displayedFrame: List<Slide>,
    ): Long {
        generation++
        restoreFrame =
            if (origin == TransitionOrigin.USER || displayedFrame.isEmpty()) {
                null
            } else {
                displayedFrame.toList()
            }
        return generation
    }

    fun pauseForDetails(): List<Slide>? {
        val restore = restoreFrame ?: return null
        invalidate()
        return restore
    }

    fun complete(request: Long) {
        if (request == generation) {
            restoreFrame = null
        }
    }

    fun invalidate() {
        generation++
        restoreFrame = null
    }

    fun isCurrent(request: Long): Boolean = request == generation
}

internal data class SlideshowRefreshTarget(
    val index: Int,
    val instant: Boolean,
)

/** Keeps a refresh render pending until the matching asynchronous callback displays it. */
internal class SlideshowRefreshQueue {
    private var generation = 0L
    var pending: SlideshowRefreshTarget? = null
        private set

    fun queue(target: SlideshowRefreshTarget): Long {
        generation++
        pending = target
        return generation
    }

    fun complete(request: Long) {
        if (request == generation) {
            pending = null
        }
    }

    fun clear() {
        generation++
        pending = null
    }
}

/** Chooses the frame that must be rendered after applying a deferred photo refresh. */
internal object SlideshowRefresh {
    fun resolve(
        slides: List<Slide>,
        inspectedId: String?,
        requestedId: String?,
        requestedInstant: Boolean,
    ): SlideshowRefreshTarget {
        val preserved = inspectedId?.let { id -> slides.indexOfFirst { it.id == id } } ?: -1
        if (preserved >= 0) {
            return SlideshowRefreshTarget(preserved, instant = true)
        }
        val requested = requestedId?.let { id -> slides.indexOfFirst { it.id == id } } ?: -1
        return SlideshowRefreshTarget(
            index = requested.takeIf { it >= 0 } ?: 0,
            instant = requestedInstant,
        )
    }
}

internal data class WeatherSettings(
    val city: String,
    val fahrenheit: Boolean,
)

internal data class WeatherRequest(
    val settings: WeatherSettings,
    val generation: Long,
)

/** Tracks live weather preferences and rejects responses issued for older settings. */
internal class WeatherRefreshState(initial: WeatherSettings) {
    private var generation = 0L
    var settings: WeatherSettings = initial
        private set

    fun update(next: WeatherSettings): Boolean {
        if (next == settings) {
            return false
        }
        settings = next
        generation++
        return true
    }

    fun beginRequest(): WeatherRequest = WeatherRequest(settings, generation)

    fun isCurrent(request: WeatherRequest): Boolean = request.generation == generation
}
