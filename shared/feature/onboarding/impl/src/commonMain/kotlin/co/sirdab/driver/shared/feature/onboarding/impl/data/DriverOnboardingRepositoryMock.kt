package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.demo.DemoWorld
import co.sirdab.driver.shared.core.demo.demoLatency
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.model.DriverDocumentStatus
import co.sirdab.driver.shared.core.model.TruckSize
import co.sirdab.driver.shared.core.model.TruckType
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.feature.onboarding.api.domain.DetailsDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfileStatus
import co.sirdab.driver.shared.feature.onboarding.api.domain.OnboardingGap
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileDocument
import co.sirdab.driver.shared.feature.onboarding.api.domain.ProfileTruck
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sign-up with no server: the same checklist, kept in memory.
 *
 * It recomputes `missing` exactly as the server does, because the whole flow is driven by that
 * list — a demo that always answered "nothing missing" would exercise none of these screens.
 */
class DriverOnboardingRepositoryMock(private val world: DemoWorld) : DriverOnboardingRepository {

    private val _profile = MutableStateFlow<DriverProfile?>(null)
    override val profile: StateFlow<DriverProfile?> = _profile.asStateFlow()

    override fun clear() {
        _profile.value = null
    }

    override suspend fun refresh(): AppResult<DriverProfile> {
        demoLatency()
        return AppResult.Success(_profile.value ?: empty().also { _profile.value = it })
    }

    override suspend fun saveDetails(draft: DetailsDraft): AppResult<DriverProfile> {
        demoLatency()
        return current {
            it.copy(
                name = draft.name.trim(),
                nationality = draft.nationality,
                licenceNumber = draft.licenceNumber.trim(),
                licenceExpiresAt = draft.licenceExpiresAt.takeIf(String::isNotBlank),
            )
        }
    }

    override suspend fun addTruck(draft: TruckDraft): AppResult<DriverProfile> {
        demoLatency()
        return current { profile ->
            val existing = profile.trucks.firstOrNull { it.licencePlate == draft.licencePlate.trim() }
            val truck = ProfileTruck(
                id = existing?.id ?: "demo-truck-${profile.trucks.size + 1}",
                licencePlate = draft.licencePlate.trim(),
                truckType = draft.truckType,
                truckSize = draft.truckSize,
                capacityKg = draft.capacityKg,
            )
            profile.copy(trucks = profile.trucks.filterNot { it.id == truck.id } + truck)
        }
    }

    override suspend fun removeTruck(truckId: String): AppResult<DriverProfile> {
        demoLatency()
        val profile = _profile.value ?: empty()
        if (profile.trucks.size <= 1) {
            return AppResult.Failure(
                AppError("You need at least one truck.", reason = AppErrorReason.LAST_TRUCK),
            )
        }
        return current { it.copy(trucks = it.trucks.filterNot { truck -> truck.id == truckId }) }
    }

    override suspend fun uploadDocument(
        kind: DriverDocumentKind,
        truckId: String?,
        bytes: ByteArray,
        contentType: String,
    ): AppResult<DriverProfile> {
        demoLatency()
        return current { profile ->
            val slot = truckId.takeIf { kind.needsTruck }
            val document = ProfileDocument(
                id = "demo-doc-${kind.wire}-${slot.orEmpty()}",
                kind = kind,
                truckId = slot,
                status = DriverDocumentStatus.UPLOADED,
                rejectionReason = null,
                uploadedAt = "now",
                downloadUrl = null,
            )
            profile.copy(
                documents = profile.documents
                    .filterNot { it.kind == kind && it.truckId == slot } + document,
            )
        }
    }

    /** Apply the change, then recompute what is still missing, the way the server would. */
    private fun current(transform: (DriverProfile) -> DriverProfile): AppResult<DriverProfile> {
        val updated = transform(_profile.value ?: empty()).recomputed()
        _profile.value = updated

        // The demo has no ops queue to wait on, so finishing is approval. Without this the demo
        // driver would hand everything over and then sit on "under review" for ever, which is the
        // one screen the demo cannot get past on its own.
        if (updated.onboardingComplete) {
            world.completeOnboarding(
                fullNameEn = updated.name,
                fullNameAr = updated.name,
                vehicle = updated.trucks.firstOrNull()?.let { truck ->
                    Vehicle(
                        truckType = truck.truckType,
                        truckSize = truck.truckSize,
                        plate = truck.licencePlate,
                        capacityTons = truck.capacityKg?.let { kg -> kg / 1000.0 } ?: 0.0,
                    )
                } ?: Vehicle(TruckType.DRY, TruckSize.CLOSED_LORRY, plate = "", capacityTons = 0.0),
            )
        }
        return AppResult.Success(updated)
    }

    private fun DriverProfile.recomputed(): DriverProfile {
        val missing = buildList {
            if (name.isBlank()) add(OnboardingGap.Name)
            if (licenceNumber.isNullOrBlank()) add(OnboardingGap.Licence)
            if (trucks.isEmpty()) add(OnboardingGap.Truck)
            // Either identity document closes the one gap, exactly as the server has it.
            val identity = documentFor(DriverDocumentKind.NATIONAL_ID)
                ?: documentFor(DriverDocumentKind.IQAMA)
            if (identity == null) add(OnboardingGap.Identity)
            if (documentFor(DriverDocumentKind.DRIVING_LICENCE) == null) add(OnboardingGap.DrivingLicence)
            trucks.forEach { truck ->
                if (documentFor(DriverDocumentKind.VEHICLE_REGISTRATION, truck.id) == null) {
                    add(OnboardingGap.VehicleRegistration(truck.id))
                }
            }
        }

        val completedAt = onboardingCompletedAt ?: "now".takeIf { missing.isEmpty() }
        return copy(
            missing = missing,
            // Set once and never cleared, as the server does it.
            onboardingCompletedAt = completedAt,
            status = if (completedAt != null) DriverProfileStatus.ACTIVE else status,
        )
    }

    private fun empty() = DriverProfile(
        userId = "demo-driver",
        name = "",
        phone = null,
        nationality = null,
        licenceNumber = null,
        licenceExpiresAt = null,
        status = DriverProfileStatus.PENDING_VERIFICATION,
        onboardingCompletedAt = null,
        missing = listOf(
            OnboardingGap.Name,
            OnboardingGap.Licence,
            OnboardingGap.Truck,
            OnboardingGap.Identity,
            OnboardingGap.DrivingLicence,
        ),
        trucks = emptyList(),
        documents = emptyList(),
        workspaces = emptyList(),
    )
}
