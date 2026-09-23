package com.oryareach.core.ui.text

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

/**
 * A clipboard entry for text that must not be put on show: the recovery phrase.
 *
 * Android 13+ previews whatever is copied in a system overlay, and keyboards keep clipboard
 * history; both honour `EXTRA_IS_SENSITIVE` and hide the text (dots in the preview, left out of
 * history). Before 13 the platform has no constant, but Gboard and others read the same key.
 */
fun sensitiveClipEntry(label: String, text: String): ClipEntry {
    val clip = ClipData.newPlainText(label, text)
    val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
    clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
    return ClipEntry(clip)
}
