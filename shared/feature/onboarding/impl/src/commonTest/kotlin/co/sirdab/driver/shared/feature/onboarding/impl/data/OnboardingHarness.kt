package co.sirdab.driver.shared.feature.onboarding.impl.data

import co.sirdab.driver.shared.core.auth.SecureStore
import co.sirdab.driver.shared.core.auth.SessionManager
import co.sirdab.driver.shared.core.auth.SupabaseAuthApi
import co.sirdab.driver.shared.core.network.FileUploader
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsEnvironment
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.platform.files.LocalFileStore
import co.sirdab.driver.shared.core.queue.PendingWrite
import co.sirdab.driver.shared.core.queue.PendingWriteDao
import co.sirdab.driver.shared.core.queue.WriteQueue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A driver app wired to a fake TMS.
 *
 * The real session manager, the real API client and the real repositories, with one mock engine
 * underneath: these tests are about the conversation with the server, so everything between the
 * screen and the socket is the production code.
 */
internal class OnboardingHarness(
    val auth: AuthRepositoryTms,
    val onboarding: DriverOnboardingRepositoryTms,
    val session: SessionManager,
    val queue: WriteQueue,
    val dao: InMemoryWriteDao,
    val store: InMemoryStore,
    /** Every request but the code send, which every case makes and none is about. */
    val calls: MutableList<Call>,
) {
    /** What was asked of the server, and with what. */
    data class Call(val method: String, val path: String, val body: String?)

    fun paths(): List<String> = calls.map { it.path }

    fun bodyOf(path: String): String? = calls.lastOrNull { it.path == path }?.body
}

internal fun harness(
    /** Shared between two harnesses to stand for the same phone across a cold start. */
    store: InMemoryStore = InMemoryStore(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): OnboardingHarness {
    val calls = mutableListOf<OnboardingHarness.Call>()
    val http = HttpClient(
        MockEngine { request ->
            if (request.url.encodedPath == Api.OTP) {
                respondJson("{}")
            } else {
                calls += OnboardingHarness.Call(
                    method = request.method.value,
                    path = request.url.encodedPath,
                    body = (request.body as? TextContent)?.text,
                )
                handler(request)
            }
        },
    ) {
        expectSuccess = false
        install(ContentNegotiation) { json(TmsJson) }
        defaultRequest { url("http://127.0.0.1:4400/") }
    }

    val environment = TmsEnvironment(
        apiBaseUrl = "http://127.0.0.1:4400",
        supabaseUrl = "http://127.0.0.1:54321",
        supabaseAnonKey = "anon",
    )
    val session = SessionManager(SupabaseAuthApi(http, environment), store)
    val api = TmsApiClient(http, session, ServerClock())
    val dao = InMemoryWriteDao()
    val queue = WriteQueue(dao, api, FileUploader(api), InMemoryFileStore())
    val onboarding = DriverOnboardingRepositoryTms(api, session)

    return OnboardingHarness(
        auth = AuthRepositoryTms(session, DriverProfileRemoteHttp(api), onboarding, api, queue, store),
        onboarding = onboarding,
        session = session,
        queue = queue,
        dao = dao,
        store = store,
        calls = calls,
    )
}

/** The paths the driver app talks to, named once. */
internal object Api {
    const val OTP = "/auth/v1/otp"
    const val VERIFY = "/auth/v1/verify"
    const val REFRESH = "/auth/v1/token"
    const val PROFILE = "/api/driver/profile"
    const val TRUCKS = "/api/driver/profile/trucks"
    const val DOCUMENTS = "/api/driver/profile/documents"
    const val ME = "/api/driver/me"
    const val EVENTS = "/api/driver/trips/t1/events"
    const val ACTIVE_WORKSPACE = "/api/v1/me/active-workspace"
    const val STORAGE = "/storage/v1/object/upload/driver-documents/doc.jpg"
    const val STORAGE_URL = "http://127.0.0.1:54321$STORAGE?token=signed"
}

/** One queued trip event, as the outbox stores it. */
internal const val ARRIVED = """{"kind":"arrived"}"""

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf("Content-Type", listOf(ContentType.Application.Json.toString())),
)

internal fun MockRequestHandleScope.respondError(
    code: String,
    status: HttpStatusCode,
): HttpResponseData = respondJson("""{"error":{"code":"$code","message":"$code"}}""", status)

internal fun session(accessToken: String): String =
    """{"access_token":"$accessToken","refresh_token":"r1","expires_in":3600}"""

/** A driver on a fleet's roster: the trip surface will answer them. */
internal val DRIVER_TOKEN = token(
    """{"sub":"p1","app_metadata":{"workspace_id":"ws-1","organization_id":"org-1"},""" +
        """"tms_access":"driver","tms_driver_id":"drv-7"}""",
)

/** Verified, and carrying no fleet at all: the hook strips the access claims with the workspace. */
internal val UNLINKED_TOKEN = token("""{"sub":"p2"}""")

