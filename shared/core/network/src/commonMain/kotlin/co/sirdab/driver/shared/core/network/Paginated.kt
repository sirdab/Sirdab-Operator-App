package co.sirdab.driver.shared.core.network

import kotlinx.serialization.Serializable

/**
 * The list envelope every TMS list endpoint returns.
 *
 * [nextCursor] is opaque: pass back exactly what arrived, never build one, never reuse one issued
 * by a different endpoint. A mangled cursor answers 400 `invalid_cursor`.
 */
@Serializable
data class Paginated<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)
