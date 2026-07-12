package com.freecritter.dispatch.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.freecritter.dispatch.data.DispatchRepository
import com.freecritter.dispatch.data.db.DispatchDatabase
import com.freecritter.dispatch.nostr.KeyManager
import com.freecritter.dispatch.nostr.OutboxWorker
import com.freecritter.dispatch.nostr.RelayConfig
import com.freecritter.dispatch.nostr.RestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings & safety (spec §7.13): identity visibility, backup status,
 * guarded nsec reveal with sensitive-flagged copy, identity reset,
 * relay readout, version.
 *
 * Reset semantics (0.3): reset = factory reset of app data. Wipes the
 * Room DB (trips, components, outbox — everything) AND the key material.
 * Rationale: leaving local data across a key rotation silently republishes
 * old-identity data under the new npub on next edit — a structural
 * cross-identity leak. Reset must mean reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DispatchRepository,
    keyManager: KeyManager,
    onIdentityReset: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pendingBackups by repository.observePendingBackups().collectAsState(initial = 0)

    var showNsecWarning by remember { mutableStateOf(false) }
    var revealedNsec by remember { mutableStateOf<String?>(null) }
    var showResetWarning by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val db = remember { DispatchDatabase.get(context) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
            // --- Identity ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Identity", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Your Dispatch data key (npub):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        keyManager.npub() ?: "No identity",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (!keyManager.canRevealNsec()) {
                        Text(
                            "Key held by Amber — manage it there.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (revealedNsec == null) {
                        OutlinedButton(onClick = { showNsecWarning = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reveal secret key (nsec)")
                        }
                    } else {
                        Text("Secret key — store it somewhere safe, then hide it:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            revealedNsec!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                val clip = android.content.ClipData.newPlainText("nsec", revealedNsec)
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    clip.description.extras = android.os.PersistableBundle().apply {
                                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                                clipboard.setPrimaryClip(clip)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy (paste it somewhere safe, e.g. your password manager)") }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(onClick = { revealedNsec = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Hide")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Backup ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Encrypted backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (pendingBackups == 0) "✓ All changes backed up"
                        else "$pendingBackups change(s) waiting to publish",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Relays: " + RelayConfig.defaults.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { OutboxWorker.requestDrain(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back up now") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            restoreStatus = "Restoring…"
                            scope.launch {
                                restoreStatus = runCatching {
                                    val r = RestoreService(db, keyManager.signer()).restore()
                                    "Restored ${r.restored} of ${r.eventsFound} events (${r.skipped} skipped)"
                                }.getOrElse { "Restore failed: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore from backup") }
                    if (restoreStatus.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(restoreStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If backups stall on GrapheneOS, check Settings → Apps → Dispatch → Network permission.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Danger zone ---
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Danger zone", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showResetWarning = true },
                        enabled = !resetting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (resetting) "Resetting…" else "Reset identity & erase data")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Dispatch $versionName", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showNsecWarning) {
        AlertDialog(
            onDismissRequest = { showNsecWarning = false },
            title = { Text("Reveal secret key?") },
            text = {
                Text(
                    "Anyone who sees this key can read your entire backup history — there is no way to undo exposure. " +
                            "Write it down somewhere safe; avoid screenshots and the clipboard.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    revealedNsec = keyManager.revealNsec()
                    showNsecWarning = false
                }) { Text("Reveal") }
            },
            dismissButton = { TextButton(onClick = { showNsecWarning = false }) { Text("Cancel") } },
        )
    }

    if (showResetWarning) {
        AlertDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("Reset identity & erase data?") },
            text = {
                Text(
                    "This erases ALL Dispatch data on this device — trips, components, pending backups — " +
                            "and forgets your key. Encrypted backups already on the relays stay there: " +
                            "you can restore them later by importing this key's nsec or logging into the same Amber account. " +
                            "Without the key, they are unreachable forever. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetWarning = false
                    resetting = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.clearAllTables()
                        }
                        keyManager.wipeIdentity()
                        resetting = false
                        onIdentityReset()
                    }
                }) { Text("Erase & reset") }
            },
            dismissButton = { TextButton(onClick = { showResetWarning = false }) { Text("Cancel") } },
        )
    }
}