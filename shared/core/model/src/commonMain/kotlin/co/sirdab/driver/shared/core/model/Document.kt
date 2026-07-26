package co.sirdab.driver.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocType { NATIONAL_ID, DRIVING_LICENSE, VEHICLE_REGISTRATION, INSURANCE, OPERATING_CARD }

@Serializable
enum class DocStatus { MISSING, UPLOADED, VERIFYING, VERIFIED, EXPIRING_SOON, EXPIRED }

@Serializable
data class Document(
    val id: String,
    val type: DocType,
    val status: DocStatus = DocStatus.MISSING,
    val fileRef: String? = null,
    val expiresAtMillis: Long? = null,
)
