package edu.virginia.cs4720.eta.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.tasks.await

data class PlaceHit(val placeId: String, val primary: String, val secondary: String)
data class FoundPlace(val name: String, val address: String, val location: LatLng)

/** Places API (New): type-ahead search, then the chosen place's name and location. */
class PlaceFinder(context: Context) {
    private val client by lazy { Places.createClient(context.applicationContext) }
    private var session = AutocompleteSessionToken.newInstance()

    suspend fun search(query: String, near: LatLng?): List<PlaceHit> {
        val builder = FindAutocompletePredictionsRequest.builder()
            .setQuery(query.trim())
            .setSessionToken(session)
        near?.let {
            builder.setLocationBias(
                RectangularBounds.newInstance(
                    LatLng(it.latitude - 0.1, it.longitude - 0.1),
                    LatLng(it.latitude + 0.1, it.longitude + 0.1),
                ),
            )
        }
        return client.findAutocompletePredictions(builder.build()).await().autocompletePredictions.map {
            PlaceHit(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
        }
    }

    @Suppress("DEPRECATION")
    suspend fun fetch(hit: PlaceHit): FoundPlace {
        val request = FetchPlaceRequest.builder(hit.placeId, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
            .setSessionToken(session)
            .build()
        val place = client.fetchPlace(request).await().place
        session = AutocompleteSessionToken.newInstance()   // a session ends when a place is picked
        val ll = place.latLng ?: throw IllegalStateException("That place has no location.")
        val address = listOf(hit.primary, hit.secondary).filter { it.isNotBlank() }.joinToString(", ")
        return FoundPlace(place.name ?: hit.primary, address, ll)
    }
}
