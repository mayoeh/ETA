package edu.virginia.cs4720.eta.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import edu.virginia.cs4720.eta.data.LocationRepository
import edu.virginia.cs4720.eta.data.Plan
import edu.virginia.cs4720.eta.data.PlanRepository
import edu.virginia.cs4720.eta.data.RoutesApi
import edu.virginia.cs4720.eta.data.SavedRoute
import edu.virginia.cs4720.eta.data.SavedRouteRepository
import edu.virginia.cs4720.eta.data.SavedPlace
import edu.virginia.cs4720.eta.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the form edits, kept in the ViewModel so rotation doesn't lose it. */
data class PlanForm(
    val title: String = "",
    val category: String = "Class",
    val destName: String = "",
    val destPlaceId: String = "",
    val destLat: Double? = null,
    val destLng: Double? = null,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0,
    val repeatDays: Set<String> = emptySet(),
    val date: String = LocalDate.now().toString(),   // one-time date, or first day of a repeating plan
    val repeatUntil: String = "",                    // last day of a repeating plan ("" = no end)
    val bufferMin: String = "5",
    val travelMode: String = "walk",
    val originMode: String = "current",              // current | place
    val originName: String = "",
    val originLat: Double? = null,
    val originLng: Double? = null,
    val savedRouteId: String = "",
    val shared: Boolean = false,
) {
    val start: LocalTime get() = LocalTime.of(startHour, startMinute)
    val end: LocalTime get() = LocalTime.of(endHour, endMinute)
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()

    fun toPlan(id: String) = Plan(
        id = id,
        title = title.trim(),
        category = category,
        destName = destName,
        destPlaceId = destPlaceId,
        destLat = destLat ?: 0.0,
        destLng = destLng ?: 0.0,
        startTime = String.format(Locale.US, "%02d:%02d", startHour, startMinute),
        endTime = String.format(Locale.US, "%02d:%02d", endHour, endMinute),
        repeatDays = Plan.DAYS.filter { it in repeatDays },
        date = date,
        repeatUntil = if (isRepeating) repeatUntil else "",
        bufferMin = bufferMin.toIntOrNull() ?: 0,
        travelMode = travelMode,
        originMode = if (originMode == "place" && originLat != null) "place" else "current",
        originName = originName,
        originLat = originLat ?: 0.0,
        originLng = originLng ?: 0.0,
        savedRouteId = savedRouteId,
        shared = shared,
    )

    companion object {
        fun from(p: Plan): PlanForm {
            val s = p.localTime()
            val e = p.localEndTime()
            return PlanForm(
                title = p.title,
                category = p.category,
                destName = p.destName,
                destPlaceId = p.destPlaceId,
                destLat = p.destLat,
                destLng = p.destLng,
                startHour = s.hour,
                startMinute = s.minute,
                endHour = e.hour,
                endMinute = e.minute,
                repeatDays = p.repeatDays.toSet(),
                date = p.date.ifBlank { LocalDate.now().toString() },
                repeatUntil = p.repeatUntil,
                bufferMin = p.bufferMin.toString(),
                travelMode = p.travelMode,
                originMode = p.originMode,
                originName = p.originName,
                originLat = if (p.originMode == "place") p.originLat else null,
                originLng = if (p.originMode == "place") p.originLng else null,
                savedRouteId = p.savedRouteId,
                shared = p.shared,
            )
        }
    }
}

data class Suggestion(val placeId: String, val primary: String, val secondary: String)

/** Which search box a place search belongs to. */
enum class PlaceField { DEST, ORIGIN }

/** The travel-time check shown at the bottom of the form. */
data class TripPreview(val text: String, val detail: String = "")

class EditPlanViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val planId: String = saved.get<String>("planId") ?: "new"
    val isNew = planId == "new"
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val routes = RoutesApi(app)
    private val locations = LocationRepository(app)
    private val settingsRepo = SettingsRepository(app)

    var form by mutableStateOf(PlanForm(date = saved.get<String>("date") ?: LocalDate.now().toString()))
        private set
    var destQuery by mutableStateOf("")
        private set
    var destSuggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var originQuery by mutableStateOf("")
        private set
    var originSuggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var savedRoutes by mutableStateOf<List<SavedRoute>>(emptyList())
        private set
    var places by mutableStateOf<List<SavedPlace>>(emptyList())
        private set
    var preview by mutableStateOf<TripPreview?>(null)
        private set
    var loading by mutableStateOf(!isNew)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    private val places: PlacesClient by lazy { Places.createClient(getApplication<Application>()) }
    private var session = AutocompleteSessionToken.newInstance()
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private var near: LatLng? = null

    init {
        viewModelScope.launch {
            near = locations.lastKnown()
            val s = settingsRepo.settings.first()
            if (isNew) {
                form = form.copy(travelMode = s.defaultMode, bufferMin = s.defaultBuffer.toString())
            } else {
                try {
                    PlanRepository.get(uid, planId)?.let { form = PlanForm.from(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = e.message
                }
                loading = false
            }
            refreshPreview()
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { places = it.places }
        }
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                SavedRouteRepository.observe(uid)
                    .catch { message = "Couldn't load saved routes: ${it.message}" }
                    .collect { savedRoutes = it }
            }
        }
    }

    fun useSavedPlace(p: SavedPlace) {
        update(form.copy(originMode = "place", originName = p.label, originLat = p.lat, originLng = p.lng))
    }

    fun useAnotherPlace() {
        update(form.copy(originMode = "place", originName = "", originLat = null, originLng = null))
    }

    fun update(f: PlanForm) {
        val old = form
        form = f
        val tripChanged = old.destLat != f.destLat || old.destLng != f.destLng ||
            old.originMode != f.originMode || old.originLat != f.originLat || old.originLng != f.originLng ||
            old.travelMode != f.travelMode || old.savedRouteId != f.savedRouteId ||
            old.start != f.start || old.bufferMin != f.bufferMin
        if (tripChanged) refreshPreview()
    }

    /** Start time changed: keep the end after the start (default one hour long). */
    fun setStart(hour: Int, minute: Int) {
        val f = form.copy(startHour = hour, startMinute = minute)
        update(if (f.end.isAfter(f.start)) f else f.copy(endHour = (hour + 1) % 24, endMinute = minute))
    }

    fun setEnd(hour: Int, minute: Int) {
        val f = form.copy(endHour = hour, endMinute = minute)
        if (!f.end.isAfter(f.start)) {
            message = "The end time has to be after the start time."
            return
        }
        message = null
        update(f)
    }

    // ---- Place search (Places API New), shared by Destination and Leaving from ----

    fun onQuery(field: PlaceField, q: String) {
        if (field == PlaceField.DEST) destQuery = q else originQuery = q
        searchJob?.cancel()
        if (q.trim().length < 3) {
            setSuggestions(field, emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)   // wait for typing to pause
            try {
                val builder = FindAutocompletePredictionsRequest.builder()
                    .setQuery(q.trim())
                    .setSessionToken(session)
                near?.let {
                    builder.setLocationBias(
                        RectangularBounds.newInstance(
                            LatLng(it.latitude - 0.1, it.longitude - 0.1),
                            LatLng(it.latitude + 0.1, it.longitude + 0.1),
                        ),
                    )
                }
                val response = places.findAutocompletePredictions(builder.build()).await()
                setSuggestions(field, response.autocompletePredictions.map {
                    Suggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Place search failed: ${e.message}"
            }
        }
    }

    private fun setSuggestions(field: PlaceField, list: List<Suggestion>) {
        if (field == PlaceField.DEST) destSuggestions = list else originSuggestions = list
    }

    @Suppress("DEPRECATION")
    fun choose(field: PlaceField, s: Suggestion) {
        viewModelScope.launch {
            try {
                val request = FetchPlaceRequest.builder(s.placeId, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))
                    .setSessionToken(session)
                    .build()
                val place = places.fetchPlace(request).await().place
                val ll = place.latLng ?: throw IllegalStateException("That place has no location.")
                val name = place.name ?: s.primary
                if (field == PlaceField.DEST) {
                    update(form.copy(destName = name, destPlaceId = s.placeId, destLat = ll.latitude, destLng = ll.longitude))
                    destQuery = ""
                } else {
                    update(form.copy(originMode = "place", originName = name, originLat = ll.latitude, originLng = ll.longitude))
                    originQuery = ""
                }
                setSuggestions(field, emptyList())
                session = AutocompleteSessionToken.newInstance()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't load that place: ${e.message}"
            }
        }
    }

    fun dropOriginPin(ll: LatLng) {
        update(
            form.copy(
                originMode = "place",
                originName = String.format(Locale.US, "Pin %.5f, %.5f", ll.latitude, ll.longitude),
                originLat = ll.latitude,
                originLng = ll.longitude,
            ),
        )
        originSuggestions = emptyList()
    }

    fun dropPin(ll: LatLng) {
        update(
            form.copy(
                destName = String.format(Locale.US, "Pin %.5f, %.5f", ll.latitude, ll.longitude),
                destPlaceId = "",
                destLat = ll.latitude,
                destLng = ll.longitude,
            ),
        )
        destSuggestions = emptyList()
    }

    // ---- Travel-time preview ----

    /** Works out distance and time for the current choices, so you can see how long it'll take. */
    fun refreshPreview() {
        previewJob?.cancel()
        val f = form
        if (f.destLat == null || f.destLng == null) {
            preview = null
            return
        }
        previewJob = viewModelScope.launch {
            delay(400)
            preview = TripPreview("Checking travel time…")
            try {
                val trip = if (f.savedRouteId.isNotBlank()) {
                    savedRoutes.firstOrNull { it.id == f.savedRouteId }?.toRouteResult()
                        ?: SavedRouteRepository.get(uid, f.savedRouteId)?.toRouteResult()
                } else {
                    val origin = if (f.originMode == "place" && f.originLat != null && f.originLng != null) {
                        LatLng(f.originLat, f.originLng)
                    } else {
                        near = locations.lastKnown() ?: near
                        near
                    }
                    if (origin == null) {
                        preview = TripPreview("Can't check travel time yet", "No location fix. Pick a \"Leaving from\" place, or set the emulator location.")
                        return@launch
                    }
                    routes.compute(origin, LatLng(f.destLat, f.destLng), f.travelMode)
                }
                if (trip == null) {
                    preview = TripPreview("That saved route couldn't be found.")
                    return@launch
                }
                val leave = f.start.minusSeconds(trip.durationSec).minusMinutes((f.bufferMin.toIntOrNull() ?: 0).toLong())
                val from = if (f.originMode == "place" && f.originLat != null) f.originName else "your current location"
                preview = TripPreview(
                    text = "${Fmt.minutes(trip.durationSec)} · ${Fmt.distance(trip.distanceM.toDouble())}",
                    detail = "From $from by ${Fmt.mode(f.travelMode).lowercase()}" +
                        (if (f.savedRouteId.isNotBlank()) " (saved route)" else "") +
                        ". Leave by ${Fmt.time(leave)} to arrive ${f.bufferMin.ifBlank { "0" }} min early.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preview = TripPreview("Couldn't check travel time", e.message ?: "")
            }
        }
    }

    // ---- Save / delete ----

    fun save() {
        val f = form
        if (f.title.isBlank()) { message = "Give the plan a name."; return }
        if (f.destLat == null || f.destLng == null) { message = "Choose a destination."; return }
        if (f.originMode == "place" && f.originLat == null) { message = "Choose where you're leaving from, or pick My location."; return }
        if (f.isRepeating && f.repeatUntil.isNotBlank() && f.repeatUntil < f.date) {
            message = "The end date is before the start date."
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                PlanRepository.save(uid, f.toPlan(if (isNew) "" else planId))
                done = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Save failed: ${e.message}"
            } finally {
                saving = false
            }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            try {
                PlanRepository.delete(uid, planId)
                done = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Delete failed: ${e.message}"
            }
        }
    }
}

