package com.portalhacks.frame

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Photo metadata shown over the slideshow while playback is paused.
 *
 * The panel deliberately stays non-clickable: touches outside [closeButton] fall through to the
 * controller's gesture surface. The controller calls [interact] for those gestures so the
 * inactivity deadline follows the user's most recent interaction.
 */
internal class PhotoDetailsOverlay(
    context: Context,
    private val onPauseChanged: (Boolean) -> Unit,
    private val onClose: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = DetailsTimeout()
    private val text = PhotoDetailsText(context)
    private val metadata = TextView(context)
    private val panel = LinearLayout(context)
    private val closeButton = Button(context)
    private var fadeGeneration = 0

    val view: View = panel

    val isOpen: Boolean
        get() = timeout.isOpen

    private val expire =
        Runnable {
            if (timeout.expired(SystemClock.uptimeMillis())) fadeOut()
        }

    init {
        panel.orientation = LinearLayout.HORIZONTAL
        panel.gravity = Gravity.CENTER_VERTICAL
        panel.background = Ui.roundRect(PANEL_COLOR, Ui.dp(context, 20))
        panel.elevation = Ui.dp(context, 12).toFloat()
        panel.isClickable = false
        panel.isFocusable = false
        panel.visibility = View.GONE
        val horizontalPadding = Ui.dp(context, 24)
        val verticalPadding = Ui.dp(context, 18)
        panel.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        panel.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                val margin = Ui.dp(context, 28)
                setMargins(margin, margin, margin, margin)
            }

        val copy =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = false
                isFocusable = false
            }

        copy.addView(
            TextView(context).apply {
                text = "Photo details"
                setTextColor(Ui.TEXT)
                typeface = Ui.bold(context)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                includeFontPadding = false
                isClickable = false
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        metadata.setTextColor(Ui.TEXT)
        metadata.typeface = Ui.regular(context)
        metadata.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        metadata.setLineSpacing(Ui.dp(context, 2).toFloat(), 1f)
        metadata.maxLines = MAX_METADATA_LINES
        metadata.ellipsize = TextUtils.TruncateAt.END
        metadata.isClickable = false
        metadata.isFocusable = false
        metadata.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        copy.addView(
            metadata,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = Ui.dp(context, 8) },
        )

        copy.addView(
            TextView(context).apply {
                text = "Paused • Tap photo to resume • Swipe to browse"
                setTextColor(Ui.TEXT_MUTED)
                typeface = Ui.medium(context)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = false
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = Ui.dp(context, 10) },
        )

        panel.addView(
            copy,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )

        closeButton.text = "Close"
        closeButton.contentDescription = "Close slideshow"
        closeButton.isAllCaps = false
        closeButton.setTextColor(Ui.TEXT)
        closeButton.typeface = Ui.medium(context)
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        closeButton.background = Ui.roundRect(Ui.BLUE, Ui.dp(context, 14))
        closeButton.minWidth = Ui.dp(context, 112)
        closeButton.minHeight = Ui.dp(context, 56)
        closeButton.setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), 0)
        closeButton.setOnClickListener {
            interact()
            onClose()
        }
        panel.addView(
            closeButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(context, 56),
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = Ui.dp(context, 24)
            },
        )
    }

    fun show(slides: List<Slide>) {
        cancelFade()
        metadata.text = text.render(slides)
        panel.alpha = 1f
        panel.visibility = View.VISIBLE
        timeout.open(SystemClock.uptimeMillis())
        scheduleExpiry()
        onPauseChanged(true)
    }

    /** Refresh the visible metadata without opening the panel or extending its deadline. */
    fun update(slides: List<Slide>) {
        metadata.text = text.render(slides)
    }

    fun interact() {
        if (!timeout.isOpen) return
        cancelFade()
        panel.alpha = 1f
        timeout.interact(SystemClock.uptimeMillis())
        scheduleExpiry()
    }

    fun hide() {
        val wasOpen = timeout.isOpen
        handler.removeCallbacks(expire)
        timeout.close()
        cancelFade()
        panel.alpha = 1f
        panel.visibility = View.GONE
        if (wasOpen) onPauseChanged(false)
    }

    /** Stop owned UI work without restarting playback during controller teardown. */
    fun dispose() {
        handler.removeCallbacks(expire)
        timeout.close()
        cancelFade()
        panel.alpha = 1f
        panel.visibility = View.GONE
    }

    private fun scheduleExpiry() {
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, TIMEOUT_MS)
    }

    private fun fadeOut() {
        if (!timeout.isOpen) return
        handler.removeCallbacks(expire)
        val generation = ++fadeGeneration
        panel.animate().cancel()
        panel.animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .withEndAction {
                if (generation != fadeGeneration || !timeout.isOpen) return@withEndAction
                timeout.close()
                panel.visibility = View.GONE
                panel.alpha = 1f
                onPauseChanged(false)
            }
            .start()
    }

    /** Invalidate a prior fade before cancellation can deliver its completion callback. */
    private fun cancelFade() {
        fadeGeneration++
        panel.animate().cancel()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val FADE_MS = 220L
        const val MAX_METADATA_LINES = 12
        const val PANEL_COLOR = 0xE61A1A1A.toInt()
    }
}

/** Builds bounded, URL-free copy for at most the two photos displayed by the controller. */
private class PhotoDetailsText(context: Context) {
    private val prefs = context.getSharedPreferences(ConfigReceiver.PREFS, Context.MODE_PRIVATE)
    private val uploadsPrefix = File(context.filesDir, "uploads").absolutePath + File.separator

    fun render(slides: List<Slide>): String {
        if (slides.isEmpty()) {
            return "Date: Unknown\nAlbum: Frame photos\nSource: Frame"
        }
        return slides.take(MAX_PHOTOS).mapIndexed { index, slide ->
            buildString {
                if (slides.size > 1) append("Photo ${index + 1}\n")
                append("Date: ").append(dateFor(slide.timeMs))
                bounded(slide.caption)?.let { append("\nCaption: ").append(it) }
                val origin = originFor(slide)
                append("\nAlbum: ").append(origin.album)
                append("\nSource: ").append(origin.source)
            }
        }.joinToString("\n\n")
    }

    private fun dateFor(timeMs: Long): String {
        if (timeMs == Slide.NO_DATE) return "Unknown"
        return SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timeMs))
    }

    private fun originFor(slide: Slide): Origin {
        for (url in Albums.enabled(prefs)) {
            val cached = AlbumCache.read(prefs, url)
            if (cached?.any { it.id == slide.id } == true) {
                val source = bounded(PhotoSources.providerFor(url)?.displayName) ?: "Shared album"
                val title = bounded(AlbumCache.title(prefs, url)) ?: source
                return Origin(title, source)
            }
        }
        return if (slide.id.startsWith(uploadsPrefix)) {
            Origin("Photos added from phone", "Phone upload")
        } else {
            Origin("Frame samples", "Built in")
        }
    }

    private fun bounded(value: String?): String? {
        val clean = value?.trim()?.replace(WHITESPACE, " ")?.ifEmpty { null } ?: return null
        return if (clean.length <= MAX_FIELD_CHARS) clean else clean.take(MAX_FIELD_CHARS - 1) + "…"
    }

    private data class Origin(val album: String, val source: String)

    private companion object {
        const val MAX_PHOTOS = 2
        const val MAX_FIELD_CHARS = 180
        val WHITESPACE = Regex("\\s+")
    }
}
