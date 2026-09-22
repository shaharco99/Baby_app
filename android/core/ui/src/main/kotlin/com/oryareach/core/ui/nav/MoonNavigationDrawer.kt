package com.oryareach.core.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oryareach.core.ui.theme.NightPalette

/** One destination in [MoonNavigationDrawer]. See [com.oryareach.core.ui.nav.MoonNavItem]'s
 * former bottom-bar counterpart for the filled/outlined icon convention this keeps. */
@Immutable
data class MoonNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The app's primary navigation surface: a side drawer rather than a bottom bar. Nine
 * destinations do not fit a bottom bar without either shrinking every label past legibility
 * (tried first; a Hebrew label like "הגדרות" clipped to "הגדר." at the width nine columns
 * leaves per item — screenshotted live and rejected) or hiding most of them behind a "more"
 * tier. A drawer sidesteps the width problem entirely — every row gets the full sheet width
 * to lay out an icon and a complete label — and is a well-established Android pattern for
 * exactly this destination count (Gmail, Google Drive, most productivity apps this dense use
 * one). Opened from [MoonTopBar]'s hamburger icon.
 *
 * Still built from [NightPalette], the moon countdown's own always-dark palette, rather than
 * the light/dark-adaptive [MaterialTheme.colorScheme] — the one piece of chrome the user
 * touches to get anywhere in the app stays lit like the moon it counts down to, regardless of
 * which theme the rest of the screen is in. The selected destination sits behind the same
 * warm glow the countdown's rising moonlight uses.
 */
@Composable
fun MoonNavigationDrawer(
    items: List<MoonNavItem>,
    headerTitle: String,
    headerSubtitle: String,
    modifier: Modifier = Modifier,
    /** Shown under the subtitle when the partner has their app open right now. */
    partnerHereLabel: String? = null,
) {
    // The lit end of the moon's own glow: the one warm colour in this always-dark sheet, so
    // "she is here" reads as a light being on rather than as a status LED.
    val presentGlow = NightPalette.glowStart

    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = NightPalette.sky,
        drawerContentColor = NightPalette.text,
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = NightPalette.text,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = headerSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NightPalette.textMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            partnerHereLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        // One announcement, not a dot and a sentence read separately.
                        .semantics(mergeDescendants = true) { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(color = presentGlow) }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = NightPalette.text,
                    )
                }
            }
        }
        HorizontalDivider(color = NightPalette.moonRim.copy(alpha = 0.35f))
        Column(Modifier.padding(vertical = 12.dp, horizontal = 12.dp)) {
            items.forEach { item -> DrawerItemRow(item) }
        }
    }
}

@Composable
private fun DrawerItemRow(item: MoonNavItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .background(if (item.selected) NightPalette.glowStart.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .semantics { contentDescription = item.label }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null,
            tint = if (item.selected) NightPalette.glowStart else NightPalette.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.selected) NightPalette.text else NightPalette.textMuted,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * The fixed strip above every screen: a hamburger icon that opens [MoonNavigationDrawer],
 * nothing else — each screen already renders its own in-content heading (added Phase 12 for
 * screen-reader users landing directly on a tab), so a duplicate title here would just repeat
 * it. Kept on [NightPalette] too, for the same reason the drawer is: this bar is visible on
 * every single screen in the app, same as the drawer it opens, so the two should read as one
 * fixed piece of chrome rather than a themed top bar opening an oddly-different-toned drawer.
 */
@Composable
fun MoonTopBar(
    title: String,
    onMenuClick: () -> Unit,
    menuContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(NightPalette.sky)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(selected = false, role = Role.Button, onClick = onMenuClick)
                    .semantics { contentDescription = menuContentDescription },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = null,
                    tint = NightPalette.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NightPalette.text,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(color = NightPalette.moonRim.copy(alpha = 0.3f))
    }
}
