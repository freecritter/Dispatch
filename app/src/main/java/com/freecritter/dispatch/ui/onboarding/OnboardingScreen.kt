package com.freecritter.dispatch.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.nostr.KeyManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.nostr.RestoreService
import kotlinx.coroutines.launch

/**
 * Identity onboarding (spec §7.1). Privacy by default: "Create new identity"
 * generates a fresh, never-published keypair. Import exists for deliberate
 * key reuse. Amber (NIP-55) ships in an 0.x release.
 */
@Composable
fun OnboardingScreen(
    keyManager: KeyManager,
    onIdentityReady: () -> Unit,
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Dispatch", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trips out. Notes back. Encrypted always.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = {
                runCatching { keyManager.createNewIdentity() }
                    .onSuccess { onIdentityReady() }
                    .onFailure { errorText = it.message ?: "Key generation failed" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create new identity") }
        Text(
            "Recommended: a fresh key used only by Dispatch keeps your travel data unlinkable to any public identity.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { errorText = ""; showImportDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import key (nsec)") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Login with Amber — coming soon") }

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showImportDialog) {
        var nsecInput by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf("") }
        var restoring by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import nsec") },
            text = {
                Column {
                    Text(
                        "Paste or type an nsec. Tip: use a dedicated key for Dispatch, not your main social identity.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nsecInput,
                        onValueChange = { nsecInput = it },
                        label = { Text("nsec1…") },
                        singleLine = true,
                    )
                    if (dialogError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(dialogError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { keyManager.importNsec(nsecInput) }
                        .onSuccess {
                            dialogError = ""
                            restoring = true
                            scope.launch {
                                runCatching {
                                    RestoreService(DispatchDatabase.get(context), keyManager.signer()).restore()
                                }
                                restoring = false
                                showImportDialog = false
                                onIdentityReady()
                            }
                        }
                        .onFailure { dialogError = "Not a valid nsec — check and try again" }
                }) { Text(if (restoring) "Restoring…" else "Import") }
            },
        )
    }
}