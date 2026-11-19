package edu.virginia.cs4720.eta.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A route the user saved, stored at users/{uid}/savedRoutes/{routeId}. */
data class SavedRoute(
    val id: String = "",
    val name: String = "",
    val travelMode: String = "walk",
    val distanceM: Int = 0,
    val durationSec: Long = 0,
    val polyline: String = "",      // Google encoded polyline
    val startName: String = "",
    val endName: String = "",
    val source: String = "routes",  // routes (from the Routes API) | recorded (walked with GPS)
) {
    val path: List<LatLng> get() = Geo.decodePolyline(polyline)

    fun toRouteResult() = RouteResult(distanceM = distanceM, durationSec = durationSec, path = path)

    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "travelMode" to travelMode,
        "distanceM" to distanceM,
        "durationSec" to durationSec,
        "polyline" to polyline,
        "startName" to startName,
        "endName" to endName,
        "source" to source,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        fun from(id: String, m: Map<String, Any?>) = SavedRoute(
            id = id,
            name = m["name"] as? String ?: "",
            travelMode = m["travelMode"] as? String ?: "walk",
            distanceM = (m["distanceM"] as? Number)?.toInt() ?: 0,
            durationSec = (m["durationSec"] as? Number)?.toLong() ?: 0L,
            polyline = m["polyline"] as? String ?: "",
            startName = m["startName"] as? String ?: "",
            endName = m["endName"] as? String ?: "",
            source = m["source"] as? String ?: "routes",
        )
    }
}

object SavedRouteRepository {
    private fun col(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("savedRoutes")

    fun observe(uid: String): Flow<List<SavedRoute>> = callbackFlow {
        val reg = col(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.documents?.map { SavedRoute.from(it.id, it.data ?: emptyMap()) }?.sortedBy { it.name } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    suspend fun get(uid: String, id: String): SavedRoute? {
        val snap = col(uid).document(id).get().await()
        val data = snap.data ?: return null
        return SavedRoute.from(snap.id, data)
    }

    suspend fun save(uid: String, route: SavedRoute): String = col(uid).add(route.toMap()).await().id

    suspend fun delete(uid: String, id: String) {
        col(uid).document(id).delete().await()
    }
}

/**
 * Works out the trip for a plan:
 * a saved route if the plan has one, otherwise the Routes API from the plan's
 * "leaving from" place, or from [currentLocation] when it leaves from where you are.
 * Returns null if it needs your location and there isn't one yet.
 */
suspend fun resolveTrip(uid: String, plan: Plan, currentLocation: LatLng?, routes: RoutesApi): RouteResult? {
    if (plan.savedRouteId.isNotBlank()) {
        val saved = SavedRouteRepository.get(uid, plan.savedRouteId)
        if (saved != null && saved.polyline.isNotBlank()) return saved.toRouteResult()
        // Saved route was deleted: fall through to a live route.
    }
    val origin = if (plan.usesCurrentLocation) currentLocation ?: return null else plan.origin
    return routes.compute(origin, plan.dest, plan.travelMode)
}
