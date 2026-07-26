package co.sirdab.driver.shared.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import co.sirdab.driver.shared.core.platform.locale.isRtlLanguage

private fun appColorScheme() = lightColorScheme(
    primary = AppColors.Primary.c600,
    onPrimary = AppColors.White,
    primaryContainer = AppColors.Primary.c50,
    onPrimaryContainer = AppColors.Primary.c900,
    secondary = AppColors.Accent.c500,
    onSecondary = AppColors.White,
    secondaryContainer = AppColors.Accent.c50,
    onSecondaryContainer = AppColors.Accent.c900,
    tertiary = AppColors.Primary.c400,
    background = AppColors.PageBackground,
    onBackground = AppColors.Gray.c900,
    surface = AppColors.Surface,
    onSurface = AppColors.Gray.c900,
    surfaceVariant = AppColors.Gray.c100,
    onSurfaceVariant = AppColors.Gray.c600,
    outline = AppColors.Gray.c300,
    outlineVariant = AppColors.Gray.c200,
    error = AppColors.Red.c500,
    onError = AppColors.White,
    errorContainer = AppColors.Red.c50,
    onErrorContainer = AppColors.Red.c900,
)

/**
 * Root theme. Direction and font family follow the in-app language choice (plan §9), not the
 * device locale — the caller passes the chosen language code.
 */
@Composable
fun AppTheme(
    languageCode: String = "en",
    content: @Composable () -> Unit,
) {
    val family = fontFamilyFor(languageCode)
    val typography = appTypography(family)
    val direction = if (isRtlLanguage(languageCode)) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = appColorScheme(),
            typography = typography,
            content = content,
        )
    }
}
