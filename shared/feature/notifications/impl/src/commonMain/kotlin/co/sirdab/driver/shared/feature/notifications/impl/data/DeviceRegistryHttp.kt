package co.sirdab.driver.shared.feature.notifications.impl.data

import co.sirdab.driver.shared.core.model.AppError
import co.sirdab.driver.shared.core.model.AppResult
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.apiFailure
import co.sirdab.driver.shared.core.network.toAppError
import co.sirdab.driver.shared.feature.notifications.api.DevicePlatform
import co.sirdab.driver.shared.feature.notifications.api.DeviceRegistry
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
internal data class DeviceInput(
    val platform: String,
    val pushToken: String,
    val appVersion: String? = null,
    val locale: String? = null,
)

@Serializable
internal data class DeviceResponse(
    val id: String,
    val platform: String,
    val pushToken: String,
)

/**
 * `POST /api/driver/devices`, the one driver endpoint that is live today.
 *
 * Any member may register a device, not only drivers, so this works before a dispatcher has granted
 * the driver membership the rest of the surface needs.
 */
class DeviceRegistryHttp(private val api: TmsApiClient) : DeviceRegistry {

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun register(
        platform: DevicePlatform,
        pushToken: String,
        appVersion: String?,
        locale: String?,
    ): AppResult<Unit> {
        val body = api.encode(
            DeviceInput.serializer(),
            DeviceInput(
                platform = platform.wire,
                pushToken = pushToken,
                appVersion = appVersion,
                locale = locale,
            ),
        )

        return api.post(
            path = PATH,
            body = body,
            deserializer = DeviceResponse.serializer(),
            idempotencyKey = Uuid.random().toString(),
        ).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(it.apiFailure?.toAppError() ?: AppError(it.message ?: "Device registration failed.")) },
        )
    }

    private companion object {
        const val PATH = "api/driver/devices"
    }
}
