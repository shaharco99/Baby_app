package com.oryareach.core.ui.text

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.oryareach.core.ui.R

/**
 * Says "Copied" after a copy button, on the Android versions that don't.
 *
 * Android 13+ shows its own confirmation the moment anything lands on the clipboard; a second
 * one from the app would stack on top of it. Below 13 a copy is silent, and a button that seems
 * to do nothing gets tapped again. A toast rather than a snackbar because it is what the system
 * does on newer phones, and because two of the three copy buttons sit in dialogs with no
 * snackbar host.
 */
fun Context.confirmCopied() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}
