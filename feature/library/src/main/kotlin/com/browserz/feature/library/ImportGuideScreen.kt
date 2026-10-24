package com.browserz.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Explains how to bring bookmarks from another browser. Android keeps each app's data private, so the way in is a
 * file the old browser exported; the page says how to get that file and what Browser-Z can read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportGuideScreen(
    onChooseFile: () -> Unit,
    onAutofillSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("Bring your bookmarks") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        HorizontalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Android does not let one app read another app's data, so Browser-Z imports from a file that your old browser exported.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) { Text("Choose a bookmarks file…") }
            Section(
                "Where to get the file",
                "Chrome: the Android app has no export. On a computer, open chrome://bookmarks, then ⋮ → Export bookmarks. " +
                    "Or use Google Takeout → Chrome → Bookmarks (Takeout/Chrome/Bookmarks.html).\n\n" +
                    "Firefox: on a computer, Library → Import and Backup → Export Bookmarks to HTML (or Backup… for a .json file).\n\n" +
                    "Edge: on a computer, edge://favorites → ⋯ → Export favorites.\n\n" +
                    "Other browsers: use Export bookmarks (HTML) if it has one.",
            )
            Section(
                "Files Browser-Z reads",
                "An HTML bookmarks file, a Firefox backup (.json), or the Bookmarks file of a Chrome or Edge profile. " +
                    "Folders are kept; web links only. Bookmarks you already have are not duplicated.",
            )
            Section(
                "Passwords",
                "Saved passwords do not need importing. Turn on Autofill with Google (Google Password Manager) in Android settings and Browser-Z can fill them in.",
            )
            OutlinedButton(onClick = onAutofillSettings, modifier = Modifier.fillMaxWidth()) { Text("Open autofill settings") }
            Text(
                "Logins (cookies) and open tabs cannot be moved between browsers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
