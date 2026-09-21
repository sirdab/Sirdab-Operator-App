package co.sirdab.driver.shared.core.platform.files

import org.koin.core.module.Module

/**
 * Private, durable storage for bytes waiting to be uploaded.
 *
 * A proof photo is taken the moment the driver is standing at the stop, which
 * is exactly where the signal usually is not. The bytes therefore have to
 * outlive the process, not sit in memory next to a queue row that does.
 *
 * Nothing here is a cache: the platform is free to evict a cache under storage
 * pressure, and evicting a delivery photo before it uploads loses the proof
 * that the load arrived.
 */
interface LocalFileStore {

    /**
     * Writes [bytes] under [name] and returns the path to hand to the queue.
     *
     * The name is made unique by the caller; a collision would overwrite
     * another stop's proof.
     */
    suspend fun write(name: String, bytes: ByteArray): String

    /** Null when the file is gone, which the queue treats as a write it cannot finish. */
    suspend fun read(path: String): ByteArray?

    /** Idempotent, so a retry that already deleted the file is not an error. */
    suspend fun delete(path: String)
}

expect fun platformFileStoreModule(): Module
