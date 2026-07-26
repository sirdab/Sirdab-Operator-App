package co.sirdab.driver.shared.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.MapBounds
import co.sirdab.driver.shared.core.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

private fun Double.toRadians() = this * PI / 180.0

private fun LatLng.toOffset(bounds: MapBounds, size: Size): Offset {
    val x = (longitude - bounds.west) / (bounds.east - bounds.west)
    val yMerc = ln(tan(PI / 4 + latitude.toRadians() / 2))
    val nMerc = ln(tan(PI / 4 + bounds.north.toRadians() / 2))
    val sMerc = ln(tan(PI / 4 + bounds.south.toRadians() / 2))
    val y = (nMerc - yMerc) / (nMerc - sMerc)
    return Offset((x * size.width).toFloat(), (y * size.height).toFloat())
}

/**
 * Offline schematic map on a Compose Canvas — no SDK, no API key, identical on both platforms.
 * A pre-rendered raster of Saudi Arabia can slot in behind this later without touching callers.
 */
@Composable
fun MapCanvas(
    routePoints: List<LatLng>,
    bounds: MapBounds,
    modifier: Modifier = Modifier,
    currentPosition: LatLng? = null,
    geofences: List<LatLng> = emptyList(),
) {
    val pulse by rememberInfiniteTransition(label = "geofence").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulse",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = AppColors.Primary.c50)
        drawGrid()

        // Route line
        if (routePoints.size >= 2) {
            val path = Path()
            routePoints.forEachIndexed { i, p ->
                val o = p.toOffset(bounds, size)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color = AppColors.Primary.c300, style = Stroke(width = 10f, cap = StrokeCap.Round))
            drawPath(path, color = AppColors.Primary.c600, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        // Geofence pulses
        geofences.forEach { g ->
            val o = g.toOffset(bounds, size)
            drawCircle(color = AppColors.Accent.c500.copy(alpha = (1f - pulse) * 0.5f), radius = 12f + pulse * 40f, center = o)
            drawCircle(color = AppColors.Accent.c500, radius = 9f, center = o)
            drawCircle(color = AppColors.White, radius = 4f, center = o)
        }

        // Endpoints
        routePoints.firstOrNull()?.let { drawCircle(AppColors.Gray.c700, 9f, it.toOffset(bounds, size)) }
        routePoints.lastOrNull()?.let { drawCircle(AppColors.Gray.c900, 9f, it.toOffset(bounds, size)) }

        // Truck marker
        currentPosition?.let {
            val o = it.toOffset(bounds, size)
            drawCircle(AppColors.White, 20f, o)
            drawCircle(AppColors.Primary.c600, 15f, o)
            drawCircle(AppColors.White, 5f, o)
        }
    }
}

private fun DrawScope.drawGrid() {
    val step = size.width / 8
    var x = step
    while (x < size.width) {
        drawLine(AppColors.Primary.c100, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(AppColors.Primary.c100, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
