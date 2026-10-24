package com.browserz.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/**
 * A tab's WebView with Chrome-style pull to refresh.
 *
 * The pull starts only from what the page itself did not use: WebView reports an overscroll at the top when the
 * page (not an inner scroller, a map or a game that handles its own touches) could not scroll further up. A gesture
 * that begins below the top of the page never turns into a pull, so a fling back to the top does not reload.
 */
@SuppressLint("ViewConstructor")
internal class TabWebView(context: Context) : WebView(context) {

    /** Gets the pull distance in pixels while the finger moves (0 = no pull), then the outcome on release. */
    interface PullListener {
        fun onPull(distancePx: Float)
        fun onPullEnd(refresh: Boolean)
    }

    var pullListener: PullListener? = null
    var pullEnabled = true

    /**
     * Background play and picture-in-picture: the engine pauses media when its window becomes invisible (screen
     * off, app in the background). With this set the window is reported as still visible, so playback goes on.
     */
    var keepPlayingHidden = false

    private val density = context.resources.displayMetrics.density
    private var startedAtTop = false
    private var overscrolledTop = false
    private var pulling = false
    private var anchorY = 0f
    private var lastY = 0f
    private var distance = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startedAtTop = scrollY <= 0
                overscrolledTop = false
                pulling = false
                distance = 0f
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> endPull(refresh = false) // pinch zoom, not a pull
            MotionEvent.ACTION_MOVE -> {
                if (!pulling && pullEnabled && startedAtTop && overscrolledTop && event.pointerCount == 1 && event.y > lastY) {
                    pulling = true
                    anchorY = lastY
                }
                if (pulling) {
                    distance = ((event.y - anchorY) * RESISTANCE).coerceAtLeast(0f)
                    pullListener?.onPull(distance)
                    if (distance == 0f) pulling = false // pushed back up: the page scrolls again
                }
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> endPull(refresh = distance >= TRIGGER_DP * density)
            MotionEvent.ACTION_CANCEL -> endPull(refresh = false)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (clampedY && scrollY <= 0) overscrolledTop = true
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepPlayingHidden && visibility != GONE) VISIBLE else visibility)
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, if (keepPlayingHidden && visibility != GONE) VISIBLE else visibility)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus || keepPlayingHidden)
    }

    private fun endPull(refresh: Boolean) {
        val wasPulling = pulling
        pulling = false
        startedAtTop = false
        distance = 0f
        if (wasPulling) pullListener?.onPullEnd(refresh)
    }

    companion object {
        /** How far (after resistance) the indicator must travel before letting go reloads. */
        const val TRIGGER_DP = 72f
        private const val RESISTANCE = 0.5f
    }
}
