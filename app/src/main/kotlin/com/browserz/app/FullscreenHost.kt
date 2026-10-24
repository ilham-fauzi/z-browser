package com.browserz.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.browserz.core.domain.VideoShape
import com.browserz.core.webview.FullscreenVideo

/**
 * Shows the view a page asked to put in full screen over the whole window: system bars hidden, black
 * background, rotation follows the device sensor, and Back leaves full screen.
 */
@Composable
fun FullscreenHost(video: FullscreenVideo?, onExit: () -> Unit) {
    if (video == null) return
    val activity = LocalContext.current.findActivity()

    BackHandler(onBack = onExit)

    DisposableEffect(video.view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Follow the video: wide video -> landscape, tall video -> portrait, unknown -> whatever the sensor says.
    // The shape can arrive after full screen starts, so this re-runs when it changes.
    DisposableEffect(video.shape) {
        activity?.requestedOrientation = when (video.shape) {
            VideoShape.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            VideoShape.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            VideoShape.Unknown -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        onDispose { }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
        update = { container ->
            if (container.childCount != 1 || container.getChildAt(0) !== video.view) {
                container.removeAllViews()
                (video.view.parent as? ViewGroup)?.removeView(video.view)
                container.addView(
                    video.view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        onRelease = { it.removeAllViews() },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
