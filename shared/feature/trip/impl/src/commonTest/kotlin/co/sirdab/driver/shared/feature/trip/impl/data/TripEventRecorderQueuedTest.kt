package co.sirdab.driver.shared.feature.trip.impl.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import co.sirdab.driver.shared.core.analytics.testing.RecordingAnalytics
import co.sirdab.driver.shared.core.network.FileUploader
import co.sirdab.driver.shared.core.network.ServerClock
import co.sirdab.driver.shared.core.network.TmsApiClient
import co.sirdab.driver.shared.core.network.TokenProvider
import co.sirdab.driver.shared.core.platform.files.LocalFileStore
import co.sirdab.driver.shared.core.queue.PendingWrite
import co.sirdab.driver.shared.core.queue.PendingWriteDao
import co.sirdab.driver.shared.core.queue.WriteQueue
import co.sirdab.driver.shared.feature.trip.api.ProofType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * What the recorder puts in the outbox for a proof, and what it reads back out.
 *
 * Nothing is sent here: the drain runs in a scope that is already cancelled, so every row stays
 * exactly as it was queued.
 */
class TripEventRecorderQueuedTest {

    private val dao = RowsDao()
    private val files = MemoryFiles()
    private val recorder = TripEventRecorderQueued(
        queue = WriteQueue(dao, API, FileUploader(API), files),
        files = files,
        analytics = RecordingAnalytics(),
        scope = CoroutineScope(Job().apply { cancel() }),
    )

    @Test
    fun `a delivery note is queued as a document photo of its own`() = runTest {
        recorder.attachPhotoProof("s1", byteArrayOf(1), "image/jpeg", capturedAtMillis = 2L, type = ProofType.DOCUMENT)

        val note = dao.rows.value.single()
        // The type is the whole difference to the trip page: a document at a dropoff is the POD.
        assertThat(note.body).contains(""""proofType":"document"""")
        assertThat(note.purpose).isEqualTo("proof_photo")
        assertThat(note.path).isEqualTo("api/driver/stops/s1/proofs")
    }

    @Test
    fun `only a queued photo counts toward the photo a delivery needs`() = runTest {
        recorder.attachPhotoProof("s1", byteArrayOf(1), "image/jpeg", capturedAtMillis = 1L)
        recorder.attachPhotoProof("s1", byteArrayOf(2), "image/jpeg", capturedAtMillis = 1L, type = ProofType.DOCUMENT)
        recorder.attachPhotoProof("s2", byteArrayOf(3), "image/jpeg", capturedAtMillis = 1L)

        // The note goes to the same endpoint, but the server's photo rule does not count it.
        assertThat(recorder.pendingProofs("s1").first()).isEqualTo(1)
        assertThat(recorder.pendingProofs("s2").first()).isEqualTo(1)
        // Taken in the same millisecond, the two still land in two files.
        assertThat(dao.rows.value.map { it.localPath }.toSet().size).isEqualTo(3)
    }

    @Test
    fun `a photo still counts once its file is minted`() = runTest {
        recorder.attachPhotoProof("s1", byteArrayOf(1), "image/jpeg", capturedAtMillis = 1L)
        val row = dao.rows.value.single()

        // What the queue does after the mint: the same body, with the file cited.
        dao.markMinted(row.id, "f1", row.body.removeSuffix("}") + ""","fileIds":["f1"]}""")

        assertThat(recorder.pendingProofs("s1").first()).isEqualTo(1)
    }

    private companion object {
        /** Never reached: nothing in these cases drains. */
        val API = TmsApiClient(
            HttpClient(MockEngine { error("unexpected ${it.url}") }),
            TokenProvider.Anonymous,
            ServerClock(),
        )
    }
}

private class RowsDao : PendingWriteDao {
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

    override suspend fun markAttempted(id: Long, attempts: Int, nextAttemptAtMillis: Long, lastError: String?) = Unit

    override suspend fun markMinted(id: Long, fileId: String, body: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(fileId = fileId, body = body) else it }
    }

    override suspend fun markUploaded(id: Long) = Unit
    override suspend fun resetUpload(id: Long) = Unit

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private class MemoryFiles : LocalFileStore {
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
