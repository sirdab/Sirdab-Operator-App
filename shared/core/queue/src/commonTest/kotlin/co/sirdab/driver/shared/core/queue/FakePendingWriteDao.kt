package co.sirdab.driver.shared.core.queue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for Room, so the drain rules can be tested without a
 * database. It preserves the one behaviour the queue depends on: [all] returns
 * rows in insertion (id) order, whatever their timestamps say.
 */
class FakePendingWriteDao : PendingWriteDao {

    private val rows = MutableStateFlow<List<PendingWrite>>(emptyList())
    private var nextId = 1L

    val current: List<PendingWrite> get() = rows.value.sortedBy { it.id }

    override suspend fun insert(write: PendingWrite): Long {
        val id = nextId++
        rows.value = rows.value + write.copy(id = id)
        return id
    }

    override suspend fun all(): List<PendingWrite> = rows.value.sortedBy { it.id }

    override fun observeAll(): Flow<List<PendingWrite>> = rows.map { list -> list.sortedBy { it.id } }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun markAttempted(
        id: Long,
        attempts: Int,
        nextAttemptAtMillis: Long,
        lastError: String?,
    ) {
        rows.value = rows.value.map {
            if (it.id == id) {
                it.copy(
                    attempts = attempts,
                    nextAttemptAtMillis = nextAttemptAtMillis,
                    lastError = lastError,
                )
            } else {
                it
            }
        }
    }

    override fun observeUploadsFor(path: String): Flow<Int> =
        rows.map { list ->
            list.count { it.kind == PendingWriteKind.UPLOAD && it.path == path }
        }

    override suspend fun markMinted(id: Long, fileId: String, body: String) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(fileId = fileId, body = body) else it
        }
    }

    override suspend fun markUploaded(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(uploaded = true) else it }
    }

    override suspend fun resetUpload(id: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(fileId = null, uploaded = false) else it
        }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}
