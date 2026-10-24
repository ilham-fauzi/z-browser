package com.browserz.engine.adblock

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import com.browserz.core.domain.FilterListManager
import com.browserz.core.domain.FilterStatus
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the live [FilterEngine]. Starts from the bundled list plus any list cached on disk, and
 * refreshes the downloaded lists at most once every 24 h. A failed download or parse keeps the
 * previous engine, so blocking never gets worse because an update failed.
 */
class FilterListUpdater(context: Context) : FilterListManager {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "filters").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("filters", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "filter-lists").apply { isDaemon = true } }

    private val engineRef = AtomicReference(FilterEngine.parse(""))
    private val _status = MutableStateFlow(FilterStatus(lastUpdatedAt = prefs.getLong(KEY_UPDATED, 0L)))

    override val status: StateFlow<FilterStatus> = _status.asStateFlow()
    val blocker = FilterContentBlocker(engineRef)

    /**
     * Loads the small bundled list synchronously so the first page is already protected, then the full engine on
     * the worker thread: from the engine file saved last time when the lists have not changed (a fraction of a
     * second), otherwise by parsing the cached lists again.
     */
    fun start() {
        rebuild(includeCached = false)
        worker.execute {
            if (hasCachedLists() && !loadSavedEngine()) rebuild(includeCached = true)
            val lastUpdate = prefs.getLong(KEY_UPDATED, 0L)
            // The very first download always runs (without lists the blocker is weak); later refreshes wait for Wi-Fi.
            if (lastUpdate == 0L || (System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS && isUnmetered())) updateNow()
        }
    }

    /** Automatic refreshes after the first one (about 3 MB) only run on Wi-Fi or another unmetered network; "Update now" always runs. */
    private fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun hasCachedLists() = LISTS.any { File(dir, it.file).isFile }

    override fun updateNow() {
        if (_status.value.updating) return
        _status.update { it.copy(updating = true, lastError = null) }
        worker.execute {
            var failure: String? = null
            var downloaded = 0
            for (list in LISTS) {
                try {
                    download(list)
                    downloaded++
                } catch (e: IOException) {
                    failure = "Could not download ${list.name}: ${e.message ?: "network error"}"
                }
            }
            if (downloaded > 0) prefs.edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
            rebuild(includeCached = true, error = failure)
        }
    }

    /**
     * Builds the engine in two steps so protection is strong quickly after launch: first the built-in
     * list plus the plain domain lists (fast to read, they block the ad networks that matter most),
     * then everything, including the big EasyList-style files. Each step replaces the engine atomically.
     */
    private fun rebuild(includeCached: Boolean, error: String? = null) {
        try {
            val started = System.currentTimeMillis()
            if (includeCached) {
                val quick = FilterEngine.Builder()
                addBuiltin(quick)
                for (list in LISTS.filter { it.domainsOnly }) addCached(quick, list)
                val quickEngine = quick.build()
                engineRef.set(quickEngine)
                publishCounts(quickEngine, error)
                Log.i(TAG, "stage 1 ready in ${System.currentTimeMillis() - started} ms (${quickEngine.networkRuleCount} rules)")
            }
            val full = FilterEngine.Builder()
            addBuiltin(full)
            if (includeCached) for (list in LISTS) addCached(full, list)
            val engine = full.build()
            engineRef.set(engine)
            publishCounts(engine, error)
            Log.i(TAG, "stage 2 ready in ${System.currentTimeMillis() - started} ms (${engine.networkRuleCount} rules, ${engine.cosmeticRuleCount} hiding)")
            if (includeCached) saveEngine(engine)
        } catch (e: Exception) {
            _status.update { it.copy(updating = false, lastError = "Could not load filter lists: ${e.message}") }
        }
    }

    /** Identifies what the saved engine was built from: this app build (bundled list, parser) and each list file. */
    private fun inputsFingerprint(): Long {
        var hash = appVersion()
        for (list in LISTS) {
            val file = File(dir, list.file)
            hash = hash * 31 + list.file.hashCode()
            hash = hash * 31 + (if (file.isFile) file.length() * 1_000_003 + file.lastModified() else -1L)
        }
        return hash
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): Long = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.versionCode.toLong() * 1_000_003 + info.lastUpdateTime
    } catch (_: Exception) {
        0L
    }

    private fun loadSavedEngine(): Boolean {
        val file = File(dir, ENGINE_FILE)
        if (!file.isFile) return false
        val started = System.currentTimeMillis()
        val engine = try {
            DataInputStream(file.inputStream().buffered(64 * 1024)).use { FilterEngine.readFrom(it, inputsFingerprint()) }
        } catch (e: Exception) {
            Log.w(TAG, "saved engine unreadable", e)
            null
        }
        if (engine == null) {
            file.delete()
            return false
        }
        engineRef.set(engine)
        publishCounts(engine, null)
        Log.i(TAG, "saved engine loaded in ${System.currentTimeMillis() - started} ms (${engine.networkRuleCount} rules, ${engine.cosmeticRuleCount} hiding)")
        return true
    }

    private fun saveEngine(engine: FilterEngine) {
        val tmp = File(dir, "$ENGINE_FILE.tmp")
        try {
            DataOutputStream(tmp.outputStream().buffered(64 * 1024)).use { engine.writeTo(it, inputsFingerprint()) }
            if (!tmp.renameTo(File(dir, ENGINE_FILE))) tmp.delete()
        } catch (e: IOException) {
            Log.w(TAG, "could not save engine", e)
            tmp.delete()
        }
    }

    private fun addBuiltin(builder: FilterEngine.Builder) {
        appContext.assets.open("builtin.txt").bufferedReader().useLines { it.forEach(builder::addLine) }
    }

    private fun addCached(builder: FilterEngine.Builder, list: RemoteList) {
        val file = File(dir, list.file)
        if (file.isFile) file.bufferedReader().useLines { it.forEach(builder::addLine) }
    }

    private fun publishCounts(engine: FilterEngine, error: String?) {
        _status.value = FilterStatus(
            ruleCount = engine.networkRuleCount,
            cosmeticCount = engine.cosmeticRuleCount,
            lastUpdatedAt = prefs.getLong(KEY_UPDATED, 0L),
            updating = false,
            lastError = error,
        )
    }

    private fun download(list: RemoteList) {
        val connection = URL(list.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val tmp = File(dir, list.file + ".tmp")
            var total = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_LIST_BYTES) throw IOException("list too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total < MIN_LIST_BYTES) {
                tmp.delete()
                throw IOException("list looks empty")
            }
            if (!tmp.renameTo(File(dir, list.file))) throw IOException("could not save list")
        } finally {
            connection.disconnect()
        }
    }

    private class RemoteList(val name: String, val url: String, val file: String, val domainsOnly: Boolean = false)

    private companion object {
        const val TAG = "BZFilters"
        const val KEY_UPDATED = "lastUpdatedAt"
        const val ENGINE_FILE = "engine.bin"
        const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val TIMEOUT_MS = 15_000
        const val MAX_LIST_BYTES = 10L * 1024 * 1024
        const val MIN_LIST_BYTES = 1024L

        // Fixed allow-list of HTTPS hosts; lists are never fetched from anywhere else.
        val LISTS = listOf(
            RemoteList("EasyList", "https://easylist.to/easylist/easylist.txt", "easylist.txt"),
            RemoteList("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", "easyprivacy.txt"),
            // Ad, tracker and malware domains, including the rotating domains of pop-up and push-ad networks.
            RemoteList("HaGeZi Light", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/light.txt", "hagezi-light.txt", domainsOnly = true),
            RemoteList("HaGeZi Pop-Up Ads", "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/popupads.txt", "hagezi-popup.txt", domainsOnly = true),
            // Cookie-consent banners: network rules only (small), so no heavy page-wide hiding CSS.
            RemoteList("Cookie banners (third-party)", "https://cdn.jsdelivr.net/gh/easylist/easylist@master/easylist_cookie/easylist_cookie_thirdparty.txt", "cookie-thirdparty.txt"),
            RemoteList("Cookie banners (general)", "https://cdn.jsdelivr.net/gh/easylist/easylist@master/easylist_cookie/easylist_cookie_general_block.txt", "cookie-general.txt"),
            // Indonesian sites.
            RemoteList("ABPindo", "https://raw.githubusercontent.com/ABPindo/indonesianadblockrules/master/subscriptions/abpindo.txt", "abpindo.txt"),
        )
    }
}
