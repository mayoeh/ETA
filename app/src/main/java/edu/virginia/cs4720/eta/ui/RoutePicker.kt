package edu.virginia.cs4720.eta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.virginia.cs4720.eta.data.SavedRoute

/** Route choice value meaning "ask the Routes API for the fastest route". */
const val FASTEST = "fastest"

/** Label for a route choice: "Fastest route" or the saved route's name. */
fun routeChoiceLabel(choice: String?, routes: List<SavedRoute>): String =
    if (choice == null || choice == FASTEST) "Fastest route" else routes.firstOrNull { it.id == choice }?.name ?: "Saved route"

/**
 * "Which route?" list: the fastest route from the Routes API, or one of your saved routes.
 * [onPick] gets [FASTEST] or the saved route's id.
 */
@Composable
fun RoutePickerDialog(
    routes: List<SavedRoute>,
    current: String,
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickRow("Fastest route", "Google picks the best way with turn-by-turn directions", current == FASTEST) {
                    onPick(FASTEST)
                }
                routes.forEach { r ->
                    PickRow(
                        r.name,
                        "${Fmt.distance(r.distanceM.toDouble())} · ${Fmt.minutes(r.durationSec)} · " +
                            (if (r.source == "recorded") "recorded" else "saved"),
                        current == r.id,
                    ) { onPick(r.id) }
                }
                if (routes.isEmpty()) {
                    Text("No saved routes yet. Record or save one to see it here.", fontSize = 13.sp, color = Quiet)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
        shape = RectangleShape,
        containerColor = Color.White,
    )
}

@Composable
private fun PickRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
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
