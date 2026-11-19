package edu.virginia.cs4720.eta.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * One plan, stored at users/{uid}/plans/{planId}.
 *
 * Timing: [startTime] is the be-there-by time and [endTime] is when it ends.
 * Days: a one-time plan happens on [date]. A repeating plan happens on [repeatDays],
 * starting on [date] and ending on [repeatUntil] (blank = no end date).
 * Origin: "current" uses your live location; "place" leaves from [originName].
 * Route: [savedRouteId] picks one of your saved routes; blank = ask the Routes API.
 */
data class Plan(
    val id: String = "",
    val title: String = "",
    val category: String = "Class",
    val destName: String = "",
    val destPlaceId: String = "",
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val startTime: String = "09:00",          // "HH:mm"
    val endTime: String = "10:00",            // "HH:mm"
    val repeatDays: List<String> = emptyList(),
    val date: String = "",                     // yyyy-MM-dd
    val repeatUntil: String = "",              // yyyy-MM-dd or blank
    val bufferMin: Int = 5,
    val travelMode: String = "walk",           // walk | bike | bus
    val originMode: String = "current",        // current | place
    val originName: String = "",
    val originLat: Double = 0.0,
    val originLng: Double = 0.0,
    val savedRouteId: String = "",
    val alertOn: Boolean = true,
    val shared: Boolean = false,
    val invitees: List<String> = emptyList(),
) {
    val dest: LatLng get() = LatLng(destLat, destLng)
    val origin: LatLng get() = LatLng(originLat, originLng)
    val usesCurrentLocation: Boolean get() = originMode != "place"

    /** True when working out the trip needs your live location. */
    val needsLiveLocation: Boolean get() = usesCurrentLocation && savedRouteId.isBlank()

    fun localTime(): LocalTime =
        runCatching { LocalTime.parse(startTime) }.getOrDefault(LocalTime.NOON)

    fun localEndTime(): LocalTime =
        runCatching { LocalTime.parse(endTime) }.getOrDefault(localTime().plusHours(1))

    fun occursOn(day: LocalDate): Boolean {
        if (repeatDays.isEmpty()) return date == day.toString()
        if (dayCode(day.dayOfWeek) !in repeatDays) return false
        val first = runCatching { LocalDate.parse(date) }.getOrNull()
        val last = runCatching { LocalDate.parse(repeatUntil) }.getOrNull()
        if (first != null && day.isBefore(first)) return false
        if (last != null && day.isAfter(last)) return false
        return true
    }

    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "category" to category,
        "destName" to destName,
        "destPlaceId" to destPlaceId,
        "destLat" to destLat,
        "destLng" to destLng,
        "startTime" to startTime,
        "endTime" to endTime,
        "repeatDays" to repeatDays,
        "date" to date,
        "repeatUntil" to repeatUntil,
        "bufferMin" to bufferMin,
        "travelMode" to travelMode,
        "originMode" to originMode,
        "originName" to originName,
        "originLat" to originLat,
        "originLng" to originLng,
        "savedRouteId" to savedRouteId,
        "alertOn" to alertOn,
        "shared" to shared,
        "invitees" to invitees,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        val CATEGORIES = listOf("Class", "Work", "Errand", "Social")
        val DAYS = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        /** DayOfWeek.value is MON=1..SUN=7, so % 7 puts Sunday at index 0. */
        fun dayCode(dow: DayOfWeek): String = DAYS[dow.value % 7]

        private fun num(m: Map<String, Any?>, key: String): Double = (m[key] as? Number)?.toDouble() ?: 0.0
        private fun str(m: Map<String, Any?>, key: String, default: String = ""): String = m[key] as? String ?: default
        private fun strings(m: Map<String, Any?>, key: String): List<String> =
            (m[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        fun from(id: String, m: Map<String, Any?>): Plan {
            val start = str(m, "startTime", "09:00")
            val defaultEnd = runCatching { LocalTime.parse(start).plusHours(1).toString() }.getOrDefault("10:00")
            return Plan(
                id = id,
                title = str(m, "title"),
                category = str(m, "category", "Class"),
                destName = str(m, "destName"),
                destPlaceId = str(m, "destPlaceId"),
                destLat = num(m, "destLat"),
                destLng = num(m, "destLng"),
                startTime = start,
                endTime = str(m, "endTime", defaultEnd),
                repeatDays = strings(m, "repeatDays"),
                date = str(m, "date"),
                repeatUntil = str(m, "repeatUntil"),
                bufferMin = (m["bufferMin"] as? Number)?.toInt() ?: 5,
                travelMode = str(m, "travelMode", "walk"),
                originMode = str(m, "originMode", "current"),
                originName = str(m, "originName"),
                originLat = num(m, "originLat"),
                originLng = num(m, "originLng"),
                savedRouteId = str(m, "savedRouteId"),
                alertOn = m["alertOn"] as? Boolean ?: true,
                shared = m["shared"] as? Boolean ?: false,
                invitees = strings(m, "invitees"),
            )
        }
    }
}
