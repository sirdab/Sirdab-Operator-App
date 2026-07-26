package co.sirdab.driver.shared.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import co.sirdab.driver.shared.core.ui.generated.resources.Res
import co.sirdab.driver.shared.core.ui.generated.resources.hind_bold
import co.sirdab.driver.shared.core.ui.generated.resources.hind_light
import co.sirdab.driver.shared.core.ui.generated.resources.hind_medium
import co.sirdab.driver.shared.core.ui.generated.resources.hind_regular
import co.sirdab.driver.shared.core.ui.generated.resources.hind_semibold
import co.sirdab.driver.shared.core.ui.generated.resources.inter_variable
import co.sirdab.driver.shared.core.ui.generated.resources.tajawal_bold
import co.sirdab.driver.shared.core.ui.generated.resources.tajawal_light
import co.sirdab.driver.shared.core.ui.generated.resources.tajawal_medium
import co.sirdab.driver.shared.core.ui.generated.resources.tajawal_regular
import org.jetbrains.compose.resources.Font

/** Latin (English). Inter shipped as a variable file; heavier weights are synthesized. */
@Composable
fun latinFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_variable, weight = FontWeight.Normal),
    Font(Res.font.inter_variable, weight = FontWeight.Medium),
    Font(Res.font.inter_variable, weight = FontWeight.SemiBold),
    Font(Res.font.inter_variable, weight = FontWeight.Bold),
)

/** Arabic + Urdu (RTL). Tajawal (Naskh) covers both scripts. */
@Composable
fun arabicFontFamily(): FontFamily = FontFamily(
    Font(Res.font.tajawal_light, weight = FontWeight.Light),
    Font(Res.font.tajawal_regular, weight = FontWeight.Normal),
    Font(Res.font.tajawal_medium, weight = FontWeight.Medium),
    Font(Res.font.tajawal_bold, weight = FontWeight.Bold),
)

/** Devanagari (Hindi). Hind covers Devanagari + Latin. */
@Composable
fun devanagariFontFamily(): FontFamily = FontFamily(
    Font(Res.font.hind_light, weight = FontWeight.Light),
    Font(Res.font.hind_regular, weight = FontWeight.Normal),
    Font(Res.font.hind_medium, weight = FontWeight.Medium),
    Font(Res.font.hind_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.hind_bold, weight = FontWeight.Bold),
)

@Composable
fun fontFamilyFor(languageCode: String): FontFamily = when (languageCode) {
    "ar", "ur" -> arabicFontFamily()
    "hi" -> devanagariFontFamily()
    else -> latinFontFamily()
}

fun appTypography(family: FontFamily): Typography {
    fun style(size: Int, weight: FontWeight, lineHeight: Int) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.sp)
    return Typography(
        displayLarge = style(40, FontWeight.Bold, 48),
        displayMedium = style(32, FontWeight.Bold, 40),
        displaySmall = style(28, FontWeight.Bold, 36),
        headlineLarge = style(26, FontWeight.Bold, 34),
        headlineMedium = style(22, FontWeight.SemiBold, 30),
        headlineSmall = style(20, FontWeight.SemiBold, 28),
        titleLarge = style(18, FontWeight.SemiBold, 26),
        titleMedium = style(16, FontWeight.Medium, 24),
        titleSmall = style(14, FontWeight.Medium, 20),
        bodyLarge = style(16, FontWeight.Normal, 24),
        bodyMedium = style(14, FontWeight.Normal, 20),
        bodySmall = style(12, FontWeight.Normal, 16),
        labelLarge = style(14, FontWeight.Medium, 20),
        labelMedium = style(12, FontWeight.Medium, 16),
        labelSmall = style(11, FontWeight.Medium, 14),
    )
}
