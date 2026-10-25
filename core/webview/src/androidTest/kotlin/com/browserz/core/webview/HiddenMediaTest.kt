package com.browserz.core.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [PlaybackScript.HIDDEN_CHECK] in a real WebView: after a page changes its address without loading a new document,
 * media it no longer shows is paused (and reported paused through the bridge), and media it still shows plays on.
 * Pages are served from memory under https://site.test/, so no network is needed.
 */
@RunWith(AndroidJUnit4::class)
class HiddenMediaTest {

    private lateinit var scenario: ActivityScenario<TestHostActivity>
    private lateinit var webView: WebView
    private val messages = CopyOnWriteArrayList<JSONObject>()
    private val pages = HashMap<String, String>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(TestHostActivity::class.java)
        scenario.onActivity { activity ->
            webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val html = pages[request.url.path] ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), "".byteInputStream())
                        return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
                    }
                }
            }
            WebViewCompat.addWebMessageListener(webView, PlaybackScript.BRIDGE, setOf("*")) { _, message, _, _, _ ->
                message.data?.let { messages.add(JSONObject(it)) }
            }
            WebViewCompat.addDocumentStartJavaScript(webView, PlaybackScript.HOOK, setOf("*"))
            activity.setContentView(webView)
        }
    }

    @After
    fun tearDown() {
        scenario.onActivity { webView.destroy() }
        scenario.close()
    }

    @Test
    fun sc1() {
        val hidden = mapOf(
            "removed" to "V.remove();",
            "display none" to "V.style.display='none';",
            "zero size" to "V.style.width='0';V.style.height='0';",
        )
        for ((case, change) in hidden) {
            open(PLAYER_PAGE)
            moveAway(change)
            assertTrue("$case: paused", waitFor("V.paused"))
            assertEquals("$case: reported paused", "pause", messages.last().getString("s"))
        }

        open(FRAMED_PAGE)
        moveAway("document.getElementById('f').style.display='none';")
        assertTrue("hidden frame: paused", waitFor("document.getElementById('f').contentWindow.V.paused"))
        assertEquals("hidden frame: reported paused", "pause", messages.last().getString("s"))
    }

    @Test
    fun sc2() {
        val shown = mapOf(
            "unchanged" to "",
            "mini-player" to "var b=document.createElement('div');b.style.cssText='position:fixed;right:8px;bottom:8px;width:160px;height:90px';" +
                "document.body.appendChild(b);V.style.width='100%';V.style.height='100%';b.appendChild(V);V.play();",
            "next video in the same element" to "feed(V);",
        )
        for ((case, change) in shown) {
            open(PLAYER_PAGE)
            moveAway(change)
            Thread.sleep(SETTLE_MS)
            assertFalse("$case: still playing", eval("V.paused").toBoolean())
            assertTrue("$case: not reported paused", messages.none { it.getString("s") == "pause" })
        }
    }

    @Test
    fun sc3() {
        assertEquals("plays in the frame", 0, open(FRAMED_PAGE).getInt("top"))
        moveAway("document.getElementById('f').contentDocument.getElementById('box').style.display='none';")
        assertTrue("paused", waitFor("document.getElementById('f').contentWindow.V.paused"))
        val last = messages.last()
        assertEquals("reported paused", "pause", last.getString("s"))
        assertEquals("by the frame", 0, last.getInt("top"))
    }

    /**
     * Loads [html] as https://site.test/ (with [PLAYER_PAGE] as its frame), waits until its video plays audibly and
     * returns the hook's play message.
     */
    private fun open(html: String): JSONObject {
        pages.clear()
        pages["/"] = html
        pages["/frame.html"] = PLAYER_PAGE
        messages.clear()
        instrumentation.runOnMainSync { webView.loadUrl("https://site.test/") }
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (messages.none { it.getString("s") == "play" }) {
            check(System.currentTimeMillis() < deadline) { "the video never started; messages: $messages" }
            Thread.sleep(50)
        }
        val play = messages.first { it.getString("s") == "play" }
        messages.clear()
        return play
    }

    /**
     * What a site does when it moves to another page: changes the page, then its address. Then the session's check;
     * only messages sent from then on are kept, so the tests see what the check did.
     */
    private fun moveAway(change: String) {
        eval("$change history.pushState({}, '', '/next'); true")
        Thread.sleep(SETTLE_MS)
        messages.clear()
        eval(PlaybackScript.HIDDEN_CHECK)
    }

    private fun waitFor(condition: String): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (eval("!!($condition)") == "true") return true
            Thread.sleep(50)
        }
        return false
    }

    private fun eval(script: String): String {
        var result = ""
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                result = it
                done.countDown()
            }
        }
        check(done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "no answer to $script" }
        return result
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 800L

        /** feed() plays a fresh canvas stream in a video; with sound on and full volume the hook counts it as audible. */
        const val FEED = "function feed(v){var c=document.createElement('canvas');c.width=64;c.height=36;var g=c.getContext('2d'),n=0;" +
            "setInterval(function(){g.fillStyle='hsl('+(n++*20%360)+',80%,50%)';g.fillRect(0,0,64,36);},50);" +
            "v.srcObject=c.captureStream(20);v.muted=false;v.volume=1;v.play();}"

        const val PLAYER_PAGE = "<!doctype html><html><head><meta name='viewport' content='width=device-width'></head>" +
            "<body style='margin:0'><div id='box'><video id='v' playsinline style='width:320px;height:180px'></video></div>" +
            "<script>$FEED window.V=document.getElementById('v');feed(V);</script></body></html>"

        const val FRAMED_PAGE = "<!doctype html><html><head><meta name='viewport' content='width=device-width'></head>" +
            "<body style='margin:0'><iframe id='f' src='/frame.html' style='width:340px;height:200px;border:0'></iframe></body></html>"
    }
}
