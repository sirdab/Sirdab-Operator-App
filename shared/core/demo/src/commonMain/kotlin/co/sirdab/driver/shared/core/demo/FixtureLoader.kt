package co.sirdab.driver.shared.core.demo

import co.sirdab.driver.shared.core.demo.generated.resources.Res
import co.sirdab.driver.shared.core.model.AppNotification
import co.sirdab.driver.shared.core.model.CargoType
import co.sirdab.driver.shared.core.model.City
import co.sirdab.driver.shared.core.model.Document
import co.sirdab.driver.shared.core.model.DocStatus
import co.sirdab.driver.shared.core.model.DocType
import co.sirdab.driver.shared.core.model.Driver
import co.sirdab.driver.shared.core.model.HandlingFlag
import co.sirdab.driver.shared.core.model.LatLng
import co.sirdab.driver.shared.core.model.Load
import co.sirdab.driver.shared.core.model.NotificationKind
import co.sirdab.driver.shared.core.model.ReeferRange
import co.sirdab.driver.shared.core.model.Shipper
import co.sirdab.driver.shared.core.model.TimeWindow
import co.sirdab.driver.shared.core.model.TxnStatus
import co.sirdab.driver.shared.core.model.TxnType
import co.sirdab.driver.shared.core.model.VehicleType
import co.sirdab.driver.shared.core.model.VerificationState
import co.sirdab.driver.shared.core.model.Vehicle
import co.sirdab.driver.shared.core.model.WalletState
import co.sirdab.driver.shared.core.model.WalletTxn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
private const val MIN_MS = 60_000L

@Serializable
private data class CityDto(val id: String, val nameEn: String, val nameAr: String, val lat: Double, val lng: Double)

@Serializable
private data class ShipperDto(val id: String, val nameEn: String, val nameAr: String, val rating: Double, val loadsPosted: Int, val phone: String)

@Serializable
private data class LoadDto(
    val id: String,
    val shipperId: String,
    val originCityId: String,
    val destinationCityId: String,
    val distanceKm: Int,
    val suggestedRateSar: Int,
    val requiredVehicle: String,
    val cargoType: String,
    val weightTons: Double,
    val pickupInHours: Int,
    val windowHours: Int,
    val handlingFlags: List<String> = emptyList(),
    val reeferMinC: Int? = null,
    val reeferMaxC: Int? = null,
    val requiresCertification: String? = null,
    val stops: Int = 1,
    val instantBook: Boolean = false,
    val fixedRateSar: Int? = null,
    val polylineId: String? = null,
    val postedMinsAgo: Int,
    val distanceFromDriverKm: Int = 0,
)

/** Data that onboarding "unlocks" once the driver is verified. */
data class VerifiedSeed(
    val carrierScore: Double,
    val tripsCompleted: Int,
    val onTimePercent: Int,
    val wallet: WalletState,
    val documents: List<Document>,
    val notifications: List<AppNotification>,
)

/**
 * Loads demo fixtures from composeResources JSON and computes every date relative to
 * Clock.System.now() at load time (plan §6), so the demo never looks stale.
 */
class FixtureLoader(private val json: Json) {

    @OptIn(ExperimentalTime::class)
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    suspend fun buildInitialWorld(): WorldState {
        val now = now()
        val cityDtos = decode<List<CityDto>>("files/demo/cities.json")
        val shipperDtos = decode<List<ShipperDto>>("files/demo/shippers.json")
        val loadDtos = decode<List<LoadDto>>("files/demo/loads.json")

        val cities = cityDtos.map { City(it.id, it.nameEn, it.nameAr, LatLng(it.lat, it.lng)) }
        val cityById = cities.associateBy { it.id }
        val shippers = shipperDtos.map { Shipper(it.id, it.nameEn, it.nameAr, it.rating, it.loadsPosted, it.phone) }

        val loads = loadDtos.map { dto ->
            val pickupStart = now + dto.pickupInHours * HOUR_MS
            val pickupEnd = pickupStart + dto.windowHours * HOUR_MS
            val travelMs = (dto.distanceKm / 60.0 * HOUR_MS).toLong()
            val dropStart = pickupEnd + travelMs
            Load(
                id = dto.id,
                shipperId = dto.shipperId,
                originCityId = dto.originCityId,
                destinationCityId = dto.destinationCityId,
                originName = cityById[dto.originCityId]?.nameEn ?: dto.originCityId,
                destinationName = cityById[dto.destinationCityId]?.nameEn ?: dto.destinationCityId,
                distanceKm = dto.distanceKm,
                suggestedRateSar = dto.suggestedRateSar,
                requiredVehicle = VehicleType.valueOf(dto.requiredVehicle),
                cargoType = CargoType.valueOf(dto.cargoType),
                weightTons = dto.weightTons,
                pickupWindow = TimeWindow(pickupStart, pickupEnd),
                dropoffWindow = TimeWindow(dropStart, dropStart + dto.windowHours * HOUR_MS),
                handlingFlags = dto.handlingFlags.map { HandlingFlag.valueOf(it) },
                reeferRange = if (dto.reeferMinC != null && dto.reeferMaxC != null) ReeferRange(dto.reeferMinC, dto.reeferMaxC) else null,
                requiresCertification = dto.requiresCertification,
                stops = dto.stops,
                instantBook = dto.instantBook,
                fixedRateSar = dto.fixedRateSar,
                polylineId = dto.polylineId,
                postedAtMillis = now - dto.postedMinsAgo * MIN_MS,
                distanceFromDriverKm = dto.distanceFromDriverKm,
            )
        }

        return WorldState(
            driver = freshDriver(),
            personas = personas(),
            cities = cities,
            shippers = shippers,
            loads = loads,
            polylines = emptyList(),
            wallet = WalletState(availableSar = 0, pendingSar = 0),
            documents = missingDocuments(),
            notifications = emptyList(),
            onboardingComplete = false,
        )
    }

