package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

@Serializable
data class RoutePolyline(
    val id: String,
    val name: String,
    val points: List<LatLng>,
)

@Serializable
data class City(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val location: LatLng,
)
