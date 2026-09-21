package co.sirdab.driver.shared.core.queue.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.sirdab.driver.shared.core.queue.PendingWriteDao
import co.sirdab.driver.shared.core.queue.WriteQueue
import co.sirdab.driver.shared.core.queue.WRITE_QUEUE_MIGRATIONS
import co.sirdab.driver.shared.core.queue.WriteQueueDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform binding that knows where the database file lives. */
expect fun queuePlatformModule(): Module

val queueModule: Module = module {
    single<WriteQueueDatabase> {
        get<RoomDatabase.Builder<WriteQueueDatabase>>()
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*WRITE_QUEUE_MIGRATIONS)
            // Off the main thread: a drain runs while the driver is looking at
            // the trip screen. Default rather than IO, which common KMP does not
            // expose; the queue is small and its queries are short.
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
    single<PendingWriteDao> { get<WriteQueueDatabase>().pendingWrites() }
    single { WriteQueue(dao = get(), api = get(), uploader = get(), files = get()) }
}