    fun verifiedSeed(): VerifiedSeed {
        val now = now()
        return VerifiedSeed(
            carrierScore = 4.6,
            tripsCompleted = 87,
            onTimePercent = 96,
            wallet = WalletState(
                availableSar = 3240,
                pendingSar = 8750,
                transactions = listOf(
                    WalletTxn("tx-1", TxnType.EARNING, 1680, TxnStatus.PENDING, "Riyadh → Dammam", "الرياض ← الدمام", now - 2 * HOUR_MS),
                    WalletTxn("tx-2", TxnType.EARNING, 3100, TxnStatus.SETTLED, "Jeddah → Riyadh", "جدة ← الرياض", now - 3 * DAY_MS),
                    WalletTxn("tx-3", TxnType.PAYOUT, -5000, TxnStatus.SETTLED, "Payout to bank", "تحويل للبنك", now - 5 * DAY_MS),
                    WalletTxn("tx-4", TxnType.EARNING, 1600, TxnStatus.SETTLED, "Dammam → Riyadh", "الدمام ← الرياض", now - 9 * DAY_MS),
                    WalletTxn("tx-5", TxnType.EARNING, 2900, TxnStatus.SETTLED, "Riyadh → Abha", "الرياض ← أبها", now - 12 * DAY_MS),
                    WalletTxn("tx-6", TxnType.EARNING, 1350, TxnStatus.SETTLED, "Riyadh → Qassim", "الرياض ← القصيم", now - 16 * DAY_MS),
                    WalletTxn("tx-7", TxnType.EARNING, 1750, TxnStatus.SETTLED, "Jubail → Riyadh", "الجبيل ← الرياض", now - 20 * DAY_MS),
                    WalletTxn("tx-8", TxnType.EARNING, 3300, TxnStatus.SETTLED, "Jeddah → Riyadh", "جدة ← الرياض", now - 26 * DAY_MS),
                    WalletTxn("tx-9", TxnType.EARNING, 1500, TxnStatus.SETTLED, "Jeddah → Madinah", "جدة ← المدينة", now - 33 * DAY_MS),
                ),
            ),
            documents = verifiedDocuments(now),
            notifications = listOf(
                AppNotification(
                    "n-welcome", NotificationKind.SYSTEM,
                    "Welcome aboard", "أهلاً بك",
                    "Your account is verified. Start bidding on loads.", "تم توثيق حسابك. ابدأ المزايدة على الشحنات.",
                    read = false, createdAtMillis = now,
                ),
                AppNotification(
                    "n-doc", NotificationKind.DOCUMENT,
                    "Document expiring soon", "مستند على وشك الانتهاء",
                    "Your operating card expires in 9 days.", "بطاقة التشغيل تنتهي خلال ٩ أيام.",
                    read = false, createdAtMillis = now,
                ),
            ),
        )
    }

    private fun freshDriver() = Driver(
        id = "driver-me",
        fullNameEn = "",
        fullNameAr = "",
        phone = "",
        verification = VerificationState.UNVERIFIED,
        personaKey = "new_unverified",
    )

    private fun personas() = listOf(
        Driver("p-flatbed", "Faisal Al-Otaibi", "فيصل العتيبي", "+966501234567", VerificationState.VERIFIED, 4.6, 87, 96, Vehicle(VehicleType.FLATBED, "RSH 4821", 25.0), "flatbed_verified"),
        Driver("p-new", "", "", "", VerificationState.UNVERIFIED, personaKey = "new_unverified"),
        Driver("p-van", "Bilal Khan", "بلال خان", "+966559876543", VerificationState.VERIFIED, 4.3, 41, 92, Vehicle(VehicleType.VAN_3T, "JED 2019", 3.0), "van_3t"),
    )

    private fun missingDocuments() = listOf(
        Document("doc-id", DocType.NATIONAL_ID, DocStatus.MISSING),
        Document("doc-license", DocType.DRIVING_LICENSE, DocStatus.MISSING),
        Document("doc-vehicle", DocType.VEHICLE_REGISTRATION, DocStatus.MISSING),
    )

    private fun verifiedDocuments(now: Long) = listOf(
        Document("doc-id", DocType.NATIONAL_ID, DocStatus.VERIFIED, "file://id", now + 365 * DAY_MS),
        Document("doc-license", DocType.DRIVING_LICENSE, DocStatus.VERIFIED, "file://license", now + 200 * DAY_MS),
        Document("doc-vehicle", DocType.VEHICLE_REGISTRATION, DocStatus.VERIFIED, "file://reg", now + 120 * DAY_MS),
        Document("doc-insurance", DocType.INSURANCE, DocStatus.VERIFIED, "file://ins", now + 60 * DAY_MS),
        // Deliberately expiring soon so the warning UI has something to show (plan §6).
        Document("doc-opcard", DocType.OPERATING_CARD, DocStatus.EXPIRING_SOON, "file://opcard", now + 9 * DAY_MS),
    )

    private suspend inline fun <reified T> decode(path: String): T =
        json.decodeFromString(Res.readBytes(path).decodeToString())
}
