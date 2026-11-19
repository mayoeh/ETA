package edu.virginia.cs4720.eta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// Left to right: Calendar, Routes, Today (center), Friends, Settings.
private val tabs = listOf(
    Tab("calendar", "Calendar", Icons.Filled.DateRange),
    Tab("routes", "Routes", Icons.Filled.Place),
    Tab("today", "Today", Icons.Filled.Home),
    Tab("friends", "Friends", Icons.Filled.Face),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

/** Grayscale bottom bar; the selected tab is filled dark grey, Today sits in the middle. */
@Composable
fun EtaBottomBar(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            tabs.forEach { tab ->
                val selected = tab.route == current
                val isCenter = tab.route == "today"
                Column(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .background(if (selected) Fill else Color.White)
                        .selectable(selected = selected, onClick = { onSelect(tab.route) }, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,   // the label below names it
                        tint = if (selected) Color.White else Ink,
                        modifier = Modifier.size(if (isCenter) 30.dp else 24.dp),
                    )
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else Ink,
                        fontWeight = if (selected || isCenter) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
