package co.sirdab.driver.shared.feature.onboarding.impl.data

import kotlinx.serialization.Serializable

/**
 * What linking gives back.
 *
 * A driver may have been added by more than one dispatcher, so the server
 * links every driver row that carries their phone and reports them all. The
 * app follows the one the server made active, which is the first.
 */
@Serializable
internal data class LinkedWorkspaceDto(
    val workspaceId: String,
    val driverId: String,
    val carrierId: String,
)

@Serializable
internal data class DriverOnboardingResultDto(
    val workspaces: List<LinkedWorkspaceDto> = emptyList(),
    val refreshSession: Boolean = true,
)
