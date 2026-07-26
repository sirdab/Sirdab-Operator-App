package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationKind { OUTBID, AWARDED, COUNTERED, REJECTED, GEOFENCE, DETENTION, BACKHAUL, PAYOUT, DOCUMENT, SYSTEM }

@Serializable
data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val titleEn: String,
    val titleAr: String,
    val bodyEn: String,
    val bodyAr: String,
    val read: Boolean = false,
    val createdAtMillis: Long,
    val deepLink: String? = null,
)
