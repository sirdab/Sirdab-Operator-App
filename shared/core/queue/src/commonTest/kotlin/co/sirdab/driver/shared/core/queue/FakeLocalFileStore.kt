package co.sirdab.driver.shared.core.queue

import co.sirdab.driver.shared.core.platform.files.LocalFileStore

/** In-memory stand-in for the device's outbox directory. */
class FakeLocalFileStore : LocalFileStore {

    private val contents = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()

    val paths: Set<String> get() = contents.keys

    override suspend fun write(name: String, bytes: ByteArray): String {
        val path = "/outbox/$name"
        contents[path] = bytes
        return path
    }

    /** Puts bytes at an exact path, for a row built by hand in a test. */
    fun put(path: String, bytes: ByteArray) {
        contents[path] = bytes
    }

    override suspend fun read(path: String): ByteArray? = contents[path]

    override suspend fun delete(path: String) {
        contents.remove(path)
        deleted += path
    }
}
