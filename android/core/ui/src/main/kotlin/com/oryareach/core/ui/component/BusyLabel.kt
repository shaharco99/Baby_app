package com.oryareach.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
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
 */
@Composable
fun BusyLabel(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.alpha(if (busy) 0f else 1f))
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        }
    }
}
