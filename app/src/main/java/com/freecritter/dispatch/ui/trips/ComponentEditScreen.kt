package com.freecritter.dispatch.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.ComponentType
import com.freecritter.dispatch.data.db.ReceiptLocation
import com.freecritter.dispatch.data.db.TripComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Create/edit form for a TripComponent. componentId == null => create.
 * Zoned start/end times join in a later gate (needs the timezone-aware
 * picker treatment); title/type/refs/receipt state are the 0.2 core.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentEditScreen(
    repository: DispatchRepository,
    tripId: String,
    componentId: String?,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<TripComponent?>(null) }
    var type by remember { mutableStateOf(ComponentType.FLIGHT) }
    var title by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var confirmationRef by remember { mutableStateOf("") }
    var receiptLocation by remember { mutableStateOf(ReceiptLocation.NONE) }
    var notes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    LaunchedEffect(componentId) {
        if (componentId != null) {
            repository.observeComponents(tripId).firstOrNull()
                ?.firstOrNull { it.id == componentId }
                ?.let { c ->
                    existing = c
                    type = c.type
                    title = c.title
                    address = c.address
                    confirmationRef = c.confirmationRef
                    receiptLocation = c.receiptLocation
                    notes = c.notes
                }
        }
    }

    fun save() {
        if (title.isBlank()) { errorText = "Title is required"; return }
        val component = (existing ?: TripComponent(tripId = tripId, type = type, title = title.trim()))
            .copy(
                type = type,
                title = title.trim(),
                address = address.trim(),
                confirmationRef = confirmationRef.trim(),
                receiptLocation = receiptLocation,
                notes = notes.trim(),
            )
        scope.launch {
            repository.saveComponent(component)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (componentId == null) "New item" else "Edit item") },
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
            EnumDropdown(
                label = "Type",
                options = ComponentType.entries,
                selected = type,
                display = { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) },
                onSelected = { type = it },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                title, { title = it },
                label = { Text(titleLabelFor(type)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(address, { address = it }, label = { Text("Address / location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(confirmationRef, { confirmationRef = it }, label = { Text("Confirmation #") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            EnumDropdown(
                label = "Receipt location",
                options = ReceiptLocation.entries,
                selected = receiptLocation,
                display = { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) },
                onSelected = { receiptLocation = it },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            if (errorText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (componentId == null) "Add item" else "Save changes")
            }
        }
    }
}

private fun titleLabelFor(type: ComponentType) = when (type) {
    ComponentType.FLIGHT -> "Flight (e.g. UA 1234) *"
    ComponentType.LODGING -> "Property name *"
    ComponentType.EVENT_TICKETS -> "Event / ticket name *"
    ComponentType.GROUND -> "Ground transport *"
    ComponentType.OTHER -> "Title *"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}