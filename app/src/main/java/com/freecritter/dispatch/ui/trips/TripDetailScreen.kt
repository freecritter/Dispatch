package com.freecritter.dispatch.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.ComponentType
import com.freecritter.dispatch.data.db.TripComponent
import kotlinx.coroutines.launch

/**
 * Trip dashboard v0: components with booked/receipt toggles + unbooked/missing counts.
 * Readiness numbers here are exactly what the notification digest will reuse (spec §7.11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    repository: DispatchRepository,
    tripId: String,
    onBack: () -> Unit
) {
    val trip by repository.observeTrip(tripId).collectAsState(initial = null)
    val components by repository.observeComponents(tripId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val unbooked = components.count { !it.booked }
    val missingReceipts = components.count { it.booked && !it.receiptObtained }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "Trip") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    repository.saveComponent(
                        TripComponent(tripId = tripId, type = ComponentType.OTHER, title = "New item")
                    )
                }
            }) { Text("+") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Readiness", style = MaterialTheme.typography.titleMedium)
                    Text("$unbooked unbooked · $missingReceipts receipts missing")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(components, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${c.type.name} — ${c.title}", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c.booked, onCheckedChange = { checked ->
                                    scope.launch { repository.saveComponent(c.copy(booked = checked)) }
                                })
                                Text("Booked")
                                Spacer(Modifier.width(16.dp))
                                Checkbox(checked = c.receiptObtained, onCheckedChange = { checked ->
                                    scope.launch { repository.saveComponent(c.copy(receiptObtained = checked)) }
                                })
                                Text("Receipt")
                            }
                        }
                    }
                }
            }
        }
    }
}
