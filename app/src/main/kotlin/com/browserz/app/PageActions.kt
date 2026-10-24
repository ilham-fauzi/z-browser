package com.browserz.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.browserz.core.domain.TabInfo
import java.util.Locale

/** Things that leave the app (share sheet, launcher, system Downloads, files); kept out of the composables. */
internal object PageActions {
    private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

    /** The files a picker returned: several (clip data), one, or null when the user backed out. */
    fun pickedFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) return Array(clip.itemCount) { clip.getItemAt(it).uri }
        return data.data?.let { arrayOf(it) }
    }

    fun share(context: Context, tab: TabInfo) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, tab.title)
            .putExtra(Intent.EXTRA_TEXT, tab.url)
        context.startActivity(Intent.createChooser(send, "Share page"))
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share"))
    }

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun addToHome(context: Context, tab: TabInfo) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, "Your launcher can't add shortcuts", Toast.LENGTH_SHORT).show()
            return
        }
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(tab.url))
        val label = tab.title.ifBlank { tab.url }.take(24)
        val shortcut = ShortcutInfoCompat.Builder(context, "page-" + tab.url.hashCode())
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(open)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /** Opens the system's autofill service chooser, or Settings when that screen is not available. */
    fun openAutofillSettings(context: Context) {
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:android")))
        } catch (_: RuntimeException) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (_: RuntimeException) {
                Toast.makeText(context, "Open Settings → Passwords and accounts → Autofill service", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** True when Android's browser role (the default browser) is held by this app. */
    fun isDefaultBrowser(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        val roles = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && roles.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
    }

    /** Asks Android to make this the default browser (system dialog), or opens the default-apps settings on older versions. */
    fun requestDefaultBrowser(context: Context) {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(android.app.role.RoleManager::class.java)
                roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
            } else {
                Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (_: RuntimeException) {
                Toast.makeText(context, "Open Settings → Apps → Default apps → Browser app", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openDownloads(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            Toast.makeText(context, "No Downloads app found", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the page through Google Translate in the user's language; the user picks this explicitly from the menu. */
    fun translateUrl(pageUrl: String): String =
        "https://translate.google.com/translate?sl=auto&tl=${Locale.getDefault().language}&u=${Uri.encode(pageUrl)}"

    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_IMPORT_BYTES) return@use null
            }
            out.toString(Charsets.UTF_8.name())
        }
    } catch (_: java.io.IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (_: java.io.IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
