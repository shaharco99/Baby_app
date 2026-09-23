package com.oryareach.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * A button label that turns into a small spinner while its action runs — for taps that wait on
 * the network, key derivation or a file (sign in, pairing, sign out, import, update check).
 *
 * The label stays laid out underneath at zero alpha, so the button keeps its width instead of
 * collapsing to the spinner's, and TalkBack still reads what the button is. The caller disables
 * the button while [busy]; this only draws.
 *
 * The spinner is `primary`, not the content colour: a disabled button's content is dimmed to
 * 38%, and on the light theme that left the spinner all but invisible (seen on the Pixel).
 * Safe because the button is always disabled while busy, so it never sits on a `primary` fill.
 */
@Composable
fun BusyLabel(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.alpha(if (busy) 0f else 1f))
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
