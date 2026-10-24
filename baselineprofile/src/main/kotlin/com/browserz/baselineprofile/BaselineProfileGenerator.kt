package com.browserz.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods Browser-Z runs on its main journeys so ART can compile them ahead
 * of time on install: cold start, the engine picker, the menu, settings, the library, the tab list,
 * and loading a real page.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Engine picker: open it and dismiss it again.
        tap(By.descContains("Search engine"))
        device.pressBack()

        // Overflow menu, then Settings and back.
        tap(By.desc("Menu"))
        tap(By.text("Settings"))
        device.wait(Until.hasObject(By.text("Settings")), TIMEOUT)
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
        device.pressBack()

        // Library (bookmarks and history).
        tap(By.desc("Menu"))
        tap(By.text("Bookmarks and history"))
        device.waitForIdle()
        device.pressBack()

        // A real page load exercises the WebView, request blocking and cosmetic filtering paths.
        startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).setPackage(PACKAGE))
        Thread.sleep(PAGE_LOAD_MS)

        // Tabs: new tab, then the tab list.
        tap(By.desc("New tab"))
        tap(By.descContains("tabs").clickable(true))
        device.pressBack()
    }

    /** Taps the first match if there is one; a missing control must not fail profile generation. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.tap(selector: androidx.test.uiautomator.BySelector) {
        val target = device.wait(Until.findObject(selector), TIMEOUT)
        target?.click()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.browserz.app"
        const val TIMEOUT = 3_000L
        const val PAGE_LOAD_MS = 4_000L
    }
}
