package com.browserz.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.browserz.core.webview.MediaPlayback
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as BrowserZApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openLink(intent)
        setContent { BrowserZRoot(container) }
        ContextCompat.registerReceiver(this, pipControls, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Keeps Android's picture-in-picture parameters (auto-enter, shape, play / pause button) in step with playback.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(container.session.media, container.settings.settings, container.session.selectedTabId) { media, _, _ -> media }
                    .collect { updatePipParams(it) }
            }
        }
    }

    // ---------------------------------------------------------------- picture-in-picture

    private val pipControls = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(EXTRA_PIP_ACTION)?.let(container.session::mediaControl)
        }
    }

    private val supportsPip: Boolean by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    /** A video with sound plays in the tab on screen and the user wants picture-in-picture. */
    private fun pipEligible(media: MediaPlayback?): Boolean =
        supportsPip && container.settings.settings.value.pictureInPicture && media != null && media.playing && media.hasVideo &&
            media.tabId == container.session.selectedTabId.value

    private fun pipParams(media: MediaPlayback?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (media != null && media.videoWidth > 0 && media.videoHeight > 0) {
            // Android accepts shapes between 2.39:1 and 1:2.39.
            val ratio = (media.videoWidth.toFloat() / media.videoHeight).coerceIn(1 / 2.39f + 0.01f, 2.39f - 0.01f)
            builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        }
        if (media != null) {
            val actions = ArrayList<RemoteAction>()
            if (media.canPrevious) actions.add(pipAction(android.R.drawable.ic_media_previous, "Previous", "previous"))
            actions.add(
                if (media.playing) pipAction(android.R.drawable.ic_media_pause, "Pause", "pause") else pipAction(android.R.drawable.ic_media_play, "Play", "play"),
            )
            if (media.canNext) actions.add(pipAction(android.R.drawable.ic_media_next, "Next", "next"))
            builder.setActions(actions)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(pipEligible(media))
            builder.setSeamlessResizeEnabled(false) // video: no cross-fade of the page while resizing
        }
        return builder.build()
    }

    private fun pipAction(icon: Int, title: String, command: String): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, command)
        val pending = PendingIntent.getBroadcast(this, command.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    private fun updatePipParams(media: MediaPlayback?) {
        if (!supportsPip) return
        try {
            setPictureInPictureParams(pipParams(media))
        } catch (_: IllegalStateException) {
            // Picture-in-picture disabled for the app in system settings.
        }
    }

    /** Android 8–11: enter picture-in-picture by hand when the user leaves (12+ does it via setAutoEnterEnabled). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val media = container.session.media.value
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipEligible(media)) {
            try {
                enterPictureInPictureMode(pipParams(media))
            } catch (_: IllegalStateException) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        container.pictureInPicture.value = isInPictureInPictureMode
        container.session.setPictureInPicture(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
    }

    override fun onResume() {
        super.onResume()
        container.session.onHostResumed()
    }

    override fun onPause() {
        // In picture-in-picture (or with background play) the playing tab keeps going.
        container.session.onHostPaused(keepPlaying = isInPictureInPictureMode || container.settings.settings.value.backgroundPlay)
        container.stats.flush()
        super.onPause()
    }

    // Lint flags this because androidx.core's ComponentActivity restricts it; overriding Activity.dispatchKeyEvent is the documented way
    // to see Ctrl+key shortcuts before a focused WebView consumes them.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            if (container.shortcutHandler?.invoke(event.keyCode, event.isShiftPressed) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (or closing the picture-in-picture window) stops playback unless background play is on.
        if (!container.settings.settings.value.backgroundPlay) container.session.mediaControl("pause")
    }

    override fun onDestroy() {
        unregisterReceiver(pipControls)
        if (isFinishing && !isChangingConfigurations) container.clearOnExit()
        super.onDestroy()
    }

    private fun openLink(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_TAB -> {
                val id = intent.getLongExtra(EXTRA_TAB_ID, -1L)
                if (id >= 0) container.session.selectTab(id)
                return
            }
            ACTION_NEW_TAB, ACTION_NEW_TAB_SEARCH -> {
                container.session.openTab("")
                return
            }
            ACTION_NEW_INCOGNITO -> {
                if (container.session.openTab("", incognito = true) < 0) container.session.openTab("")
                return
            }
        }
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (uri.scheme != "http" && uri.scheme != "https") return
        container.session.openTab(uri.toString())
    }

    companion object {
        const val ACTION_NEW_TAB = "com.browserz.app.action.NEW_TAB"
        const val ACTION_NEW_INCOGNITO = "com.browserz.app.action.NEW_INCOGNITO"
        const val ACTION_NEW_TAB_SEARCH = "com.browserz.app.action.NEW_TAB_SEARCH"

        /** From the media notification: bring the app back on the tab that plays. */
        const val ACTION_SHOW_TAB = "com.browserz.app.action.SHOW_TAB"
        const val EXTRA_TAB_ID = "tabId"
        private const val ACTION_PIP_CONTROL = "com.browserz.app.action.PIP_CONTROL"
        private const val EXTRA_PIP_ACTION = "action"
    }
}
