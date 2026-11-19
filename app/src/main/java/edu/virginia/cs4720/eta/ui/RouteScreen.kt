package edu.virginia.cs4720.eta.ui

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import edu.virginia.cs4720.eta.data.Geo
import edu.virginia.cs4720.eta.data.LocationRepository
import edu.virginia.cs4720.eta.data.Plan
import edu.virginia.cs4720.eta.data.PlanRepository
import edu.virginia.cs4720.eta.data.RouteResult
import edu.virginia.cs4720.eta.data.RouteStep
import edu.virginia.cs4720.eta.data.RoutesApi
import edu.virginia.cs4720.eta.data.Settings
import edu.virginia.cs4720.eta.data.SettingsRepository
import edu.virginia.cs4720.eta.data.SavedRoute
import edu.virginia.cs4720.eta.data.SavedRouteRepository
import edu.virginia.cs4720.eta.data.resolveTrip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

class RouteViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val planId: String = saved.get<String>("planId").orEmpty()
    // From "I'm leaving": which route (FASTEST or a saved route id; null = the plan's own choice),
    // and whether to open straight into turn-by-turn.
    private val navArg: Boolean = saved.get<Boolean>("nav") ?: false
    val choice = MutableStateFlow<String?>(saved.get<String>("routeId"))
    val savedRoutes = MutableStateFlow<List<SavedRoute>>(emptyList())
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val routes = RoutesApi(app)
    private val locations = LocationRepository(app)

    val settings: StateFlow<Settings> =
        SettingsRepository(app).settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val plan = MutableStateFlow<Plan?>(null)
    val route = MutableStateFlow<RouteResult?>(null)
    val position = MutableStateFlow<LatLng?>(null)
    val simulating = MutableStateFlow(false)
    val navigating = MutableStateFlow(navArg)   // Google Maps-style follow view with directions
    val error = MutableStateFlow<String?>(null)

    private var locationStarted = false
    private var routing = false
    private var lastReal: LatLng? = null
    private var simJob: Job? = null

    init {
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                SavedRouteRepository.observe(uid)
                    .catch { error.value = it.message }
                    .collect { savedRoutes.value = it }
            }
        }
        viewModelScope.launch {
            try {
                plan.value = PlanRepository.get(uid, planId) ?: throw IllegalStateException("Plan not found.")
                routeIfReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    fun startLocation() {
        if (locationStarted) return
        locationStarted = true
        viewModelScope.launch {
            locations.updates()
                .catch { error.value = it.message }
                .collect { ll ->
                    lastReal = ll
                    if (!simulating.value) position.value = ll
                    routeIfReady()
                }
        }
    }

    /**
     * The route is worked out once (saved route, the plan's leaving-from place, or where you are now),
     * so progress is measured along that path.
     */
    private suspend fun routeIfReady() {
        if (route.value != null || routing) return
        val p = plan.value ?: return
        val c = choice.value
        val needsLocation = when (c) {
            null -> p.needsLiveLocation
            FASTEST -> p.usesCurrentLocation
            else -> false
        }
        if (needsLocation && position.value == null) return
        loadRoute { tripFor(p, c) }
    }

    /** The route for a choice: the plan's own, the fastest from the Routes API, or a saved route. */
    private suspend fun tripFor(p: Plan, c: String?): RouteResult? = when (c) {
        null -> resolveTrip(uid, p, position.value, routes)
        FASTEST -> {
            val origin = if (p.usesCurrentLocation) position.value else p.origin
            origin?.let { routes.compute(it, p.dest, p.travelMode) }
        }
        else -> SavedRouteRepository.get(uid, c)?.toRouteResult() ?: resolveTrip(uid, p, position.value, routes)
    }

    /** Switch to another route from the picker. */
    fun chooseRoute(c: String) {
        choice.value = c
        route.value = null
        viewModelScope.launch { routeIfReady() }
    }

    private suspend fun loadRoute(fetch: suspend () -> RouteResult?) {
        routing = true
        try {
            fetch()?.let { route.value = it }
            error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            routing = false
        }
    }

    /** Ask the Routes API again from where you are right now. */
    fun reroute() {
        val p = plan.value ?: return
        val origin = position.value ?: return
        viewModelScope.launch { loadRoute { routes.compute(origin, p.dest, p.travelMode) } }
    }

    val saveMessage = MutableStateFlow<String?>(null)

    /** Saves the route on screen to users/{uid}/savedRoutes so plans can use it later. */
    fun saveRoute(name: String) {
        val p = plan.value ?: return
        val r = route.value ?: return
        viewModelScope.launch {
            try {
                SavedRouteRepository.save(
                    uid,
                    SavedRoute(
                        name = name.ifBlank { "Route to ${p.destName}" },
                        travelMode = p.travelMode,
                        distanceM = r.distanceM,
                        durationSec = r.durationSec,
                        polyline = Geo.encodePolyline(r.path),
                        startName = if (p.usesCurrentLocation) "Start point" else p.originName,
                        endName = p.destName,
                        source = "routes",
                    ),
                )
                saveMessage.value = "Saved \"${name.ifBlank { "Route to ${p.destName}" }}\". Pick it in Edit plan → Route."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveMessage.value = "Couldn't save: ${e.message}"
            }
        }
    }

    /** Start / exit the turn-by-turn view. */
    fun toggleNavigation() {
        navigating.value = !navigating.value
    }

    /**
     * Demo helper: moves your position along the real route from the Routes API
     * (about 7x walking speed), for presenting when you can't physically walk.
     * Starts the turn-by-turn view so it looks like walking with directions.
     */
    fun toggleSimulation() {
        if (simulating.value) {
            simJob?.cancel()
            simulating.value = false
            lastReal?.let { position.value = it }
            return
        }
        val path = route.value?.path ?: return
        if (path.size < 2) return
        simulating.value = true
        navigating.value = true
        simJob = viewModelScope.launch {
            val total = Geo.pathLength(path)
            var d = 0.0
            while (d < total) {
                position.value = Geo.pointAt(path, d)
                d += SIM_STEP_M
                delay(SIM_TICK_MS)
            }
            position.value = path.last()
            simulating.value = false
        }
    }

    companion object {
        private const val SIM_STEP_M = 2.5      // 2.5 m every 0.25 s = 10 m/s, about 7x walking pace
        private const val SIM_TICK_MS = 250L
    }
}


/** What the direction banner shows right now. */
private data class Direction(val distanceToTurnM: Double, val instruction: String, val then: String?, val arrived: Boolean)

/**
 * Picks the upcoming instruction from the Routes API steps, the way Google Maps does:
 * while you're on step i, the banner shows step i+1's maneuver and the distance left to it.
 */
private fun directionFor(
    steps: List<RouteStep>,
    stepEnds: List<Double>,
    walkedM: Double,
    remainingM: Double,
    destName: String,
): Direction {
    if (remainingM < ARRIVED_M) return Direction(0.0, "You've arrived at $destName", null, arrived = true)
    if (steps.isEmpty()) return Direction(remainingM, "Follow the route to $destName", null, arrived = false)
    val cur = stepEnds.indexOfFirst { it > walkedM }.let { if (it < 0) steps.lastIndex else it }
    // Just starting out: show the first step itself ("Head north on ...").
    if (cur == 0 && walkedM < 15) {
        return Direction(stepEnds[0] - walkedM, steps[0].instruction.ifBlank { "Start walking" }, steps.getOrNull(1)?.instruction, false)
    }
    val next = steps.getOrNull(cur + 1)
    return if (next == null) {
        Direction(remainingM, "Arrive at $destName", null, false)
    } else {
        Direction(stepEnds[cur] - walkedM, next.instruction.ifBlank { "Continue" }, steps.getOrNull(cur + 2)?.instruction, false)
    }
}

private const val ARRIVED_M = 12.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(onBack: () -> Unit, onRecord: () -> Unit, vm: RouteViewModel = viewModel()) {
    val context = LocalContext.current
    val plan by vm.plan.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val userPosition by vm.position.collectAsStateWithLifecycle()
    val simulating by vm.simulating.collectAsStateWithLifecycle()
    val navigating by vm.navigating.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saveMessage by vm.saveMessage.collectAsStateWithLifecycle()
    val choice by vm.choice.collectAsStateWithLifecycle()
    val savedRoutes by vm.savedRoutes.collectAsStateWithLifecycle()
    var showSave by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var routeName by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) vm.startLocation() }
    LaunchedEffect(Unit) {
        if (LocationRepository.hasPermission(context)) vm.startLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val currentRoute = route
    val currentPos = userPosition
    val path = currentRoute?.path.orEmpty()
    val progress = remember(currentRoute, currentPos) {
        if (currentRoute != null && currentPos != null && path.size >= 2) Geo.progressAlong(path, currentPos) else null
    }
    // How far along the path each turn-by-turn step ends (worked out once per route).
    val stepEnds = remember(currentRoute) {
        currentRoute?.steps?.map { st -> st.end?.let { Geo.progressAlong(path, it).walkedM } ?: Double.MAX_VALUE }.orEmpty()
    }
    val direction = if (progress != null && currentRoute != null) {
        directionFor(currentRoute.steps, stepEnds, progress.walkedM, progress.remainingM, plan?.destName.orEmpty())
    } else null
    val (walkedPart, aheadPart) = remember(currentRoute, progress?.walkedM) {
        if (progress != null && path.size >= 2) Geo.splitAt(path, progress.walkedM) else (emptyList<LatLng>() to path)
    }
    val remainingSec = if (currentRoute != null && progress != null && progress.totalM > 0) {
        (currentRoute.durationSec * progress.remainingM / progress.totalM).toLong()
    } else null

    val camera = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(LatLng(38.0336, -78.5080), 14f)
    }
    // Overview: fit the whole route. Navigating: zoom in behind you, pointing the way you're walking.
    LaunchedEffect(currentRoute, navigating) {
        if (!navigating && path.isNotEmpty()) {
            runCatching {
                camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition(Geo.center(path), 15.5f, 0f, 0f)), 600)
            }
        }
    }
    LaunchedEffect(currentPos, navigating) {
        if (navigating && currentPos != null) {
            val walked = progress?.walkedM ?: 0.0
            val ahead = if (path.size >= 2) Geo.pointAt(path, walked + 25.0) else currentPos
            val bearing = if (ahead != currentPos) Geo.bearing(currentPos, ahead) else camera.position.bearing
            runCatching {
                camera.animate(
                    CameraUpdateFactory.newCameraPosition(CameraPosition(currentPos, 18.5f, 55f, bearing)),
                    if (simulating) 240 else 800,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (navigating) "Navigating" else "Follow a route", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("<", color = Ink, fontSize = 18.sp) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true),
                ) {
                    if (walkedPart.size >= 2) Polyline(points = walkedPart, color = Color(0xFFBBBBBB), width = 16f)
                    if (aheadPart.size >= 2) Polyline(points = aheadPart, color = Fill, width = 16f)
                    plan?.let { Marker(state = MarkerState(position = it.dest), title = it.destName) }
                    currentPos?.let {
                        Circle(center = it, radius = 9.0, fillColor = Color(0x33333333), strokeColor = Color.Transparent, strokeWidth = 0f)
                        Circle(center = it, radius = 4.0, fillColor = Ink, strokeColor = Color.White, strokeWidth = 5f)
                    }
                }
                // Direction banner over the map while navigating.
                if (navigating && direction != null) {
                    Column(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)
                            .background(Fill).border(1.dp, Line).padding(14.dp),
                    ) {
                        if (!direction.arrived) {
                            Text(Fmt.distance(direction.distanceToTurnM), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(direction.instruction, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        direction.then?.takeIf { it.isNotBlank() }?.let {
                            Text("Then: $it", color = Color(0xFFDDDDDD), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = Line)

            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { NoticeBox(it) }

                plan?.let { p ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("To ${p.destName}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${p.title} · be there by ${Fmt.time(p.localTime())}", color = Quiet, fontSize = 14.sp)
                        }
                        if (remainingSec != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.minutes(remainingSec), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text("arrive ${Fmt.time(LocalTime.now().plusSeconds(remainingSec))}", color = Quiet, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (progress == null) {
                    Text(if (currentPos == null) "Waiting for your location…" else "Getting your route…", color = Quiet)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("${Fmt.distance(progress.walkedM)} walked", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${Fmt.distance(progress.remainingM)} left of ${Fmt.distance(progress.totalM)}")
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = Fill,
                        trackColor = Tint,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (navigating) "Exit" else "Start",
                        onClick = vm::toggleNavigation,
                        enabled = currentRoute != null,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = "Share ETA",
                        enabled = plan != null && remainingSec != null,
                        onClick = {
                            val p = plan
                            if (p != null && remainingSec != null) {
                                shareText(context, "On my way to ${p.destName}. ETA ${Fmt.time(LocalTime.now().plusSeconds(remainingSec))}.")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (settings.simulateGps) {
                    SecondaryButton(
                        text = if (simulating) "Stop simulated walk" else "Simulate walk (demo)",
                        onClick = vm::toggleSimulation,
                        enabled = currentRoute != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!navigating) {
                    val routeLabel = when {
                        choice != null -> routeChoiceLabel(choice, savedRoutes)
                        plan?.savedRouteId?.isNotBlank() == true -> routeChoiceLabel(plan?.savedRouteId, savedRoutes)
                        else -> "Fastest route"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Route: $routeLabel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        SecondaryButton("Change route", { showPicker = true })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Reroute", vm::reroute, Modifier.weight(1f), enabled = currentPos != null)
                        SecondaryButton("Save route", { showSave = true }, Modifier.weight(1f), enabled = currentRoute != null)
                    }
                    SecondaryButton("Record a route", onRecord, Modifier.fillMaxWidth())
                    saveMessage?.let { NoticeBox(it) }
                }
            }
        }
    }

    if (showPicker) {
        RoutePickerDialog(
            routes = savedRoutes,
            current = choice ?: plan?.savedRouteId?.ifBlank { FASTEST } ?: FASTEST,
            title = "Which route?",
            onPick = { c ->
                showPicker = false
                vm.chooseRoute(c)
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save this route") },
            text = {
                OutlinedTextField(
                    value = routeName, onValueChange = { routeName = it },
                    label = { Text("Route name") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveRoute(routeName.trim())
                    routeName = ""
                    showSave = false
                }) { Text("Save", color = Ink) }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel", color = Ink) } },
            shape = RectangleShape,
        )
    }
}
