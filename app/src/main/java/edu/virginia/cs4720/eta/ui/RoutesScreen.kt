package edu.virginia.cs4720.eta.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import edu.virginia.cs4720.eta.data.Geo
import edu.virginia.cs4720.eta.data.SavedRoute
import edu.virginia.cs4720.eta.data.SavedRouteRepository
import edu.virginia.cs4720.eta.data.Settings
import edu.virginia.cs4720.eta.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutesViewModel(app: Application) : AndroidViewModel(app) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val settings: StateFlow<Settings> =
        SettingsRepository(app).settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val routes = MutableStateFlow<List<SavedRoute>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    init {
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                SavedRouteRepository.observe(uid)
                    .catch { error.value = it.message }
                    .collect { routes.value = it }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                SavedRouteRepository.delete(uid, id)
            } catch (e: Exception) {
                error.value = "Couldn't delete: ${e.message}"
            }
        }
    }
}

/** Your saved and recorded routes, with a map preview of the selected one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(onRecord: () -> Unit, vm: RoutesViewModel = viewModel()) {
    val routes by vm.routes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = routes.firstOrNull { it.id == selectedId } ?: routes.firstOrNull()
    val path = selected?.path.orEmpty()

    val camera = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(LatLng(38.0336, -78.5080), 14f)
    }
    LaunchedEffect(selected?.id) {
        if (path.isNotEmpty()) camera.position = CameraPosition.fromLatLngZoom(Geo.center(path), 15f)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Routes", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            ) {
                if (path.size >= 2) Polyline(points = path, color = Fill, width = 14f)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { item { NoticeBox(it) } }
                item { PrimaryButton("+ Record a new route", onRecord, Modifier.fillMaxWidth()) }
                if (routes.isEmpty()) {
                    item { NoticeBox("No saved routes yet. Record one here, or tap Save route on a plan's Route screen.") }
                }
                items(routes, key = { it.id }) { r ->
                    val on = r.id == selected?.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(if (on) 2.dp else 1.dp, Line)
                            .background(if (on) Fill else Color.White)
                            .selectable(selected = on, onClick = { selectedId = r.id }, role = Role.RadioButton)
                            .padding(12.dp),
                    ) {
                        Text(r.name, fontWeight = FontWeight.Bold, color = if (on) Color.White else Ink)
                        Text(
                            "${Fmt.distance(r.distanceM.toDouble())} · ${Fmt.minutes(r.durationSec)} · " +
                                "${Fmt.mode(r.travelMode)} · ${if (r.source == "recorded") "recorded" else "saved"}",
                            fontSize = 13.sp,
                            color = if (on) Color.White else Quiet,
                        )
                    }
                    if (on) {
                        SecondaryButton("Delete this route", { vm.delete(r.id) }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
