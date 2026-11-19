package edu.virginia.cs4720.eta.ui

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import edu.virginia.cs4720.eta.data.Geo
import edu.virginia.cs4720.eta.data.LocationRepository
import edu.virginia.cs4720.eta.data.SavedRoute
import edu.virginia.cs4720.eta.data.SavedRouteRepository
import edu.virginia.cs4720.eta.data.Settings
import edu.virginia.cs4720.eta.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** Records a walk from GPS fixes and saves it as a route plans can use. */
class RecordRouteViewModel(app: Application) : AndroidViewModel(app) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val locations = LocationRepository(app)

    val settings: StateFlow<Settings> =
        SettingsRepository(app).settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val phase = MutableStateFlow(PHASE_IDLE)
    val points = MutableStateFlow<List<LatLng>>(emptyList())
    val position = MutableStateFlow<LatLng?>(null)
    val elapsedSec = MutableStateFlow(0L)
    val distanceM = MutableStateFlow(0.0)
    val message = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)

    private var locationStarted = false

    init {
        // Recording clock: only counts while recording, not while paused.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (phase.value == PHASE_RECORDING) elapsedSec.value += 1
            }
        }
    }

    fun startLocation() {
        if (locationStarted) return
        locationStarted = true
        viewModelScope.launch {
            locations.updates(intervalMs = 2_000)
                .catch { message.value = it.message }
                .collect { ll ->
                    position.value = ll
                    if (phase.value == PHASE_RECORDING) addPoint(ll)
                }
        }
    }

    /** Keeps a point only after moving at least 5 m, so GPS jitter doesn't add fake distance. */
    private fun addPoint(ll: LatLng) {
        val last = points.value.lastOrNull()
        if (last == null) {
            points.value = listOf(ll)
            return
        }
        val d = Geo.distanceM(last, ll)
        if (d >= MIN_STEP_M) {
            distanceM.value += d
            points.value = points.value + ll
        }
    }

    fun start() {
        points.value = emptyList()
        distanceM.value = 0.0
        elapsedSec.value = 0
        message.value = null
        phase.value = PHASE_RECORDING
        position.value?.let { addPoint(it) }
    }

    fun togglePause() {
        phase.value = if (phase.value == PHASE_PAUSED) PHASE_RECORDING else PHASE_PAUSED
    }

    fun stop() {
        phase.value = PHASE_DONE
    }

    fun discard() {
        points.value = emptyList()
        distanceM.value = 0.0
        elapsedSec.value = 0
        phase.value = PHASE_IDLE
    }

    fun save(name: String, mode: String) {
        val path = points.value
        if (path.size < 2) {
            message.value = "Walk a little farther first: the route needs at least two points."
            return
        }
        viewModelScope.launch {
            try {
                SavedRouteRepository.save(
                    uid,
                    SavedRoute(
                        name = name.ifBlank { "Recorded route" },
                        travelMode = mode,
                        distanceM = distanceM.value.toInt(),
                        durationSec = elapsedSec.value,
                        polyline = Geo.encodePolyline(path),
                        source = "recorded",
                    ),
                )
                saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "Couldn't save: ${e.message}"
            }
        }
    }

    companion object {
        const val PHASE_IDLE = "idle"
        const val PHASE_RECORDING = "recording"
        const val PHASE_PAUSED = "paused"
        const val PHASE_DONE = "done"
        private const val MIN_STEP_M = 5.0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordRouteScreen(onDone: () -> Unit, vm: RecordRouteViewModel = viewModel()) {
    val context = LocalContext.current
    val phase by vm.phase.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val here by vm.position.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSec.collectAsStateWithLifecycle()
    val distance by vm.distanceM.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("walk") }

    LaunchedEffect(saved) { if (saved) onDone() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) vm.startLocation() }
    LaunchedEffect(Unit) {
        if (LocationRepository.hasPermission(context)) vm.startLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val camera = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(LatLng(38.0336, -78.5080), 15f)
    }
    // Keep the map centered on you while recording.
    LaunchedEffect(here) {
        here?.let { camera.position = CameraPosition.fromLatLngZoom(it, 17f) }
    }

    val badge = when (phase) {
        RecordRouteViewModel.PHASE_RECORDING -> "REC"
        RecordRouteViewModel.PHASE_PAUSED -> "PAUSED"
        RecordRouteViewModel.PHASE_DONE -> "DONE"
        else -> "READY"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record a route", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onDone) { Text("<", color = Ink, fontSize = 18.sp) } },
                actions = {
                    Box(Modifier.padding(end = 12.dp).border(1.dp, Line).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(badge, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().weight(1f),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                if (points.size >= 2) Polyline(points = points, color = Fill, width = 14f)
                here?.let { Circle(center = it, radius = 6.0, fillColor = Ink, strokeColor = Color.White, strokeWidth = 4f) }
            }
            HorizontalDivider(color = Line)

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { NoticeBox(it) }

                Row(Modifier.fillMaxWidth().border(1.dp, Line)) {
                    Stat("Time", formatClock(elapsed), Modifier.weight(1f))
                    Stat("Distance", Fmt.distance(distance), Modifier.weight(1f))
                    Stat("Pace", pace(distance, elapsed), Modifier.weight(1f))
                }

                when (phase) {
                    RecordRouteViewModel.PHASE_IDLE -> {
                        Text(if (here == null) "Waiting for your location…" else "Walk your route, then tap Stop.", color = Quiet)
                        PrimaryButton("Start recording", vm::start, Modifier.fillMaxWidth(), enabled = here != null)
                    }
                    RecordRouteViewModel.PHASE_RECORDING, RecordRouteViewModel.PHASE_PAUSED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(if (phase == RecordRouteViewModel.PHASE_PAUSED) "Resume" else "Pause", vm::togglePause, Modifier.weight(1f))
                            PrimaryButton("Stop", vm::stop, Modifier.weight(1f))
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Route name (e.g. Shortcut)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ChoiceRow(listOf("walk" to "Walk", "bike" to "Bike", "bus" to "Bus"), mode, { mode = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Discard", vm::discard, Modifier.weight(1f))
                            PrimaryButton("Save route", { vm.save(name.trim(), mode) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(10.dp)) {
        Text(label, fontSize = 13.sp, color = Quiet)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatClock(sec: Long): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

/** Minutes per mile, from GPS distance and recording time. */
private fun pace(meters: Double, sec: Long): String {
    val unitM = 1609.344
    if (meters < 20 || sec <= 0) return "—"
    val secPerUnit = sec / (meters / unitM)
    val m = (secPerUnit / 60).toInt()
    val s = (secPerUnit % 60).toInt()
    return String.format(Locale.US, "%d:%02d /mi", m, s)
}
