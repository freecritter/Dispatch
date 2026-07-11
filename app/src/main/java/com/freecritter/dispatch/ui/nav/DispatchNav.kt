package com.freecritter.dispatch.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.nostr.KeyManager
import com.freecritter.dispatch.ui.onboarding.OnboardingScreen
import com.freecritter.dispatch.ui.settings.SettingsScreen
import com.freecritter.dispatch.ui.trips.TripDetailScreen
import com.freecritter.dispatch.ui.trips.TripEditScreen
import com.freecritter.dispatch.ui.trips.TripListScreen
import com.freecritter.dispatch.ui.trips.ComponentEditScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val TRIPS = "trips"
    const val TRIP_DETAIL = "trip/{tripId}"
    const val TRIP_EDIT = "tripEdit?tripId={tripId}"
    const val SETTINGS = "settings"
    fun tripDetail(id: String) = "trip/$id"
    fun tripEdit(id: String? = null) = if (id == null) "tripEdit" else "tripEdit?tripId=$id"
    const val COMPONENT_EDIT = "componentEdit/{tripId}?componentId={componentId}"
    fun componentEdit(tripId: String, componentId: String? = null) =
        if (componentId == null) "componentEdit/$tripId" else "componentEdit/$tripId?componentId=$componentId"
}

@Composable
fun DispatchNav(
    repository: DispatchRepository,
    keyManager: KeyManager,
) {
    val nav = rememberNavController()
    val start = if (keyManager.hasIdentity()) Routes.TRIPS else Routes.ONBOARDING

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                keyManager = keyManager,
                onIdentityReady = {
                    nav.navigate(Routes.TRIPS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.TRIPS) {
            TripListScreen(
                repository = repository,
                onOpenTrip = { id -> nav.navigate(Routes.tripDetail(id)) },
                onCreateTrip = { nav.navigate(Routes.tripEdit()) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.TRIP_DETAIL,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
            TripDetailScreen(
                repository = repository,
                tripId = tripId,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.tripEdit(tripId)) },
                onAddComponent = { nav.navigate(Routes.componentEdit(tripId)) },
                onEditComponent = { cid -> nav.navigate(Routes.componentEdit(tripId, cid)) },
            )
        }
        composable(
            Routes.TRIP_EDIT,
            arguments = listOf(
                navArgument("tripId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            TripEditScreen(
                repository = repository,
                tripId = backStackEntry.arguments?.getString("tripId"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            Routes.COMPONENT_EDIT,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("componentId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            ComponentEditScreen(
                repository = repository,
                tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable,
                componentId = backStackEntry.arguments?.getString("componentId"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                keyManager = keyManager,
                onIdentityReset = {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}