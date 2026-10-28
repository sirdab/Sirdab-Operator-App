package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.auth.AuthState
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppErrorReason
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.model.DocumentUpload
import co.sirdab.driver.shared.core.model.DriverDocumentKind
import co.sirdab.driver.shared.core.network.ApiFailure
import co.sirdab.driver.shared.core.network.ApiSuccess
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.feature.onboarding.api.domain.City
import co.sirdab.driver.shared.feature.onboarding.api.domain.DetailsDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverOnboardingRepository
import co.sirdab.driver.shared.feature.onboarding.api.domain.DriverProfile
import co.sirdab.driver.shared.feature.onboarding.api.domain.RoutesDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckDraft
import co.sirdab.driver.shared.feature.onboarding.api.domain.TruckType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sign-up against the live contract: one profile, read and written in place.
 *
 * There is no registration call. `GET /api/driver/profile` creates the profile the first time a
 * verified phone asks for it, links any fleet that added that number, and reports what is still
 * missing; everything after is a partial write to the same object. Each write answers with the
 * whole profile, so the app never has to guess at what remains — the exception is adding a truck,
 * which answers with the truck, and is re-read here so every method returns the same thing.
 */
class DriverOnboardingRepositoryTms(
    private val api: TmsApiClient,
    private val session: SessionManager,
) : DriverOnboardingRepository {

    private val _profile = MutableStateFlow<DriverProfile?>(null)
    override val profile: StateFlow<DriverProfile?> = _profile.asStateFlow()

    override fun clear() {
        _profile.value = null
    }

    override suspend fun refresh(): AppResult<DriverProfile> =
        api.get(PATH, DriverProfileDto.serializer()).toProfile()

    override suspend fun saveDetails(draft: DetailsDraft): AppResult<DriverProfile> = api.patch(
        path = PATH,
        body = api.encode(
            UpdateProfileDto.serializer(),
            UpdateProfileDto(
                name = draft.name.trim(),
                nationality = draft.nationality?.wire,
                licenceNumber = draft.licenceNumber.trim(),
                licenceExpiresAt = draft.licenceExpiresAt.takeIf { it.isNotBlank() },
            ),
        ),
        deserializer = DriverProfileDto.serializer(),
    ).toProfile()

    override suspend fun saveRoutes(draft: RoutesDraft): AppResult<DriverProfile> = api.patch(
        path = PATH,
        body = api.encode(
            UpdateProfileDto.serializer(),
            UpdateProfileDto(
                homeCityId = draft.homeCity?.id,
                // Always sent, empty or not: this is the whole list, and an empty one is the driver
                // removing the last route.
                preferredRoutes = draft.routes.map {
                    PreferredRouteDto(originCityId = it.origin.id, destinationCityId = it.destination.id)
                },
            ),
        ),
        deserializer = DriverProfileDto.serializer(),
    ).toProfile()

    override suspend fun searchCities(query: String): AppResult<List<City>> =
        api.get(
            path = CITIES_PATH,
            deserializer = CitiesDto.serializer(),
            // The contract refuses a blank `q`, so no query is no parameter.
            query = query.trim().takeIf { it.isNotEmpty() }?.let { mapOf("q" to it) }.orEmpty(),
        ).fold(
            onSuccess = { success -> AppResult.Success(success.value.items.map { it.toDomain() }) },
            onFailure = { AppResult.Failure(it.toOnboardingError()) },
        )

    override suspend fun truckTypes(): AppResult<List<TruckType>> =
        api.get(TRUCK_TYPES_PATH, TruckTypesDto.serializer()).fold(
            onSuccess = { success -> AppResult.Success(success.value.items.mapNotNull { it.toDomain() }) },
            onFailure = { AppResult.Failure(it.toOnboardingError()) },
        )

    /**
     * The contract answers with the truck alone, so the profile is read back.
     *
     * Worth the second call: adding a truck opens a new gap — that truck's own registration — and
     * the checklist the driver is looking at has to show it.
     */
    override suspend fun addTruck(draft: TruckDraft): AppResult<DriverProfile> {
        // The screen does not offer the button without one; this only keeps a caller that skipped
        // the check from sending a truck the server would refuse anyway.
        val type = draft.type ?: return AppResult.Failure(
            AppError("Pick a truck type.", reason = AppErrorReason.EQUIPMENT_NOT_OFFERED),
        )
        val created = api.post(
            path = TRUCKS_PATH,
            body = api.encode(
                AddTruckDto.serializer(),
                AddTruckDto(
                    licencePlate = draft.licencePlate.trim(),
                    // As the list sent them: the server checks the combination against it.
                    bodyType = type.bodyType.wire,
                    sizeClass = type.sizeClass.wire,
                    temperature = type.temperature.wire,
                    capacityKg = draft.capacityKg,
                ),
            ),
            deserializer = ProfileTruckDto.serializer(),
        )

        return created.fold(
            onSuccess = { refresh() },
            onFailure = { AppResult.Failure(it.toOnboardingError()) },
        )
    }

    override suspend fun removeTruck(truckId: String): AppResult<DriverProfile> =
        api.delete("$TRUCKS_PATH/$truckId", DriverProfileDto.serializer()).toProfile()

    /**
     * Mint, upload, confirm.
     *
     * Three calls because the bytes do not go through the API: the server signs a URL, storage
     * takes the photograph directly, and only then does the document count. Confirming is what
     * makes it real, and it answers with the profile so the checklist updates without a re-read.
     */
    override suspend fun uploadDocument(
        kind: DriverDocumentKind,
        truckId: String?,
        bytes: ByteArray,
        contentType: String,
    ): AppResult<DriverProfile> {
        if (bytes.size > DocumentUpload.MAX_BYTES) {
            return AppResult.Failure(
                AppError("That file is too large.", reason = AppErrorReason.DOCUMENT_TOO_LARGE),
            )
        }
        if (contentType !in DocumentUpload.accepted) {
            return AppResult.Failure(
                AppError("That file type is not accepted.", reason = AppErrorReason.DOCUMENT_TYPE),
            )
        }

        val target = api.post(
            path = DOCUMENTS_PATH,
            body = api.encode(
                CreateDocumentDto.serializer(),
                CreateDocumentDto(
                    kind = kind.wire,
                    // Required for a vehicle registration, refused for the rest. Sending it anyway
                    // would fail the whole write on a field the driver never saw.
                    truckId = truckId.takeIf { kind.needsTruck },
                    contentType = contentType,
                    sizeBytes = bytes.size,
                ),
            ),
            deserializer = CreateDocumentResultDto.serializer(),
        ).getOrElse { return AppResult.Failure(it.toOnboardingError()) }.value

        api.putBytes(target.uploadUrl, bytes, target.headers)
            .getOrElse { return AppResult.Failure(it.toOnboardingError()) }

        return api.post(
            path = "$DOCUMENTS_PATH/${target.documentId}/confirm",
            body = "{}",
            deserializer = DriverProfileDto.serializer(),
        ).toProfile()
    }

    /** Every call lands here: cache the profile, and keep the session's claims in step with it. */
    private suspend fun Result<ApiSuccess<DriverProfileDto>>.toProfile(): AppResult<DriverProfile> =
        fold(
            onSuccess = { success ->
                val profile = success.value.toDomain()
                _profile.value = profile

                // The profile links a fleet as a side effect of being read, and the token in hand
                // was minted before that membership existed. Trading it in here is what stops the
                // trip surface answering 403 to a driver the roster already has.
                if (profile.hasWorkspace && session.state.value !is AuthState.Ready) {
                    session.refresh()
                }
                AppResult.Success(profile)
            },
            onFailure = { AppResult.Failure(it.toOnboardingError()) },
        )

    private companion object {
        const val PATH = "api/driver/profile"
        const val TRUCKS_PATH = "api/driver/profile/trucks"
        const val TRUCK_TYPES_PATH = "api/driver/equipment-types"
        const val CITIES_PATH = "api/v1/cities"
        const val DOCUMENTS_PATH = "api/driver/profile/documents"
    }
}

/**
 * The failures this surface has words for.
 *
 * Everything else keeps the server's own message, which for a validation failure is more useful
 * than anything the app could invent.
 */
internal fun Throwable.toOnboardingError(): AppError {
    val failure = apiFailure
    val reason = when {
        failure !is ApiFailure.Http -> null
        failure.rawCode == "driver_suspended" -> AppErrorReason.DRIVER_SUSPENDED
        failure.rawCode == "last_truck" -> AppErrorReason.LAST_TRUCK
        failure.rawCode == "equipment_not_offered" -> AppErrorReason.EQUIPMENT_NOT_OFFERED
        failure.rawCode == "document_not_found" -> AppErrorReason.DOCUMENT_MISSING
        failure.rawCode == "workspace_suspended" -> AppErrorReason.WORKSPACE_SUSPENDED
        failure.rawCode == "no_driver_profile" -> AppErrorReason.NOT_PROVISIONED
        else -> null
    }

    return AppError(
        message = failure?.message ?: message ?: "Something went wrong.",
        cause = this,
        reason = reason,
    )
}