private val dayLabels = mapOf(
    "SUN" to "Su", "MON" to "M", "TUE" to "T", "WED" to "W", "THU" to "Th", "FRI" to "F", "SAT" to "Sa",
)
private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

private fun prettyDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(shortDate) }.getOrDefault(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(onDone: () -> Unit, onRecordRoute: () -> Unit, vm: EditPlanViewModel = viewModel()) {
    // Which picker dialog is open: "start", "end", "date", "until", or "" for none.
    var dialog by rememberSaveable { mutableStateOf("") }
    // Which pin a tap on the map sets: "dest" or "origin".
    var pinTarget by rememberSaveable { mutableStateOf("dest") }
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    val f = vm.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New plan" else "Edit plan", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onDone) { Text("<", color = Ink, fontSize = 18.sp) } },
                actions = {
                    if (!vm.isNew) {
                        TextButton(onClick = { dialog = "delete" }) { Text("Delete", color = Ink) }
                    }
                    TextButton(onClick = vm::save, enabled = !vm.saving) {
                        Text(if (vm.saving) "Saving…" else "Save", color = Ink, fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…") }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                vm.message?.let { NoticeBox(it) }

                OutlinedTextField(
                    value = f.title, onValueChange = { vm.update(f.copy(title = it)) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                Section("Category") {
                    ChoiceRow(Plan.CATEGORIES.map { it to it }, f.category, { vm.update(f.copy(category = it)) })
                }

                // ---- Where ----
                Section("Destination") {
                    SelectedPlace("Destination", if (f.destLat != null) f.destName else null)
                    PlaceSearch(
                        query = vm.destQuery,
                        label = if (f.destLat == null) "Search for a place" else "Search to change destination",
                        suggestions = vm.destSuggestions,
                        onQuery = { vm.onQuery(PlaceField.DEST, it) },
                        onChoose = { vm.choose(PlaceField.DEST, it) },
                    )
                }

                Section("Leaving from") {
                    val savedMatch = vm.places.firstOrNull {
                        f.originMode == "place" && f.originLat == it.lat && f.originLng == it.lng
                    }
                    val choice = when {
                        f.originMode != "place" -> "current"
                        savedMatch != null -> "saved:" + savedMatch.label
                        else -> "other"
                    }
                    OriginOption("My location", "Wherever you are when it's time to go", choice == "current") {
                        vm.update(f.copy(originMode = "current"))
                        pinTarget = "dest"
                    }
                    vm.places.forEach { place ->
                        OriginOption(place.label, place.address, choice == "saved:" + place.label) {
                            vm.useSavedPlace(place)
                        }
                    }
                    OriginOption("Another place", "Search or drop a pin on the map", choice == "other") {
                        vm.useAnotherPlace()
                        pinTarget = "origin"
                    }
                    if (vm.places.isEmpty()) {
                        Text("Tip: save Home, Work and other places in Settings to pick them here.", fontSize = 13.sp, color = Quiet)
                    }
                    if (choice == "other") {
                        SelectedPlace("Leaving from", if (f.originLat != null) f.originName else null)
                        PlaceSearch(
                            query = vm.originQuery,
                            label = if (f.originLat == null) "Search where you'll start" else "Search to change starting place",
                            suggestions = vm.originSuggestions,
                            onQuery = { vm.onQuery(PlaceField.ORIGIN, it) },
                            onChoose = { vm.choose(PlaceField.ORIGIN, it) },
                        )
                    }
                }

                Section("Map") {
                    ChoiceRow(
                        listOf("dest" to "Pin destination", "origin" to "Pin leaving from"),
                        pinTarget,
                        { pinTarget = it },
                    )
                    PlanMap(
                        form = f,
                        pinTarget = pinTarget,
                        onPick = { ll -> if (pinTarget == "origin") vm.dropOriginPin(ll) else vm.dropPin(ll) },
                    )
                    Text(
                        "Tap the map to place the ${if (pinTarget == "origin") "leaving-from" else "destination"} pin. " +
                            "Red pin = destination, blue pin = leaving from.",
                        fontSize = 13.sp, color = Quiet,
                    )
                }

                // ---- When ----
                Section("Time") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Starts (be there by)", fontSize = 13.sp, color = Quiet)
                            SecondaryButton(Fmt.time(f.start), { dialog = "start" }, Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Ends", fontSize = 13.sp, color = Quiet)
                            SecondaryButton(Fmt.time(f.end), { dialog = "end" }, Modifier.fillMaxWidth())
                        }
                    }
                    OutlinedTextField(
                        value = f.bufferMin,
                        onValueChange = { v -> vm.update(f.copy(bufferMin = v.filter { it.isDigit() }.take(3))) },
                        label = { Text("Arrive early (minutes)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Section("Repeats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Plan.DAYS.forEach { d ->
                            val on = d in f.repeatDays
                            ToggleBox(
                                label = dayLabels[d] ?: d,
                                on = on,
                                onToggle = { vm.update(f.copy(repeatDays = if (on) f.repeatDays - d else f.repeatDays + d)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (f.isRepeating) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Starts on", fontSize = 13.sp, color = Quiet)
                                SecondaryButton(prettyDate(f.date), { dialog = "date" }, Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Ends on", fontSize = 13.sp, color = Quiet)
                                SecondaryButton(
                                    if (f.repeatUntil.isBlank()) "No end date" else prettyDate(f.repeatUntil),
                                    { dialog = "until" },
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (f.repeatUntil.isNotBlank()) {
                            TextButton(onClick = { vm.update(f.copy(repeatUntil = "")) }) {
                                Text("Remove end date", color = Ink)
                            }
                        }
                    } else {
                        Text("One-time plan on", fontSize = 13.sp, color = Quiet)
                        SecondaryButton(prettyDate(f.date), { dialog = "date" }, Modifier.fillMaxWidth())
                    }
                }

                // ---- How ----
                Section("How you'll get there") {
                    ChoiceRow(
                        listOf("walk" to "Walk", "bike" to "Bike", "bus" to "Bus"),
                        f.travelMode,
                        { vm.update(f.copy(travelMode = it)) },
                    )
                }

                Section("Route") {
                    RouteOption(
                        title = "Fastest route",
                        detail = "Google Routes API picks the route each time",
                        selected = f.savedRouteId.isBlank(),
                        onClick = { vm.update(f.copy(savedRouteId = "")) },
                    )
                    vm.savedRoutes.forEach { r ->
                        RouteOption(
                            title = r.name,
                            detail = "${Fmt.distance(r.distanceM.toDouble())} · ${Fmt.minutes(r.durationSec)} · " +
                                (if (r.source == "recorded") "recorded" else "saved") + " · ${Fmt.mode(r.travelMode)}",
                            selected = f.savedRouteId == r.id,
                            onClick = { vm.update(f.copy(savedRouteId = r.id, travelMode = r.travelMode)) },
                        )
                    }
                    if (vm.savedRoutes.isEmpty()) {
                        Text("No saved routes yet. Save one from the Route screen, or record one.", fontSize = 13.sp, color = Quiet)
                    }
                    TextButton(onClick = onRecordRoute) { Text("+ Record a new route", color = Ink, fontWeight = FontWeight.Bold) }
                }

                // ---- Travel-time preview ----
                vm.preview?.let { p ->
                    Column(
                        Modifier.fillMaxWidth().border(2.dp, Line).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Travel time", fontSize = 13.sp, color = Quiet)
                        Text(p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (p.detail.isNotBlank()) Text(p.detail, fontSize = 13.sp, color = Quiet)
                        TextButton(onClick = vm::refreshPreview) { Text("Check again", color = Ink) }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Line)
                        .toggleable(value = f.shared, onValueChange = { vm.update(f.copy(shared = it)) }, role = Role.Switch)
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Share with friends", modifier = Modifier.weight(1f))
                    Switch(checked = f.shared, onCheckedChange = null)
                }

                if (!vm.isNew) {
                    SecondaryButton("Delete plan", { dialog = "delete" }, Modifier.fillMaxWidth())
                }
            }
        }
    }

    when (dialog) {
        "delete" -> {
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Delete this plan?") },
                text = {
                    Text(
                        if (f.isRepeating) "\"${f.title}\" and all its repeats will be removed."
                        else "\"${f.title}\" will be removed.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = ""
                        vm.delete()
                    }) { Text("Delete", color = Ink, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel", color = Ink) } },
                shape = RectangleShape,
            )
        }
        "start", "end" -> {
            val isStart = dialog == "start"
            val state = rememberTimePickerState(
                initialHour = if (isStart) f.startHour else f.endHour,
                initialMinute = if (isStart) f.startMinute else f.endMinute,
                is24Hour = false,
            )
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(if (isStart) "Starts (be there by)" else "Ends") },
                confirmButton = {
                    TextButton(onClick = {
                        if (isStart) vm.setStart(state.hour, state.minute) else vm.setEnd(state.hour, state.minute)
                        dialog = ""
                    }) { Text("OK", color = Ink) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel", color = Ink) } },
                text = { TimePicker(state = state) },
                shape = RectangleShape,
            )
        }
        "date", "until" -> {
            val isUntil = dialog == "until"
            val current = if (isUntil) f.repeatUntil.ifBlank { f.date } else f.date
            val state = rememberDatePickerState(initialSelectedDateMillis = toUtcMillis(current))
            DatePickerDialog(
                onDismissRequest = { dialog = "" },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val picked = fromUtcMillis(ms).toString()
                            vm.update(if (isUntil) vm.form.copy(repeatUntil = picked) else vm.form.copy(date = picked))
                        }
                        dialog = ""
                    }) { Text("OK", color = Ink) }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel", color = Ink) } },
                shape = RectangleShape,
            ) {
                DatePicker(state = state)
            }
        }
    }
}

/** The date picker works in UTC milliseconds at midnight. */
private fun toUtcMillis(iso: String): Long? =
    runCatching { LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()

private fun fromUtcMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(title)
        content()
    }
}

@Composable
private fun PlaceSearch(
    query: String,
    label: String,
    suggestions: List<Suggestion>,
    onQuery: (String) -> Unit,
    onChoose: (Suggestion) -> Unit,
) {
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    suggestions.forEach { s ->
        Column(Modifier.fillMaxWidth().border(1.dp, Line).clickable { onChoose(s) }.padding(10.dp)) {
            Text(s.primary, fontWeight = FontWeight.Bold)
            Text(s.secondary, fontSize = 13.sp, color = Quiet)
        }
    }
}

@Composable
private fun RouteOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, Line)
            .background(if (selected) Fill else Color.White)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Ink)
        Text(detail, fontSize = 13.sp, color = if (selected) Color.White else Quiet)
    }
}

/** One choice under "Leaving from" (My location, a saved place, or Another place). */
@Composable
private fun OriginOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, Line)
            .background(if (selected) Fill else Color.White)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Ink)
        Text(detail, fontSize = 13.sp, color = if (selected) Color.White else Quiet)
    }
}

