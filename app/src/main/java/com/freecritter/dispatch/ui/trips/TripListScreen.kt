package com.freecritter.dispatch.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.Trip
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    repository: DispatchRepository,
    onOpenTrip: (String) -> Unit
) {
    val trips by repository.observeTrips().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Trips") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Placeholder create; real create/edit form lands in build step 2 polish.
                scope.launch {
                    repository.saveTrip(
                        Trip(
                            name = "New trip",
                            city = "",
                            startDate = LocalDate.now().toString(),
                            endDate = LocalDate.now().plusDays(2).toString()
                        )
                    )
                }
            }) { Text("+") }
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No trips yet.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap + to create your first trip.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(trips, key = { it.id }) { trip ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onOpenTrip(trip.id) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(trip.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(trip.city, "${trip.startDate} → ${trip.endDate}")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
