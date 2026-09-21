package co.sirdab.driver.shared.core.queue.di

import androidx.room.RoomDatabase
import co.sirdab.driver.shared.core.queue.WriteQueueDatabase
import co.sirdab.driver.shared.core.queue.writeQueueDatabaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun queuePlatformModule(): Module = module {
    single<RoomDatabase.Builder<WriteQueueDatabase>> { writeQueueDatabaseBuilder(androidContext()) }
}
