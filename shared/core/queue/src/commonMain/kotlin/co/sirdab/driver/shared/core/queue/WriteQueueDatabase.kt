package co.sirdab.driver.shared.core.queue

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [PendingWrite::class], version = 2, exportSchema = true)
@ConstructedBy(WriteQueueDatabaseConstructor::class)
abstract class WriteQueueDatabase : RoomDatabase() {
    abstract fun pendingWrites(): PendingWriteDao
}

// Room's KSP processor generates the platform actuals for this.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object WriteQueueDatabaseConstructor : RoomDatabaseConstructor<WriteQueueDatabase> {
    override fun initialize(): WriteQueueDatabase
}

const val WRITE_QUEUE_DB = "driver_write_queue.db"

/**
 * Adds the upload columns.
 *
 * Migrated rather than recreated: this table is the driver's unsent work, and a
 * destructive migration would quietly delete an arrival recorded in a dead zone
 * the moment they installed an update.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE pending_writes ADD COLUMN kind TEXT NOT NULL DEFAULT 'json'",
        )
        connection.execSQL("ALTER TABLE pending_writes ADD COLUMN localPath TEXT")
        connection.execSQL("ALTER TABLE pending_writes ADD COLUMN contentType TEXT")
        connection.execSQL("ALTER TABLE pending_writes ADD COLUMN purpose TEXT")
        connection.execSQL("ALTER TABLE pending_writes ADD COLUMN fileId TEXT")
        connection.execSQL(
            "ALTER TABLE pending_writes ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val WRITE_QUEUE_MIGRATIONS = arrayOf(MIGRATION_1_2)
