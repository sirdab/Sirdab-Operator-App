package co.sirdab.driver.shared.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Thin DataStore-Preferences wrapper, for small values that must survive a restart. */
class KeyValueStore(private val dataStore: DataStore<Preferences>) {

    suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun getString(key: String): String? =
        dataStore.data.map { it[stringPreferencesKey(key)] }.first()

    suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}
