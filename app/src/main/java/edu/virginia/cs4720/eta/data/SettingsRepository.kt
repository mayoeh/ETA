package edu.virginia.cs4720.eta.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** A place you often leave from, like Home or Work. */
data class SavedPlace(
    val label: String,     // "Home", "Work", ...
    val address: String,   // what the search returned, or "Current location when saved"
    val lat: Double,
    val lng: Double,
)

data class Settings(
    val defaultMode: String = "walk",   // walk | bike | bus
    val defaultBuffer: Int = 5,         // minutes to arrive early
    val simulateGps: Boolean = false,   // demo helper on the Route screen
    val places: List<SavedPlace> = emptyList(),
)

/** Local user settings, persisted with DataStore (not in Firestore). */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val MODE = stringPreferencesKey("default_mode")
        val BUFFER = intPreferencesKey("default_buffer")
        val SIMULATE = booleanPreferencesKey("simulate_gps")
        val PLACES = stringPreferencesKey("saved_places")   // JSON array
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            defaultMode = p[Keys.MODE] ?: "walk",
            defaultBuffer = p[Keys.BUFFER] ?: 5,
            simulateGps = p[Keys.SIMULATE] ?: false,
            places = decodePlaces(p[Keys.PLACES]),
        )
    }

    suspend fun setDefaultMode(mode: String) = context.dataStore.edit { it[Keys.MODE] = mode }
    suspend fun setDefaultBuffer(min: Int) = context.dataStore.edit { it[Keys.BUFFER] = min }
    suspend fun setSimulateGps(on: Boolean) = context.dataStore.edit { it[Keys.SIMULATE] = on }

    /** Adds a place, replacing any existing one with the same label (so "Home" can be updated). */
    suspend fun addPlace(place: SavedPlace) = context.dataStore.edit { p ->
        val list = decodePlaces(p[Keys.PLACES]).filterNot { it.label.equals(place.label, ignoreCase = true) } + place
        p[Keys.PLACES] = encodePlaces(list)
    }

    suspend fun removePlace(label: String) = context.dataStore.edit { p ->
        p[Keys.PLACES] = encodePlaces(decodePlaces(p[Keys.PLACES]).filterNot { it.label == label })
    }

    private fun encodePlaces(list: List<SavedPlace>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("label", it.label).put("address", it.address).put("lat", it.lat).put("lng", it.lng))
        }
        return arr.toString()
    }

    private fun decodePlaces(json: String?): List<SavedPlace> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedPlace(o.optString("label"), o.optString("address"), o.optDouble("lat"), o.optDouble("lng"))
            }
        }.getOrDefault(emptyList())
    }
}
