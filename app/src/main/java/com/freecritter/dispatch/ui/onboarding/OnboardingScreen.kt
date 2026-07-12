package com.freecritter.dispatch.ui.onboarding

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.nostr.KeyManager
import com.freecritter.dispatch.nostr.RestoreService
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.isExternalSignerInstalled
import kotlinx.coroutines.launch

/**
 * Identity onboarding (spec §7.1). Three live paths:
 *  - Create: fresh, never-published keypair (privacy default)
 *  - Import: existing nsec, auto-restores backup after import
 *  - Amber: NIP-55 login, auto-restores backup after login (decrypt via Amber)
 *
 * Amber permissions: requested up front at login so the OutboxWorker's
 * background signing (kind 30078) and NIP-44 encrypt/decrypt never need
 * per-event foreground approval. Quartz's DefaultPermissions only covers
 * relay-auth signing (kind 22242) — not enough for Dispatch.
 */
private val DispatchAmberPermissions = listOf(
    Permission(CommandType.SIGN_EVENT, 30078),
    Permission(CommandType.NIP44_ENCRYPT),
    Permission(CommandType.NIP44_DECRYPT),
)

@Composable
fun OnboardingScreen(
    keyManager: KeyManager,
    onIdentityReady: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var amberRestoring by remember { mutableStateOf(false) }

    val amberInstalled = remember { isExternalSignerInstalled(context) }

    val amberLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        val data = activityResult.data
        if (activityResult.resultCode != Activity.RESULT_OK || data == null) {
            errorText = "Amber login cancelled"
            return@rememberLauncherForActivityResult
        }
        when (val parsed = ExternalSignerLogin.parseResult(data)) {
            is SignerResult.RequestAddressed.Successful -> {
                keyManager.loginWithAmber(
                    pubkeyHex = parsed.result.pubkey,
                    signerPackage = parsed.result.packageName,
                )
                // Mirror the import path: pull any existing backup before
                // entering the app, decrypting through Amber. Best-effort —
                // a fresh Amber account simply restores nothing.
                amberRestoring = true
                scope.launch {
                    runCatching {
                        RestoreService(DispatchDatabase.get(context), keyManager.signer()).restore()
                    }
                    amberRestoring = false
                    onIdentityReady()
                }
            }
            else -> errorText = "Amber login failed — try again"
        }
    }

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
            enabled = !amberRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create new identity") }
        Text(
            "Recommended: a fresh key used only by Dispatch keeps your travel data unlinkable to any public identity.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { errorText = ""; showImportDialog = true },
            enabled = !amberRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import key (nsec)") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                errorText = ""
                amberLauncher.launch(
                    ExternalSignerLogin.createIntent(permissions = DispatchAmberPermissions),
                )
            },
            enabled = amberInstalled && !amberRestoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    amberRestoring -> "Restoring backup…"
                    amberInstalled -> "Login with Amber"
                    else -> "Login with Amber — install Amber first"
                },
            )
        }
        if (amberInstalled) {
            Text(
                "Tip: create a new account in Amber for Dispatch rather than reusing your social identity.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showImportDialog) {
        var nsecInput by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf("") }
        var restoring by remember { mutableStateOf(false) }

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
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
        )
    }
}