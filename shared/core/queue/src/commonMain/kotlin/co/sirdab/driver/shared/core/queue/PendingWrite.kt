package co.sirdab.driver.shared.core.queue

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Stored as text rather than an enum so the column survives reordering the constants. */
object PendingWriteKind {
    /** A single JSON POST. */
    const val JSON = "json"

    /** Bytes on disk that must reach storage before the JSON POST that cites them. */
    const val UPLOAD = "upload"
}

/**
 * One write the driver made that has not reached the server yet.
 *
 * [body] is stored as the exact string that will be sent, not as an object to
 * re-encode. The server hashes `method + "\n" + path + "\n" + raw body` to decide
 * whether a retry is a replay of the same write, so re-serializing between
 * attempts could reorder keys, change the hash, and earn a 409 for a write the
 * driver only made once.
 *
 * [idempotencyKey] is generated when the driver taps and never regenerated. It
 * is what makes the retry safe.
 *
 * An [PendingWriteKind.UPLOAD] row is the same write with a file in front of it:
 * the bytes at [localPath] have to be in the bucket before [body] is posted,
 * because the body cites the file by id and the server refuses a proof whose
 * bytes never arrived. Keeping it in this table rather than a queue of its own
 * is what guarantees a photo lands before the `delivered` event that needs it.
 */
@Entity(tableName = "pending_writes")
data class PendingWrite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val body: String,
    val idempotencyKey: String,
    /** Device clock at the moment of the tap, already inside [body]. */
    val occurredAtMillis: Long,
    val createdAtMillis: Long,
    val attempts: Int = 0,
    /** Earliest time this may be tried again, moved out by the backoff. */
    val nextAttemptAtMillis: Long = 0,
    /** Set when a failure is terminal, so the row can be surfaced then removed. */
    val lastError: String? = null,
    /** One of [PendingWriteKind]. */
    val kind: String = PendingWriteKind.JSON,
    /** Where the bytes are on this device, for an upload row. */
    val localPath: String? = null,
    val contentType: String? = null,
    /** The contract's FilePurpose, sent when minting the upload target. */
    val purpose: String? = null,
    /**
     * The file the server minted. Null until the mint succeeds. Minted again only when the
     * bytes never reached storage, since the signed upload target is not kept.
     */
    val fileId: String? = null,
    /** Set once the bytes are in the bucket, so a retry resumes at the POST. */
    val uploaded: Boolean = false,
    /**
     * Who recorded this (the session's JWT `sub`). Null on rows from before the column existed,
     * which are taken to be the current driver's, since signing out has always cleared the queue.
     */
    val ownerId: String? = null,
)

@Dao
interface PendingWriteDao {

    @Insert
    suspend fun insert(write: PendingWrite): Long

    /**
     * The queue drains in the order the driver acted.
     *
     * Ordering is not cosmetic: trip events are the source of truth for a trip's
     * status, so an arrival that lands after the departure for the same stop
     * leaves dispatch with a timeline that cannot have happened.
     *
     * By [PendingWrite.id], never by a timestamp: the id only ever grows, while
     * the device clock can step backwards (an NTP correction, a driver fixing a
     * wrong time) between two taps and put the departure ahead of the arrival.
     */
    @Query("SELECT * FROM pending_writes ORDER BY id ASC")
    suspend fun all(): List<PendingWrite>

    @Query("SELECT * FROM pending_writes ORDER BY id ASC")
    fun observeAll(): Flow<List<PendingWrite>>

    @Query("SELECT COUNT(*) FROM pending_writes")
    fun observeCount(): Flow<Int>

    /**
     * Rows still owing a proof for this stop.
     *
     * The screen reads it to tell "no proof yet" apart from "the proof is on its
     * way", which are the same thing to the server and very different things to
     * a driver standing at a gate.
     */
    @Query(
        "SELECT COUNT(*) FROM pending_writes WHERE kind = 'upload' AND path = :path",
    )
    fun observeUploadsFor(path: String): Flow<Int>

    @Query("DELETE FROM pending_writes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE pending_writes SET attempts = :attempts, " +
            "nextAttemptAtMillis = :nextAttemptAtMillis, lastError = :lastError WHERE id = :id",
    )
    suspend fun markAttempted(
        id: Long,
        attempts: Int,
        nextAttemptAtMillis: Long,
        lastError: String?,
    )

    /**
     * Remember the minted file and the body that now cites it.
     *
     * Both together, because the body is only valid for that file: storing one
     * without the other would leave a retry posting a proof for a file it no
     * longer references.
     */
    @Query("UPDATE pending_writes SET fileId = :fileId, body = :body WHERE id = :id")
    suspend fun markMinted(id: Long, fileId: String, body: String)

    @Query("UPDATE pending_writes SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    /**
     * Forget the minted file, so the next attempt mints and uploads again.
     *
     * For the one refusal that says the bytes never arrived (`file_not_ready`):
     * the photo is still on the device, so the proof is recoverable by redoing
     * the upload rather than lost by dropping the row.
     */
    @Query("UPDATE pending_writes SET fileId = NULL, uploaded = 0 WHERE id = :id")
    suspend fun resetUpload(id: Long)

    @Query("DELETE FROM pending_writes")
    suspend fun clear()
}
