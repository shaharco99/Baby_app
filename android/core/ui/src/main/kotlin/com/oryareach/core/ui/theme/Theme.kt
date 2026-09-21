package com.oryareach.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two palette entries that have no Material 3 slot. Exposed through a composition local
 * rather than as loose constants so they follow the light/dark switch like every other color.
 */
@Immutable
data class ExtendedColors(
    val moss: Color,
    val onMoss: Color,
    val blush: Color,
    val onBlush: Color,
)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        moss = Palette.Light.moss,
        onMoss = Palette.Light.mossForeground,
        blush = Palette.Light.blush,
        onBlush = Palette.Light.blushForeground,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Palette.Light.primary,
    onPrimary = Palette.Light.primaryForeground,
    secondary = Palette.Light.secondary,
    onSecondary = Palette.Light.secondaryForeground,
    tertiary = Palette.Light.blush,
    onTertiary = Palette.Light.blushForeground,
    background = Palette.Light.background,
    onBackground = Palette.Light.foreground,
    surface = Palette.Light.card,
    onSurface = Palette.Light.cardForeground,
    surfaceVariant = Palette.Light.muted,
    onSurfaceVariant = Palette.Light.mutedForeground,
    surfaceContainer = Palette.Light.accent,
    error = Palette.Light.destructive,
    onError = Palette.Light.primaryForeground,
    outline = Palette.Light.border,
    outlineVariant = Palette.Light.border,

    // The container roles. Leaving these unset is not neutral: Material falls back to its
    // baseline purple, and every component that defaults to one silently leaves the palette —
    // the add button on Tasks and Shopping, the selected half of every segmented control and
    // filter chip, every FilledTonalButton, the undo snackbar, dialog surfaces. The app looked
    // like two products depending on which control you were looking at.
    //
    // `primaryContainer` is the accent itself rather than a washed-out version of it: the
    // brand's whole action vocabulary is that one colour, so the add button should read as the
    // same thing as "Log a feed". The quieter roles take `accent`, which is what that palette
    // entry was always for.
    primaryContainer = Palette.Light.primary,
    onPrimaryContainer = Palette.Light.primaryForeground,
    secondaryContainer = Palette.Light.accent,
    onSecondaryContainer = Palette.Light.accentForeground,
    tertiaryContainer = Palette.Light.accent,
    onTertiaryContainer = Palette.Light.accentForeground,
    errorContainer = Palette.Light.destructive,
    onErrorContainer = Palette.Light.primaryForeground,

    // Named for what actually renders them, not for a five-step elevation story this product
    // does not tell: it has one card colour, and depth comes from borders and `accent`.
    // `surfaceContainerHighest` is what a filled Card uses, so it has to be `card` or every
    // card in the app changes colour.
    surfaceContainerLowest = Palette.Light.background,
    surfaceContainerLow = Palette.Light.card,
    surfaceContainerHigh = Palette.Light.card,
    surfaceContainerHighest = Palette.Light.card,

    // The inverse of a light theme is the dark one. Used by the snackbar, which was the one
    // lavender rectangle on an otherwise warm screen.
    inverseSurface = Palette.Dark.card,
    inverseOnSurface = Palette.Dark.foreground,
    inversePrimary = Palette.Dark.primary,
)

private val DarkColorScheme = darkColorScheme(
    primary = Palette.Dark.primary,
    onPrimary = Palette.Dark.primaryForeground,
    secondary = Palette.Dark.secondary,
    onSecondary = Palette.Dark.secondaryForeground,
    tertiary = Palette.Dark.blush,
    onTertiary = Palette.Dark.blushForeground,
    background = Palette.Dark.background,
    onBackground = Palette.Dark.foreground,
    surface = Palette.Dark.card,
    onSurface = Palette.Dark.cardForeground,
    surfaceVariant = Palette.Dark.muted,
    onSurfaceVariant = Palette.Dark.mutedForeground,
    surfaceContainer = Palette.Dark.accent,
    error = Palette.Dark.destructive,
    onError = Palette.Dark.primaryForeground,
    outline = Palette.Dark.border,
    outlineVariant = Palette.Dark.border,

    // See the light scheme above for why these are not optional.
    primaryContainer = Palette.Dark.primary,
    onPrimaryContainer = Palette.Dark.primaryForeground,
    secondaryContainer = Palette.Dark.accent,
    onSecondaryContainer = Palette.Dark.accentForeground,
    tertiaryContainer = Palette.Dark.accent,
    onTertiaryContainer = Palette.Dark.accentForeground,
    errorContainer = Palette.Dark.destructive,
    onErrorContainer = Palette.Dark.primaryForeground,

    // See the light scheme: Card reads `surfaceContainerHighest`, so it is the card colour.
    surfaceContainerLowest = Palette.Dark.background,
    surfaceContainerLow = Palette.Dark.card,
    surfaceContainerHigh = Palette.Dark.card,
    surfaceContainerHighest = Palette.Dark.card,

    inverseSurface = Palette.Light.card,
    inverseOnSurface = Palette.Light.foreground,
    inversePrimary = Palette.Light.primary,
)

/**
 * Dynamic color is deliberately not used: the palette is the product's identity, carried
 * over from the web app, and letting the wallpaper recolor it would lose that.
 */
@Composable
fun OrYareachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) {
        ExtendedColors(
            moss = Palette.Dark.moss,
            onMoss = Palette.Dark.mossForeground,
            blush = Palette.Dark.blush,
            onBlush = Palette.Dark.blushForeground,
        )
    } else {
        ExtendedColors(
            moss = Palette.Light.moss,
            onMoss = Palette.Light.mossForeground,
            blush = Palette.Light.blush,
            onBlush = Palette.Light.blushForeground,
        )
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OrYareachTypography,
            shapes = OrYareachShapes,
            content = content,
        )
    }
}

object OrYareachTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

    /** The always-dark palette used by the moon countdown, independent of the app theme. */
    val night = NightPalette
}