/** Shows which place is chosen, in a box that stands out from the search field. */
@Composable
private fun SelectedPlace(label: String, name: String?) {
    Column(
        Modifier.fillMaxWidth().border(2.dp, Line).background(Tint).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Quiet, letterSpacing = 1.sp)
        Text(name ?: "Not chosen yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (name == null) Quiet else Ink)
    }
}

/** One map for both pins; a tap sets whichever pin [pinTarget] says. */
@Composable
private fun PlanMap(form: PlanForm, pinTarget: String, onPick: (LatLng) -> Unit) {
    val dest = if (form.destLat != null && form.destLng != null) LatLng(form.destLat, form.destLng) else null
    val origin = if (form.originMode == "place" && form.originLat != null && form.originLng != null) {
        LatLng(form.originLat, form.originLng)
    } else null

    // Defaults to Central Grounds until something is chosen.
    val camera = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(LatLng(38.0336, -78.5080), 14f)
    }
    LaunchedEffect(pinTarget, dest, origin) {
        val focus = (if (pinTarget == "origin") origin ?: dest else dest ?: origin)
        if (focus != null) camera.position = CameraPosition.fromLatLngZoom(focus, 16f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(220.dp).border(1.dp, Line),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
        onMapClick = onPick,
    ) {
        dest?.let { Marker(state = MarkerState(position = it), title = "Destination") }
        origin?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Leaving from",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
            )
        }
    }
}
