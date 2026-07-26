package co.sirdab.driver.shared.core.ui.theme

import androidx.compose.ui.graphics.Color

/** A 50→900 tonal scale, Chakra-style. */
class ColorScale(
    val c50: Color, val c100: Color, val c200: Color, val c300: Color, val c400: Color,
    val c500: Color, val c600: Color, val c700: Color, val c800: Color, val c900: Color,
)

private fun String.hex(): Color = Color(0xFF000000L or removePrefix("#").toLong(16))

private fun scale(vararg hex: String): ColorScale = ColorScale(
    hex[0].hex(), hex[1].hex(), hex[2].hex(), hex[3].hex(), hex[4].hex(),
    hex[5].hex(), hex[6].hex(), hex[7].hex(), hex[8].hex(), hex[9].hex(),
)

/**
 * Driver-app palette. Primary is a deep teal (haulage / logistics feel), distinct from the
 * reference merchant brand. Scales are the design-system source of truth for the ColorScheme.
 */
object AppColors {
    val Primary = scale(
        "#E6F5F4", "#C0E7E4", "#97D8D2", "#6DC8C0", "#4DBDB3",
        "#2EB1A6", "#279A90", "#1E7D75", "#155F59", "#0A3E3A",
    )
    val Accent = scale(
        "#FFF3E0", "#FFE0B2", "#FFCC80", "#FFB74D", "#FFA726",
        "#FF9800", "#FB8C00", "#F57C00", "#EF6C00", "#E65100",
    )
    val Gray = scale(
        "#F8FAFC", "#F1F5F9", "#E2E8F0", "#CBD5E1", "#94A3B8",
        "#64748B", "#475569", "#334155", "#1E293B", "#0F172A",
    )
    val Green = scale(
        "#E9F9EF", "#C8F0D6", "#9FE4B6", "#6FD592", "#43C56F",
        "#22B357", "#1B9448", "#15753A", "#0F562B", "#0A3A1D",
    )
    val Red = scale(
        "#FDECEC", "#FAD1D1", "#F5A9A9", "#EE7C7C", "#E65252",
        "#D93636", "#B82C2C", "#932424", "#6E1B1B", "#4A1212",
    )
    val Amber = scale(
        "#FEF6E7", "#FCE7BD", "#F9D690", "#F5C25E", "#F1B037",
        "#E89C1A", "#C6820F", "#9E680C", "#775009", "#4F3506",
    )

    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF0B1220)
    val PageBackground = Color(0xFFF6F8F9)
    val Surface = White
}
