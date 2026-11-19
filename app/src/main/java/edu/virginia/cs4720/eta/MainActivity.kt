package edu.virginia.cs4720.eta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.libraries.places.api.Places
import com.google.firebase.auth.FirebaseAuth
import edu.virginia.cs4720.eta.ui.CalendarScreen
import edu.virginia.cs4720.eta.ui.EditPlanScreen
import edu.virginia.cs4720.eta.ui.EtaBottomBar
import edu.virginia.cs4720.eta.ui.EtaTheme
import edu.virginia.cs4720.eta.ui.FriendsScreen
import edu.virginia.cs4720.eta.ui.LoginScreen
import edu.virginia.cs4720.eta.ui.RecordRouteScreen
import edu.virginia.cs4720.eta.ui.RouteScreen
import edu.virginia.cs4720.eta.ui.RoutesScreen
import edu.virginia.cs4720.eta.ui.SettingsScreen
import edu.virginia.cs4720.eta.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Places SDK (destination search) uses the same key as Maps and Routes.
        if (!Places.isInitialized() && BuildConfig.MAPS_API_KEY.isNotBlank()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        setContent {
            EtaTheme { EtaNavHost() }
        }
    }
}

/** Screens reachable from the bottom bar. Everything else (edit, follow, record) hides the bar. */
val TAB_ROUTES = listOf("calendar", "routes", "today", "friends", "settings")

/** Switches tabs without piling up copies of screens on the back stack. */
fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun EtaNavHost() {
    val nav = rememberNavController()
    // Decided once, so a recomposition never rebuilds the nav graph.
    val start = remember { if (FirebaseAuth.getInstance().currentUser != null) "today" else "login" }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (current in TAB_ROUTES) {
                EtaBottomBar(current = current.orEmpty(), onSelect = { nav.goToTab(it) })
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable("login") {
                LoginScreen(onSignedIn = {
                    nav.navigate("today") { popUpTo("login") { inclusive = true } }
                })
            }
            composable("today") {
                TodayScreen(
                    onAddPlan = { date -> nav.navigate("edit/new?date=$date") },
                    onOpenPlan = { id -> nav.navigate("edit/$id") },
                    onOpenRoute = { id -> nav.navigate("route/$id") },
                    onLeave = { id, routeChoice -> nav.navigate("route/$id?routeId=$routeChoice&nav=true") },
                )
            }
            composable("calendar") {
                CalendarScreen(
                    onAddPlan = { date -> nav.navigate("edit/new?date=$date") },
                    onOpenPlan = { id -> nav.navigate("edit/$id") },
                )
            }
            composable("routes") {
                RoutesScreen(onRecord = { nav.navigate("record") })
            }
            composable("friends") {
                FriendsScreen()
            }
            composable("settings") {
                SettingsScreen(
                    onSignedOut = {
                        nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                    },
                )
            }
            composable(
                route = "edit/{planId}?date={date}",
                arguments = listOf(
                    navArgument("planId") { type = NavType.StringType },
                    navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                EditPlanScreen(onDone = { nav.popBackStack() }, onRecordRoute = { nav.navigate("record") })
            }
            composable(
                route = "route/{planId}?routeId={routeId}&nav={nav}",
                arguments = listOf(
                    navArgument("planId") { type = NavType.StringType },
                    navArgument("routeId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("nav") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                RouteScreen(onBack = { nav.popBackStack() }, onRecord = { nav.navigate("record") })
            }
            composable("record") {
                RecordRouteScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}
