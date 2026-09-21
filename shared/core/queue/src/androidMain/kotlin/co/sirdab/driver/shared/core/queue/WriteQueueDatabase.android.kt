package co.sirdab.driver.shared.core.queue

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun writeQueueDatabaseBuilder(context: Context): RoomDatabase.Builder<WriteQueueDatabase> {
    val file = context.getDatabasePath(WRITE_QUEUE_DB)
    return Room.databaseBuilder<WriteQueueDatabase>(
        context = context.applicationContext,
        name = file.absolutePath,
    )
}
