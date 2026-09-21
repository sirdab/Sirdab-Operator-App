package co.sirdab.driver.shared.core.queue

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import co.sirdab.driver.shared.core.network.FileUploader
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TmsJson
import co.sirdab.driver.shared.core.network.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

private class UploadClock(var millis: Long = 2_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

/** One request the queue made, flattened so a test can assert on the sequence. */
private data class Call(val method: String, val path: String, val body: String)

private fun OutgoingContent.text(): String = when (this) {
    is TextContent -> text
    is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
    else -> ""
}

/**
 * A proof photo is bytes plus a write that cites them, and the server refuses
 * the write until the bytes are in the bucket. These tests pin the three steps,
 * where a retry resumes, and the ordering that is the reason uploads share the
 * event queue rather than having one of their own.
 */
class WriteQueueUploadTest {

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

    private fun queue(
        dao: PendingWriteDao,
        clock: Clock,
        files: FakeLocalFileStore,
        calls: MutableList<Call>,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): WriteQueue {
        val http = HttpClient(
            MockEngine { request ->
                calls += Call(request.method.value, request.url.encodedPath, request.body.text())
                handler(request)
            },
        ) {
            expectSuccess = false
            install(ContentNegotiation) { json(TmsJson) }
            defaultRequest { url("http://127.0.0.1:4400/") }
        }
        val api = TmsApiClient(http, TokenProvider.Anonymous, ServerClock())
        return WriteQueue(
            dao = dao,
            api = api,
            uploader = FileUploader(api),
            files = files,
            clock = clock,
            random = Random(7),
        )
    }

    /** Mint, then upload, then post: the happy path in the order storage needs. */
    private suspend fun MockRequestHandleScope.route(
        request: HttpRequestData,
        fileId: String = "f-1",
    ): HttpResponseData = when {
        request.url.encodedPath.endsWith("/driver/files") -> respond(
            """{"fileId":"$fileId","uploadUrl":"http://storage.test/put/$fileId",""" +
                """"method":"PUT","headers":{"Content-Type":"image/jpeg"},""" +
                """"expiresAt":"2026-09-20T12:00:00Z"}""",
            HttpStatusCode.Created,
            jsonHeaders,
        )

        request.url.host == "storage.test" -> respond("", HttpStatusCode.OK)

        else -> respond("""{"id":"p1"}""", HttpStatusCode.Created, jsonHeaders)
    }

    @Test
    fun `an upload mints a file then puts the bytes then posts the proof`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, calls) { route(it) }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Drained::class)
        assertThat(calls.map { "${it.method} ${it.path}" }).isEqualTo(
            listOf(
                "POST /api/driver/files",
                "PUT /put/f-1",
                "POST /api/driver/stops/s1/proofs",
            ),
        )
        assertThat(dao.current).isEmpty()
    }

    @Test
    fun `the posted proof cites the file the server minted`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, calls) { route(it, fileId = "f-42") }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        q.drain()

        val proofPost = calls.last()
        assertThat(proofPost.body).contains("f-42")
        assertThat(proofPost.body).contains("fileIds")
    }

    @Test
    fun `the bytes are deleted from the device once the proof lands`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, mutableListOf()) { route(it) }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        q.drain()

        assertThat(files.paths).isEmpty()
    }

    @Test
    fun `a photo already in the bucket is not uploaded twice`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        // The post failed last time, but the bytes did land, which is what the
        // row records. A retry must not push the photo over the air again.
        dao.insert(
            PendingWrite(
                path = "api/driver/stops/s1/proofs",
                body = """{"proofType":"photo","fileIds":["f-9"]}""",
                idempotencyKey = "key-1",
                occurredAtMillis = 1L,
                createdAtMillis = 1L,
                kind = PendingWriteKind.UPLOAD,
                localPath = path,
                contentType = "image/jpeg",
                purpose = "proof_photo",
                fileId = "f-9",
                uploaded = true,
            ),
        )

        val q = queue(dao, UploadClock(), files, calls) { route(it) }
        q.drain()

        assertThat(calls.map { "${it.method} ${it.path}" })
            .isEqualTo(listOf("POST /api/driver/stops/s1/proofs"))
    }

    @Test
    fun `a failed upload keeps the row and the bytes for the next attempt`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, mutableListOf()) { request ->
            when {
                request.url.encodedPath.endsWith("/driver/files") -> route(request)
                // Storage is unreachable: nothing about this is the driver's fault.
                else -> respond("", HttpStatusCode.ServiceUnavailable)
            }
        }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Deferred::class)
        assertThat(dao.current.size).isEqualTo(1)
        assertThat(files.paths).contains(path)
        // Minting did succeed, and the body it rewrote is what the retry posts.
        assertThat(dao.current.first().fileId).isNotNull()
        assertThat(dao.current.first().uploaded).isEqualTo(false)
    }

    @Test
    fun `storage refusing the bytes parks the queue rather than losing the photo`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, mutableListOf()) { request ->
            when {
                request.url.encodedPath.endsWith("/driver/files") -> route(request)
                // The shape a storage rejection actually takes: a 400 that a
                // JSON write would treat as final.
                else -> respond(
                    """{"error":"invalid_mime_type","message":"not supported"}""",
                    HttpStatusCode.BadRequest,
                    jsonHeaders,
                )
            }
        }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        val result = q.drain()

        // The driver has left the stop; these bytes cannot be taken again, so
        // the queue parks with them intact instead of discarding the evidence.
        assertThat(result).isInstanceOf(DrainResult.Paused::class)
        assertThat(dao.current.size).isEqualTo(1)
        assertThat(files.paths).contains(path)
    }

    @Test
    fun `a photo that is gone from the device is dropped rather than posted`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()

        dao.insert(
            PendingWrite(
                path = "api/driver/stops/s1/proofs",
                body = """{"proofType":"photo"}""",
                idempotencyKey = "key-1",
                occurredAtMillis = 1L,
                createdAtMillis = 1L,
                kind = PendingWriteKind.UPLOAD,
                localPath = "/outbox/vanished.jpg",
                contentType = "image/jpeg",
                purpose = "proof_photo",
            ),
        )

        val q = queue(dao, UploadClock(), files, calls) { route(it) }
        val result = q.drain()

        // A proof with no bytes behind it would leave the stop looking
        // documented, which is worse than leaving it undocumented.
        assertThat(result).isInstanceOf(DrainResult.Dropped::class)
        assertThat(calls).isEmpty()
        assertThat(dao.current).isEmpty()
    }

    @Test
    fun `a proof goes before the event that needs it`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()
        val clock = UploadClock()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, clock, files, calls) { route(it) }

        // The driver photographs the load, then taps Delivered.
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )
        clock.millis += 1000
        q.enqueue("api/driver/trips/t1/events", """{"eventType":"delivered"}""", 2L)

        q.drain()

        // This ordering is the whole reason uploads share this queue: the other
        // way round the server answers photo_required, which drops the write
        // and loses a delivery the driver actually made.
        assertThat(calls.map { "${it.method} ${it.path}" }).isEqualTo(
            listOf(
                "POST /api/driver/files",
                "PUT /put/f-1",
                "POST /api/driver/stops/s1/proofs",
                "POST /api/driver/trips/t1/events",
            ),
        )
    }

    @Test
    fun `an event waits while its proof is still failing to upload`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val calls = mutableListOf<Call>()
        val clock = UploadClock()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, clock, files, calls) { request ->
            when {
                request.url.encodedPath.endsWith("/driver/files") ->
                    respond("", HttpStatusCode.ServiceUnavailable)
                else -> respond("""{"id":"e1"}""", HttpStatusCode.Created, jsonHeaders)
            }
        }

        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo","capturedAt":"2026-09-20T10:00:00Z"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )
        clock.millis += 1000
        q.enqueue("api/driver/trips/t1/events", """{"eventType":"delivered"}""", 2L)

        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Deferred::class)
        // The delivery stayed put rather than racing ahead of its evidence.
        assertThat(calls.map { it.path }).isEqualTo(listOf("/api/driver/files"))
        assertThat(dao.current.size).isEqualTo(2)
    }

    @Test
    fun `signing out takes the photos with the rows`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, mutableListOf()) { route(it) }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        q.clear()

        // One driver's evidence must not be attachable by the next person to
        // sign in on the same phone.
        assertThat(dao.current).isEmpty()
        assertThat(files.paths).isEmpty()
    }

    @Test
    fun `an upload row missing its file details is dropped rather than retried forever`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()

        dao.insert(
            PendingWrite(
                path = "api/driver/stops/s1/proofs",
                body = """{"proofType":"photo"}""",
                idempotencyKey = "key-1",
                occurredAtMillis = 1L,
                createdAtMillis = 1L,
                kind = PendingWriteKind.UPLOAD,
                localPath = null,
                contentType = null,
                purpose = null,
            ),
        )

        val q = queue(dao, UploadClock(), files, mutableListOf()) { route(it) }
        val result = q.drain()

        assertThat(result).isInstanceOf(DrainResult.Dropped::class)
        assertThat(dao.current).isEmpty()
    }

    @Test
    fun `the screen can see how many proofs a stop still owes`() = runTest {
        val dao = FakePendingWriteDao()
        val files = FakeLocalFileStore()
        val path = files.write("proof.jpg", "photo-bytes".toByteArray())

        val q = queue(dao, UploadClock(), files, mutableListOf()) { route(it) }
        q.enqueueUpload(
            path = "api/driver/stops/s1/proofs",
            body = """{"proofType":"photo"}""",
            occurredAtMillis = 1L,
            localPath = path,
            contentType = "image/jpeg",
            purpose = "proof_photo",
        )

        assertThat(q.observeUploadsFor("api/driver/stops/s1/proofs").first()).isEqualTo(1)
        assertThat(q.observeUploadsFor("api/driver/stops/s2/proofs").first()).isEqualTo(0)
    }
}
