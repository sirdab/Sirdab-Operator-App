package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.MapBounds
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** Builds and samples the trip route from origin/destination city coordinates. */
object RouteUtil {

    /** A gently-arced polyline between two points (arc so the line reads as a road, not a ruler). */
    fun buildRoute(from: LatLng, to: LatLng, steps: Int = 24): List<LatLng> {
        val dLat = to.latitude - from.latitude
        val dLng = to.longitude - from.longitude
        val len = hypot(dLat, dLng).coerceAtLeast(1e-6)
        val perpLat = -dLng / len
        val perpLng = dLat / len
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val arc = sin(t * PI) * len * 0.12
            LatLng(
                latitude = from.latitude + dLat * t + perpLat * arc,
                longitude = from.longitude + dLng * t + perpLng * arc,
            )
        }
    }

    fun boundsFor(points: List<LatLng>, padding: Double = 0.25): MapBounds {
        if (points.isEmpty()) return MapBounds(32.0, 16.0, 55.0, 34.0)
        val north = points.maxOf { it.latitude }
        val south = points.minOf { it.latitude }
        val east = points.maxOf { it.longitude }
        val west = points.minOf { it.longitude }
        val padLat = ((north - south) * padding).coerceAtLeast(0.4)
        val padLng = ((east - west) * padding).coerceAtLeast(0.4)
        return MapBounds(north + padLat, south - padLat, east + padLng, west - padLng)
    }

    /** Position at [progress] (0f..1f) along the polyline. */
    fun pointAt(points: List<LatLng>, progress: Float): LatLng {
        if (points.isEmpty()) return LatLng(0.0, 0.0)
        if (points.size == 1) return points.first()
        val p = progress.coerceIn(0f, 1f)
        val scaled = p * (points.size - 1)
        val i = scaled.toInt().coerceIn(0, points.size - 2)
        val frac = scaled - i
        val a = points[i]
        val b = points[i + 1]
        return LatLng(
            latitude = a.latitude + (b.latitude - a.latitude) * frac,
            longitude = a.longitude + (b.longitude - a.longitude) * frac,
        )
    }
}
