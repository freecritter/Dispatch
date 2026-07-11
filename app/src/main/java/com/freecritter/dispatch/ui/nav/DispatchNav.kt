package com.freecritter.dispatch.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.ui.onboarding.OnboardingScreen
import com.freecritter.dispatch.ui.trips.TripDetailScreen
import com.freecritter.dispatch.ui.trips.TripListScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val TRIPS = "trips"
    const val TRIP_DETAIL = "trip/{tripId}"
    fun tripDetail(id: String) = "trip/$id"
}

@Composable
fun DispatchNav(repository: DispatchRepository) {
    val nav = rememberNavController()
    // TODO: start destination becomes TRIPS once an identity exists (Keystore/Amber check).
    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onIdentityReady = {
                nav.navigate(Routes.TRIPS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.TRIPS) {
            TripListScreen(
                repository = repository,
                onOpenTrip = { id -> nav.navigate(Routes.tripDetail(id)) }
            )
        }
        composable(
            Routes.TRIP_DETAIL,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
            TripDetailScreen(repository = repository, tripId = tripId, onBack = { nav.popBackStack() })
        }
    }
}