@OptIn(ExperimentalEncodingApi::class)
internal fun token(payload: String): String {
    val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val header = encoder.encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
    return "$header.${encoder.encode(payload.encodeToByteArray())}.signature"
}

/**
 * A driver profile as the server sends it.
 *
 * Defaults describe someone who has just verified a phone and nothing else, which is the state
 * most of these tests start from.
 */
internal fun profileJson(
    name: String = "",
    licenceNumber: String? = null,
    status: String = "pending_verification",
    onboardingCompletedAt: String? = null,
    missing: List<String> = listOf("name", "licence", "truck", "identity", "driving_licence"),
    trucks: List<String> = emptyList(),
    documents: List<String> = emptyList(),
    workspaces: List<String> = emptyList(),
): String = """
{
  "userId": "p2",
  "name": ${name.quoted()},
  "phone": "+966500000003",
  "nationality": null,
  "licenceNumber": ${licenceNumber?.quoted() ?: "null"},
  "licenceExpiresAt": null,
  "status": "$status",
  "onboardingCompletedAt": ${onboardingCompletedAt?.quoted() ?: "null"},
  "missing": ${missing.joinToString(prefix = "[", postfix = "]") { it.quoted() }},
  "trucks": ${trucks.joinToString(prefix = "[", postfix = "]")},
  "documents": ${documents.joinToString(prefix = "[", postfix = "]")},
  "workspaces": ${workspaces.joinToString(prefix = "[", postfix = "]")}
}
"""

internal fun truckJson(
    id: String = "t1",
    plate: String = "RUH 9002",
    type: String = "chilled",
    size: String = "closed_dyna",
): String =
    """{"id":"$id","licencePlate":"$plate","truckType":"$type","truckSize":"$size","capacityKg":4000}"""

internal fun documentJson(
    id: String = "d1",
    kind: String = "national_id",
    truckId: String? = null,
    status: String = "uploaded",
    rejectionReason: String? = null,
): String = """
{"id":"$id","kind":"$kind","truckId":${truckId?.quoted() ?: "null"},"contentType":"image/jpeg",
 "status":"$status","rejectionReason":${rejectionReason?.quoted() ?: "null"},"expiresAt":null,
 "uploadedAt":"2026-09-22T08:00:00+03:00","downloadUrl":null}
"""

internal fun workspaceJson(id: String = "ws-1"): String =
    """{"workspaceId":"$id","workspaceName":"Sirdab B2B","driverId":"drv-7","carrierId":"c-1"}"""

internal val ME_JSON = """
{"profile":{"id":"p1","name":"Nayef Al Rashidi","phone":"+966500000003"},
 "workspaceId":"ws-1",
 "driver":{"id":"drv-7","name":"Nayef Al Rashidi","status":"active",
           "licenceNumber":"DL-2020202","licenceExpiresAt":"2027-09-21"},
 "carrier":{"id":"c-1","name":"Nayef","kind":"independent"},
 "trucks":[]}
"""

private fun String.quoted(): String = "\"$this\""

/** True when the request is the signed PUT of the bytes themselves. */
internal fun HttpRequestData.isUpload(): Boolean = method == HttpMethod.Put

internal class InMemoryStore : SecureStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(key: String, value: String) { values[key] = value }
    override suspend fun get(key: String): String? = values[key]
    override suspend fun remove(key: String) { values.remove(key) }
}

/** Enough of the Room DAO for the queue to run against, without Room. */
internal class InMemoryWriteDao : PendingWriteDao {
    val rows = MutableStateFlow<List<PendingWrite>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(write: PendingWrite): Long {
        val id = nextId++
        rows.value = rows.value + write.copy(id = id)
        return id
    }

    override suspend fun all(): List<PendingWrite> = rows.value
    override fun observeAll(): Flow<List<PendingWrite>> = rows
    override fun observeCount(): Flow<Int> = rows.map { it.size }
    override fun observeUploadsFor(path: String): Flow<Int> =
        rows.map { all -> all.count { it.path == path && it.localPath != null } }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun markAttempted(
        id: Long,
        attempts: Int,
        nextAttemptAtMillis: Long,
        lastError: String?,
    ) {
        rows.value = rows.value.map { row ->
            if (row.id != id) {
                row
            } else {
                row.copy(attempts = attempts, nextAttemptAtMillis = nextAttemptAtMillis, lastError = lastError)
            }
        }
    }

    override suspend fun markMinted(id: Long, fileId: String, body: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(fileId = fileId, body = body) else it }
    }

    override suspend fun markUploaded(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(uploaded = true) else it }
    }

    override suspend fun resetUpload(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(fileId = null, uploaded = false) else it }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

internal class InMemoryFileStore : LocalFileStore {
    private val files = mutableMapOf<String, ByteArray>()

    override suspend fun write(name: String, bytes: ByteArray): String {
        files[name] = bytes
        return name
    }

    override suspend fun read(path: String): ByteArray? = files[path]

    override suspend fun delete(path: String) {
        files.remove(path)
    }
}
