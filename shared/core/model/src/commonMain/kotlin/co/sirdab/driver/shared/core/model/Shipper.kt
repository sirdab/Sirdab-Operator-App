package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Shipper(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val rating: Double,
    val loadsPosted: Int,
    val phone: String,
    val verified: Boolean = true,
)
