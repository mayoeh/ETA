package edu.virginia.cs4720.eta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Placeholder until the friends feature is built (planned before Demo Day). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Friends", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoticeBox("Friends is coming next.")
            Text("Planned for this screen:", fontWeight = FontWeight.Bold)
            Text("Search for friends by name or email and send requests", fontSize = 15.sp, color = Quiet)
            Text("See upcoming plans you share", fontSize = 15.sp, color = Quiet)
            Text("See the plans each friend marks as shared", fontSize = 15.sp, color = Quiet)
        }
    }
}
