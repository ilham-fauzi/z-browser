package com.browserz.core.webview

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Shows the selected tab's WebView. The WebView is re-parented, never recreated, on layout changes. */
@Composable
fun WebViewHost(session: WebViewBrowserSession, modifier: Modifier = Modifier) {
    val selected by session.selectedTabId.collectAsStateWithLifecycle()
    val version by session.hostVersion.collectAsStateWithLifecycle()
    val tabs by session.tabs.collectAsStateWithLifecycle()
    val selectedUrl = tabs.firstOrNull { it.id == selected }?.url

    AndroidView(
        modifier = modifier,
        factory = { FrameLayout(it) },
        // selected/version/selectedUrl are inputs: when any changes the container must re-attach.
        update = { container -> session.attach(container, selected, version, selectedUrl) },
        onRelease = { session.detach(it) },
    )
}
