package com.freecritter.dispatch.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.Trip
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Create/edit form for a Trip. tripId == null => create.
 * Dates are ISO yyyy-MM-dd text for 0.2; Material date pickers are a later polish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    repository: DispatchRepository,
    tripId: String?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Trip?>(null) }
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(2).toString()) }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    // Load once for edit mode.
    LaunchedEffect(tripId) {
        if (tripId != null) {
            repository.observeTrip(tripId).firstOrNull()?.let { t ->
                existing = t
                name = t.name
                purpose = t.purpose
                city = t.city
                venue = t.venue
                startDate = t.startDate
                endDate = t.endDate
                notes = t.notes
            }
        }
    }

    fun validateAndSave() {
        val start = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull()
        val end = runCatching { LocalDate.parse(endDate.trim()) }.getOrNull()
        errorText = when {
            name.isBlank() -> "Name is required"
            start == null -> "Start date must be yyyy-mm-dd"
            end == null -> "End date must be yyyy-mm-dd"
            end.isBefore(start) -> "End date is before start date"
            else -> ""
        }
        if (errorText.isNotEmpty()) return

        val trip = (existing ?: Trip(name = name.trim(), startDate = startDate.trim(), endDate = endDate.trim()))
            .copy(
                name = name.trim(),
                purpose = purpose.trim(),
                city = city.trim(),
                venue = venue.trim(),
                startDate = startDate.trim(),
                endDate = endDate.trim(),
                notes = notes.trim(),
            )
        scope.launch {
            repository.saveTrip(trip)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tripId == null) "New trip" else "Edit trip") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Trip name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(venue, { venue = it }, label = { Text("Venue / event") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    startDate, { startDate = it },
                    label = { Text("Start (yyyy-mm-dd)") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    endDate, { endDate = it },
                    label = { Text("End (yyyy-mm-dd)") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            if (errorText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { validateAndSave() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (tripId == null) "Create trip" else "Save changes")
            }
        }
    }
}
