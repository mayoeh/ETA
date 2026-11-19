package edu.virginia.cs4720.eta.ui

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import edu.virginia.cs4720.eta.data.LocationRepository
import edu.virginia.cs4720.eta.data.Plan
import edu.virginia.cs4720.eta.data.PlanRepository
import edu.virginia.cs4720.eta.data.RouteResult
import edu.virginia.cs4720.eta.data.RoutesApi
import edu.virginia.cs4720.eta.data.Settings
import edu.virginia.cs4720.eta.data.SettingsRepository
import edu.virginia.cs4720.eta.data.resolveTrip
import edu.virginia.cs4720.eta.data.SavedRoute
import edu.virginia.cs4720.eta.data.SavedRouteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Route status for the next plan on the Today screen. */
sealed interface RouteState {
    data object Idle : RouteState
    data class Loading(val planId: String) : RouteState
    data class Ready(val planId: String, val result: RouteResult) : RouteState
    data class Failed(val planId: String, val message: String) : RouteState
}

class TodayViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val routes = RoutesApi(app)
    private val locations = LocationRepository(app)

    val settings: StateFlow<Settings> =
        SettingsRepository(app).settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    // The day being viewed is kept in SavedStateHandle so it survives rotation and process death.
    val selectedDate: StateFlow<LocalDate> = saved.getStateFlow(KEY_DATE, LocalDate.now().toString())
        .map { LocalDate.parse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    val now = MutableStateFlow(LocalDateTime.now())
    val location = MutableStateFlow<LatLng?>(null)
    val route = MutableStateFlow<RouteState>(RouteState.Idle)
    val error = MutableStateFlow<String?>(null)
    private val plans = MutableStateFlow<List<Plan>>(emptyList())
    val savedRoutes = MutableStateFlow<List<SavedRoute>>(emptyList())

    val dayPlans: StateFlow<List<Plan>> = combine(plans, selectedDate) { ps, day ->
        ps.filter { it.occursOn(day) }.sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The first plan today whose be-there-by time hasn't passed yet. */
    val nextPlan: StateFlow<Plan?> = combine(plans, now) { ps, n ->
        ps.filter { it.occursOn(n.toLocalDate()) }
            .sortedBy { it.startTime }
            .firstOrNull { it.localTime().isAfter(n.toLocalTime()) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var locationStarted = false

    init {
        // Clock for the countdown.
        viewModelScope.launch {
            while (true) {
                now.value = LocalDateTime.now()
                delay(1_000)
            }
        }
        // Saved routes, for the "I'm leaving" route picker.
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                SavedRouteRepository.observe(uid)
                    .catch { error.value = it.message }
                    .collect { savedRoutes.value = it }
            }
        }
        // Live plans from Firestore.
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                PlanRepository.observe(uid)
                    .catch { error.value = it.message }
                    .collect { plans.value = it }
            }
        }
        // Ask the Routes API again whenever the next plan changes or we get our first location fix.
        viewModelScope.launch {
            combine(nextPlan, location.map { it != null }.distinctUntilChanged()) { p, hasLoc -> p to hasLoc }
                .collectLatest { (p, hasLoc) ->
                    if (p == null) {
                        route.value = RouteState.Idle
                    } else if (hasLoc || !p.needsLiveLocation) {
                        computeRoute(p)
                    }
                }
        }
    }

    fun startLocation() {
        if (locationStarted) return
        locationStarted = true
        viewModelScope.launch {
            locations.updates()
                .catch { error.value = it.message }
                .collect { location.value = it }
        }
    }

    fun refreshRoute() {
        val p = nextPlan.value ?: return
        viewModelScope.launch { computeRoute(p) }
    }

    private suspend fun computeRoute(p: Plan) {
        if (p.needsLiveLocation && location.value == null) return
        route.value = RouteState.Loading(p.id)
        route.value = try {
            val trip = resolveTrip(uid, p, location.value, routes)
            if (trip == null) RouteState.Idle else RouteState.Ready(p.id, trip)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RouteState.Failed(p.id, e.message ?: "Could not get a route.")
        }
    }

    fun shiftDay(days: Long) {
        saved[KEY_DATE] = selectedDate.value.plusDays(days).toString()
    }

    companion object {
        private const val KEY_DATE = "date"
    }
}

