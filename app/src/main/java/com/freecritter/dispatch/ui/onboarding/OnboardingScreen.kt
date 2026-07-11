package com.freecritter.dispatch.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Identity onboarding (spec §7.1). Three paths, privacy-by-default copy.
 * All three are wired to navigation only; actual key work lands with the de-risk slice:
 *  - Create: NostrSignerInternal generates a fresh, never-published keypair
 *  - Import: nsec entry (validated, no clipboard reads encouraged)
 *  - Amber: NIP-55 login intent; tip nudges a dedicated account
 */
@Composable
fun OnboardingScreen(onIdentityReady: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dispatch", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trips out. Notes back. Encrypted always.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onIdentityReady, // TODO: generate fresh key first (de-risk slice)
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create new identity") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onIdentityReady, // TODO: nsec import flow
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import key (nsec)") }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onIdentityReady, // TODO: NIP-55 Amber login intent
            modifier = Modifier.fillMaxWidth()
        ) { Text("Login with Amber") }

        Spacer(Modifier.height(24.dp))
        Text(
            "Tip: for Amber, create a new account just for Dispatch rather than reusing your social identity.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
