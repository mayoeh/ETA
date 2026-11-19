package edu.virginia.cs4720.eta.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import edu.virginia.cs4720.eta.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class RouteResult(
    val distanceM: Int,
    val durationSec: Long,
    val path: List<LatLng>,
    val steps: List<RouteStep> = emptyList(),   // turn-by-turn; empty for saved routes
)

/** One turn-by-turn step from the Routes API, e.g. "Turn left onto McCormick Rd". */
data class RouteStep(
    val instruction: String,
    val maneuver: String,
    val distanceM: Int,
    val end: LatLng?,
)

/**
 * Google Maps Platform Routes API (computeRoutes), called over plain HTTPS.
 * Docs: https://developers.google.com/maps/documentation/routes/compute_route_directions
 */
class RoutesApi(context: Context) {
    private val appContext = context.applicationContext

    suspend fun compute(origin: LatLng, dest: LatLng, mode: String): RouteResult = withContext(Dispatchers.IO) {
        val travelMode = when (mode) {
            "bike" -> "BICYCLE"
            "bus" -> "TRANSIT"
            else -> "WALK"
        }
        val body = JSONObject().apply {
            put("origin", waypoint(origin))
            put("destination", waypoint(dest))
            put("travelMode", travelMode)
            if (travelMode == "TRANSIT") {
                put("transitPreferences", JSONObject().put("allowedTravelModes", JSONArray().put("BUS")))
            }
        }

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
            setRequestProperty(
                "X-Goog-FieldMask",
                "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
                    "routes.legs.steps.navigationInstruction,routes.legs.steps.distanceMeters,routes.legs.steps.endLocation",
            )
            // Needed because the API key is restricted to this Android app.
            setRequestProperty("X-Android-Package", appContext.packageName)
            signingSha1()?.let { setRequestProperty("X-Android-Cert", it) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Routes API error $code: ${errorMessage(text)}")

            val routes = JSONObject(text).optJSONArray("routes")
            if (routes == null || routes.length() == 0) throw IOException("No ${mode} route found")
            val r = routes.getJSONObject(0)
            RouteResult(
                distanceM = r.optInt("distanceMeters", 0),
                durationSec = r.optString("duration", "0s").removeSuffix("s").toDoubleOrNull()?.toLong() ?: 0L,
                path = Geo.decodePolyline(r.getJSONObject("polyline").getString("encodedPolyline")),
                steps = parseSteps(r),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSteps(route: JSONObject): List<RouteStep> {
        val steps = mutableListOf<RouteStep>()
        val legs = route.optJSONArray("legs") ?: return steps
        for (i in 0 until legs.length()) {
            val legSteps = legs.getJSONObject(i).optJSONArray("steps")
            if (legSteps != null) {
                for (j in 0 until legSteps.length()) {
                    val s = legSteps.getJSONObject(j)
                    val nav = s.optJSONObject("navigationInstruction")
                    val end = s.optJSONObject("endLocation")?.optJSONObject("latLng")
                    steps.add(
                        RouteStep(
                            instruction = nav?.optString("instructions").orEmpty().replace("\n", " "),
                            maneuver = nav?.optString("maneuver").orEmpty(),
                            distanceM = s.optInt("distanceMeters", 0),
                            end = end?.let { LatLng(it.optDouble("latitude"), it.optDouble("longitude")) },
                        ),
                    )
                }
            }
        }
        return steps
    }

    private fun waypoint(p: LatLng) = JSONObject().put(
        "location",
        JSONObject().put("latLng", JSONObject().put("latitude", p.latitude).put("longitude", p.longitude)),
    )

    private fun errorMessage(text: String): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(200))

    /** SHA-1 of the app's signing certificate, uppercase hex with no colons. */
    @Suppress("DEPRECATION")
    private fun signingSha1(): String? = runCatching {
        val pm = appContext.packageManager
        val signer = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }) ?: return null
        MessageDigest.getInstance("SHA-1").digest(signer.toByteArray())
            .joinToString("") { "%02X".format(it) }
    }.getOrNull()

    companion object {
        private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
    }
}
