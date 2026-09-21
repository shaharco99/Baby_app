package com.oryareach.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.oryareach.core.ui.R

/**
 * The bar that opens and closes a drawer of finished things.
 *
 * Every list in this app grows a tail of items nobody needs to look at again — bought shopping,
 * done tasks, the feeding log from four days ago. They were still drawn in full, so the part of
 * the list that is actually being worked on got pushed further down the screen every week.
 * Rather than hide them behind a filter nobody would remember to un-set, they go into a drawer:
 * shut by default, one tap to look inside, and the tap target says how many are in there so it
 * never feels like something went missing.
 *
 * Just the bar, not the contents, because the two kinds of caller need different things from
 * it: a `LazyColumn` (shopping, tasks, the logs) puts this in one `item` and then emits the
 * rows as siblings, so they stay lazily composed and keep their own keys; a plain `Column`
 * (settings, the cycle history) wants the contents nested, which is what [CollapsibleDrawer]
 * below wraps up.
 *
 * @param count how many rows are inside, shown on the bar and read out by TalkBack. Null for a
 *   drawer over something that is not a list — a settings group has no number worth printing.
 */
@Composable
fun DrawerHeader(
    title: String,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = stringResource(
        if (expanded) R.string.drawer_collapse_action else R.string.drawer_expand_action,
    )
    // One announcement for the whole bar rather than three: the title, the bare number and the
    // chevron each mean nothing read on their own.
    val spoken = if (count == null) {
        stringResource(R.string.drawer_header_description_plain, title, action)
    } else {
        stringResource(R.string.drawer_header_description, title, count, action)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    onClick(label = action, action = null)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * [DrawerHeader] with its contents nested underneath, for callers laying out in a plain
 * [Column]. The contents are composed only while open, so a drawer that is shut costs nothing.
 */
@Composable
fun CollapsibleDrawer(
    title: String,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = verticalArrangement) {
        DrawerHeader(title = title, count = count, expanded = expanded, onToggle = onToggle)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}
