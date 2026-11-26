package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class LedgerColors(
    val debit: Color,
    val debitBg: Color,
    val credit: Color,
    val creditBg: Color,
    val warning: Color,
    val warningBg: Color,
    val borderSubtle: Color,
)

private val LightLedgerColors = LedgerColors(
    debit = DebitGreenLight,
    debitBg = DebitGreenBgLight,
    credit = CreditRedLight,
    creditBg = CreditRedBgLight,
    warning = WarningAmberLight,
    warningBg = WarningAmberBgLight,
    borderSubtle = OutlineSubtle,
)

private val DarkLedgerColors = LedgerColors(
    debit = DebitGreenDark,
    debitBg = DebitGreenBgDark,
    credit = CreditRedDark,
    creditBg = CreditRedBgDark,
    warning = WarningAmberDark,
    warningBg = WarningAmberBgDark,
    borderSubtle = DarkOutline,
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    background = PaperBackground,
    onBackground = InkOnSurface,
    surface = PaperSurface,
    onSurface = InkOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = OutlineSubtle,
    error = CreditRedLight,
    errorContainer = CreditRedBgLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = DarkOnEmeraldPrimary,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkOnEmeraldContainer,
    secondary = SlateSecondaryContainer,
    onSecondary = SlateOnSecondaryContainer,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkAmberTertiary,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkOnAmberContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = CreditRedDark,
    errorContainer = CreditRedBgDark,
)

val DaftaryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun DaftaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val ledgerColors = if (darkTheme) DarkLedgerColors else LightLedgerColors

    CompositionLocalProvider(
        LocalLedgerColors provides ledgerColors,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = DaftaryShapes,
            content = content,
        )
    }
}
