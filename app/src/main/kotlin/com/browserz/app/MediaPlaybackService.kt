package com.browserz.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.browserz.core.webview.MediaPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Background play: keeps the app alive while a page plays audio with the app out of sight, and shows the media
 * notification with play / pause / next / previous, which Android also puts on the lock screen. Runs only while
 * the "Background play" setting is on and a tab has media; it is started by [watch] when playback begins.
 */
class MediaPlaybackService : Service() {

    private val container get() = (application as BrowserZApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Media playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for audio and video playing in the background"
                setShowBadge(false)
            },
        )
        mediaSession = MediaSession(this, "BrowserZ").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = container.session.mediaControl("play")
                override fun onPause() = container.session.mediaControl("pause")
                override fun onSkipToNext() = container.session.mediaControl("next")
                override fun onSkipToPrevious() = container.session.mediaControl("previous")
                override fun onStop() = container.session.mediaControl("stop")
            })
            setSessionActivity(openAppIntent(null))
            isActive = true
        }
        scope.launch {
            combine(container.session.media, container.settings.settings) { media, settings -> media.takeIf { settings.backgroundPlay } }
                .distinctUntilChanged()
                .collect { media -> if (media == null) shutDown() else show(media) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() demands startForeground() soon, whatever the state.
        val media = container.session.media.value
        if (!foreground) {
            if (media == null) {
                goForeground(placeholder())
                shutDown()
                return START_NOT_STICKY
            }
            show(media)
        }
        intent?.getStringExtra(EXTRA_ACTION)?.let(container.session::mediaControl)
        return START_NOT_STICKY
    }

    /** The user swiped the app away from Recents: stop the music with it, like a media app. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        container.session.mediaControl("stop")
        shutDown()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    private fun show(media: MediaPlayback) {
        val icon = container.session.favicon(media.tabId)
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, media.title.ifBlank { media.host })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, media.artist.ifBlank { media.host })
                .apply { if (icon != null) putBitmap(MediaMetadata.METADATA_KEY_ART, icon) }
                .build(),
        )
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (media.canNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (media.canPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(if (media.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        val notification = notificationFor(media)
        if (media.playing) {
            goForeground(notification)
        } else {
            // Paused: the notification stays, but can be swiped away, and Android may stop the app again.
            if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            foreground = false
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun goForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        } catch (_: RuntimeException) {
            // Android refused (app in the background without a user action): the notification still shows.
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        foreground = false
        stopSelf()
    }

    private fun notificationFor(media: MediaPlayback): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle(media.title.ifBlank { media.host })
            .setContentText(media.artist.ifBlank { media.host })
            .setContentIntent(openAppIntent(media.tabId))
            .setDeleteIntent(controlIntent("stop"))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(media.playing)
        container.session.favicon(media.tabId)?.let(builder::setLargeIcon)
        if (media.canPrevious) builder.addAction(action(android.R.drawable.ic_media_previous, "Previous", "previous"))
        builder.addAction(
            if (media.playing) action(android.R.drawable.ic_media_pause, "Pause", "pause") else action(android.R.drawable.ic_media_play, "Play", "play"),
        )
        if (media.canNext) builder.addAction(action(android.R.drawable.ic_media_next, "Next", "next"))
        val shown = 1 + (if (media.canPrevious) 1 else 0) + (if (media.canNext) 1 else 0)
        builder.style = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(*IntArray(shown) { it })
        return builder.build()
    }

    @Suppress("DEPRECATION") // Notification.Action.Builder(int, ...) is the form that works on API 26+
    private fun action(icon: Int, title: String, command: String) =
        Notification.Action.Builder(icon, title, controlIntent(command)).build()

    private fun controlIntent(command: String): PendingIntent =
        PendingIntent.getService(
            this,
            command.hashCode(),
            Intent(this, MediaPlaybackService::class.java).putExtra(EXTRA_ACTION, command),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(tabId: Long?): PendingIntent {
        val open = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_TAB)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tabId != null) open.putExtra(MainActivity.EXTRA_TAB_ID, tabId)
        return PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun placeholder(): Notification =
        Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_media_notification).setContentTitle("Browser-Z").build()

    companion object {
        private const val CHANNEL = "media"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_ACTION = "action"

        @Volatile private var running = false

        /**
         * Starts the service when a page starts playing while "Background play" is on. Playback begins with the
         * app on screen, which is when Android allows a foreground service to start.
         */
        fun watch(context: Context, container: AppContainer) {
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                combine(container.session.media, container.settings.settings) { media, settings -> settings.backgroundPlay && media?.playing == true }
                    .distinctUntilChanged()
                    .collect { wanted ->
                        if (!wanted || running) return@collect
                        try {
                            ContextCompat.startForegroundService(app, Intent(app, MediaPlaybackService::class.java))
                        } catch (_: RuntimeException) {
                            // Not allowed from the background on Android 12+; it starts the next time playback begins in the app.
                        }
                    }
            }
        }
    }
}
