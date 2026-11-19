package edu.virginia.cs4720.eta.data

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geometry helpers: polyline decoding and distance along a path. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Decodes Google's encoded polyline format into points. */
    fun decodePolyline(encoded: String): List<LatLng> {
        val points = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

            points.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return points
    }

    /** Encodes points into Google's polyline format (the reverse of [decodePolyline]). */
    fun encodePolyline(path: List<LatLng>): String {
        val out = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        for (p in path) {
            val lat = Math.round(p.latitude * 1e5).toInt()
            val lng = Math.round(p.longitude * 1e5).toInt()
            encodeValue(lat - prevLat, out)
            encodeValue(lng - prevLng, out)
            prevLat = lat
            prevLng = lng
        }
        return out.toString()
    }

    private fun encodeValue(value: Int, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        out.append((v + 63).toChar())
    }

    /** Great-circle distance in meters. */
    fun distanceM(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun pathLength(path: List<LatLng>): Double =
        path.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }

    fun center(path: List<LatLng>): LatLng {
        val minLat = path.minOf { it.latitude }
        val maxLat = path.maxOf { it.latitude }
        val minLng = path.minOf { it.longitude }
        val maxLng = path.maxOf { it.longitude }
        return LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2)
    }

    data class Progress(val walkedM: Double, val remainingM: Double, val totalM: Double) {
        val fraction: Float get() = if (totalM <= 0) 0f else (walkedM / totalM).toFloat().coerceIn(0f, 1f)
    }

    /**
     * How far along [path] the position [pos] is, measured along the path itself:
     * snaps [pos] to the closest point on the closest segment.
     */
    fun progressAlong(path: List<LatLng>, pos: LatLng): Progress {
        val total = pathLength(path)
        var bestDist = Double.MAX_VALUE
        var bestAlong = 0.0
        var walkedSoFar = 0.0
        for ((a, b) in path.zipWithNext()) {
            // Local flat projection around point a, in meters.
            val kx = 111_320.0 * cos(Math.toRadians(a.latitude))
            val ky = 110_540.0
            val bx = (b.longitude - a.longitude) * kx
            val by = (b.latitude - a.latitude) * ky
            val px = (pos.longitude - a.longitude) * kx
            val py = (pos.latitude - a.latitude) * ky
            val segLen2 = bx * bx + by * by
            val t = if (segLen2 == 0.0) 0.0 else ((px * bx + py * by) / segLen2).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            val d = sqrt(dx * dx + dy * dy)
            val segLen = distanceM(a, b)
            if (d < bestDist) {
                bestDist = d
                bestAlong = walkedSoFar + t * segLen
            }
            walkedSoFar += segLen
        }
        return Progress(walkedM = bestAlong, remainingM = (total - bestAlong).coerceAtLeast(0.0), totalM = total)
    }

    /** Compass bearing from [a] to [b], in degrees (0 = north), for pointing the map camera. */
    fun bearing(a: LatLng, b: LatLng): Float {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /** Splits [path] at [distM] meters: the part already walked and the part still ahead. */
    fun splitAt(path: List<LatLng>, distM: Double): Pair<List<LatLng>, List<LatLng>> {
        if (path.size < 2) return path to path
        val cut = pointAt(path, distM)
        val walked = mutableListOf(path.first())
        var left = distM
        var i = 0
        while (i < path.size - 1) {
            val seg = distanceM(path[i], path[i + 1])
            if (left <= seg) break
            left -= seg
            walked.add(path[i + 1])
            i++
        }
        walked.add(cut)
        val ahead = listOf(cut) + path.drop(i + 1)
        return walked to ahead
    }

    /** The point [distM] meters along [path] (used by the simulated walk). */
    fun pointAt(path: List<LatLng>, distM: Double): LatLng {
        var left = distM
        for ((a, b) in path.zipWithNext()) {
            val seg = distanceM(a, b)
            if (left <= seg && seg > 0) {
                val t = left / seg
                return LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
            left -= seg
        }
        return path.last()
    }
}
