package edu.virginia.cs4720.eta.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import edu.virginia.cs4720.eta.data.FoundPlace
import edu.virginia.cs4720.eta.data.LocationRepository
import edu.virginia.cs4720.eta.data.PlaceFinder
import edu.virginia.cs4720.eta.data.PlaceHit
import edu.virginia.cs4720.eta.data.SavedPlace
import edu.virginia.cs4720.eta.data.Settings
import edu.virginia.cs4720.eta.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val finder = PlaceFinder(app)
    private val locations = LocationRepository(app)
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val email: String = FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    // "Add a saved place" form; kept here so rotation doesn't lose it.
    var newLabel by mutableStateOf("")
    var query by mutableStateOf("")
        private set
    var hits by mutableStateOf<List<PlaceHit>>(emptyList())
        private set
    var picked by mutableStateOf<FoundPlace?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    fun setMode(mode: String) { viewModelScope.launch { repo.setDefaultMode(mode) } }
    fun setBuffer(min: Int) { viewModelScope.launch { repo.setDefaultBuffer(min.coerceIn(0, 60)) } }
    fun setSimulate(on: Boolean) { viewModelScope.launch { repo.setSimulateGps(on) } }
    fun signOut() = FirebaseAuth.getInstance().signOut()

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.trim().length < 3) { hits = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                hits = finder.search(q, locations.lastKnown())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Place search failed: ${e.message}"
            }
        }
    }

    fun choose(hit: PlaceHit) {
        viewModelScope.launch {
            try {
                picked = finder.fetch(hit)
                query = ""
                hits = emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't load that place: ${e.message}"
            }
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            val here = locations.lastKnown()
            if (here == null) {
                message = "No location yet. Allow location for ETA (open Today once), or set the emulator's location."
            } else {
                picked = FoundPlace("Current location", "Where you were when you saved it", here)
                message = null
            }
        }
    }

    fun savePlace() {
        val p = picked
        val label = newLabel.trim()
        if (label.isBlank()) { message = "Give the place a name, like Home or Work."; return }
        if (p == null) { message = "Search for the address, or use your current location."; return }
        viewModelScope.launch {
            repo.addPlace(SavedPlace(label = label, address = p.address.ifBlank { p.name }, lat = p.location.latitude, lng = p.location.longitude))
            newLabel = ""
            picked = null
            message = "Saved $label."
        }
    }

    fun removePlace(label: String) { viewModelScope.launch { repo.removePlace(label) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignedOut: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().border(1.dp, Line).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Signed in as", fontSize = 13.sp, color = Quiet)
                    Text(vm.email, fontWeight = FontWeight.Bold)
                }
                SecondaryButton("Sign out", { vm.signOut(); onSignedOut() })
            }

            Text("Trip defaults", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("Default travel mode")
                ChoiceRow(listOf("walk" to "Walk", "bike" to "Bike", "bus" to "Bus"), s.defaultMode, vm::setMode)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("Arrive early by")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("−", { vm.setBuffer(s.defaultBuffer - 1) })
                    Text("${s.defaultBuffer} min", modifier = Modifier.width(80.dp), textAlign = TextAlign.Center, fontSize = 18.sp)
                    SecondaryButton("+", { vm.setBuffer(s.defaultBuffer + 1) })
                }
            }

            // ---- Saved places ----
            Text("Saved places", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Places you often leave from. They show up under \"Leaving from\" when you make a plan.", fontSize = 13.sp, color = Quiet)
            if (s.places.isEmpty()) {
                Text("None yet.", color = Quiet)
            }
            s.places.forEach { place ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, Line).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(place.label, fontWeight = FontWeight.Bold)
                        Text(place.address, fontSize = 13.sp, color = Quiet)
                    }
                    androidx.compose.material3.TextButton(onClick = { vm.removePlace(place.label) }) {
                        Text("Remove", color = Ink)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().border(1.dp, Line).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldLabel("Add a place")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Home", "Work", "Gym").forEach { quick ->
                        ToggleBox(quick, vm.newLabel == quick, { vm.newLabel = quick }, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = vm.newLabel, onValueChange = { vm.newLabel = it },
                    label = { Text("Name (e.g. Home)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                vm.picked?.let { p ->
                    Column(Modifier.fillMaxWidth().border(2.dp, Line).background(Tint).padding(10.dp)) {
                        Text("ADDRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Quiet, letterSpacing = 1.sp)
                        Text(p.address.ifBlank { p.name }, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = vm.query, onValueChange = vm::onQuery,
                    label = { Text("Search for the address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                vm.hits.forEach { hit ->
                    Column(Modifier.fillMaxWidth().border(1.dp, Line).clickable { vm.choose(hit) }.padding(10.dp)) {
                        Text(hit.primary, fontWeight = FontWeight.Bold)
                        Text(hit.secondary, fontSize = 13.sp, color = Quiet)
                    }
                }
                SecondaryButton("Use my current location", vm::useCurrentLocation, Modifier.fillMaxWidth())
                vm.message?.let { NoticeBox(it) }
                PrimaryButton("Save place", vm::savePlace, Modifier.fillMaxWidth())
            }

            Text("Demo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line)
                    .toggleable(value = s.simulateGps, onValueChange = vm::setSimulate, role = Role.Switch)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("Simulate walk on the Route screen")
                    Text("Moves your dot along the real route, for presenting indoors.", fontSize = 12.sp, color = Quiet)
                }
                Switch(checked = s.simulateGps, onCheckedChange = null)
            }
        }
    }
}