private val dateHeader = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddPlan: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    onLeave: (planId: String, routeChoice: String) -> Unit,
    vm: TodayViewModel = viewModel(),
) {
    val context = LocalContext.current
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val plans by vm.dayPlans.collectAsStateWithLifecycle()
    val next by vm.nextPlan.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val savedRoutes by vm.savedRoutes.collectAsStateWithLifecycle()
    // Plan whose "I'm leaving" route picker is open.
    var leavingPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            permissionDenied = false
            vm.startLocation()
        } else {
            permissionDenied = true
        }
    }
    val askPermission = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) {
        if (LocationRepository.hasPermission(context)) vm.startLocation() else askPermission()
    }

    val isToday = date == now.toLocalDate()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(date.format(dateHeader), fontSize = 13.sp, color = Quiet)
                        Text(if (isToday) "Today" else "Schedule", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = { vm.shiftDay(-1) }) { Text("<", color = Ink, fontSize = 18.sp) }
                    TextButton(onClick = { vm.shiftDay(1) }) { Text(">", color = Ink, fontSize = 18.sp) }
                },
            )
        },
        floatingActionButton = {
            PrimaryButton(text = "+ Add plan", onClick = { onAddPlan(date.toString()) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { item { NoticeBox(it) } }

            if (isToday) {
                item {
                    NextUpCard(
                        plan = next,
                        route = route,
                        now = now,
                        hasLocation = location != null,
                        permissionDenied = permissionDenied,
                        onLeaving = { leavingPlanId = it.id },
                        onRoute = { onOpenRoute(it.id) },
                        onRetry = vm::refreshRoute,
                        onGrant = askPermission,
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("${plans.size} plans", color = Quiet, fontSize = 14.sp)
                }
            }

            if (plans.isEmpty()) {
                item { NoticeBox("No plans on this day. Tap + Add plan to create one.") }
            }

            items(plans, key = { it.id }) { plan ->
                val done = isToday && !plan.localEndTime().isAfter(now.toLocalTime())
                val isNext = isToday && plan.id == next?.id
                val leaveLabel = (route as? RouteState.Ready)
                    ?.takeIf { isNext && it.planId == plan.id }
                    ?.let { "Leave ${Fmt.time(leaveAt(plan, now.toLocalDate(), it.result.durationSec).toLocalTime())}" }
                PlanRow(plan, isNext, done, leaveLabel, onClick = { onOpenPlan(plan.id) })
            }
        }
    }

    leavingPlanId?.let { id ->
        val leavingPlan = plans.firstOrNull { it.id == id } ?: next
        RoutePickerDialog(
            routes = savedRoutes,
            current = leavingPlan?.savedRouteId?.ifBlank { FASTEST } ?: FASTEST,
            title = "Which route are you taking?",
            onPick = { choice ->
                leavingPlanId = null
                onLeave(id, choice)
            },
            onDismiss = { leavingPlanId = null },
        )
    }
}

/** When to walk out the door: be-there-by time minus travel time minus the buffer. */
fun leaveAt(plan: Plan, day: LocalDate, travelSec: Long): LocalDateTime =
    day.atTime(plan.localTime()).minusSeconds(travelSec).minusMinutes(plan.bufferMin.toLong())

@Composable
private fun NextUpCard(
    plan: Plan?,
    route: RouteState,
    now: LocalDateTime,
    hasLocation: Boolean,
    permissionDenied: Boolean,
    onLeaving: (Plan) -> Unit,
    onRoute: (Plan) -> Unit,
    onRetry: () -> Unit,
    onGrant: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().border(2.dp, Line).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NEXT UP", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            if (plan != null) {
                Box(Modifier.border(1.dp, Line).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(Fmt.mode(plan.travelMode), fontSize = 13.sp)
                }
            }
        }

        if (plan == null) {
            Text("Nothing else today.", fontSize = 18.sp)
            return@Column
        }

        val ready = (route as? RouteState.Ready)?.takeIf { it.planId == plan.id }
        when {
            permissionDenied && plan.needsLiveLocation -> {
                Text("Location is off, so ETA can't time your trip.", color = Quiet)
                SecondaryButton("Allow location", onGrant)
            }
            !hasLocation && plan.needsLiveLocation -> Text("Waiting for your location…", color = Quiet)
            ready != null -> {
                val secs = Duration.between(now, leaveAt(plan, now.toLocalDate(), ready.result.durationSec)).seconds
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (secs > 0) {
                        Text(Fmt.minutes(secs), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("until you should leave", color = Quiet, modifier = Modifier.padding(bottom = 6.dp))
                    } else {
                        Text("Leave now", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            route is RouteState.Failed && route.planId == plan.id -> {
                NoticeBox("Route problem: ${route.message}")
                SecondaryButton("Try again", onRetry)
            }
            else -> Text("Getting your route…", color = Quiet)
        }

        Column {
            Text(plan.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val trip = ready?.let {
                " · ${Fmt.distance(it.result.distanceM.toDouble())} · ${Fmt.minutes(it.result.durationSec)}"
            } ?: ""
            Text("${plan.destName} · be there by ${Fmt.time(plan.localTime())}$trip", color = Quiet, fontSize = 14.sp)
            val from = if (plan.usesCurrentLocation) "your location" else plan.originName
            val via = if (plan.savedRouteId.isNotBlank()) " · saved route" else ""
            Text("From $from$via", color = Quiet, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "I'm leaving",
                onClick = { onLeaving(plan) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton("View route", { onRoute(plan) })
        }
        if (ready != null) {
            TextButton(onClick = onRetry) { Text("Refresh route", color = Ink) }
        }
    }
}

@Composable
private fun PlanRow(plan: Plan, isNext: Boolean, done: Boolean, leaveLabel: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (done) 0.5f else 1f).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(72.dp).padding(top = 12.dp)) {
            Text(Fmt.time(plan.localTime()), fontSize = 14.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal)
            Text(Fmt.time(plan.localEndTime()), fontSize = 12.sp, color = Quiet)
        }
        Column(
            Modifier.weight(1f).border(if (isNext) 2.dp else 1.dp, Line).padding(12.dp),
        ) {
            Row {
                Text(plan.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (leaveLabel != null) Text(leaveLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else if (done) Text("Past", fontSize = 13.sp, color = Quiet)
            }
            Text("${plan.category} · ${plan.destName} · ${Fmt.mode(plan.travelMode)}", fontSize = 13.sp, color = Quiet)
        }
    }
}
