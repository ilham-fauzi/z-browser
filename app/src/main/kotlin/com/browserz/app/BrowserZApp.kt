package com.browserz.app

import android.app.Application

class BrowserZApp : Application() {
    private var created: AppContainer? = null

    val container: AppContainer
        get() = created ?: AppContainer(this).also { created = it }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        created?.session?.onTrimMemory(level)
    }
}
