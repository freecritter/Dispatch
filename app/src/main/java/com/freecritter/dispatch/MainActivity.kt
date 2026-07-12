package com.freecritter.dispatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.freecritter.dispatch.nostr.KeyManager
import com.freecritter.dispatch.ui.nav.DispatchNav
import com.freecritter.dispatch.ui.theme.DispatchTheme
import com.vitorpamplona.quartz.nip55AndroidSigner.client.IActivityLauncher

class MainActivity : ComponentActivity() {

    private val keyManager: KeyManager
        get() = (application as DispatchApp).keyManager

    /**
     * NIP-55 foreground fallback (Gate C). When Amber's background
     * content-resolver path can't serve a request (permission not granted,
     * Amber asks for manual approval), NostrSignerExternal fires an intent
     * through a registered launcher and waits for newResponse(). This is
     * that plumbing. Results are routed via keyManager.signer() — the cached
     * instance is the one that made the request — never a stored reference,
     * because activity results arrive before onResume.
     */
    private val amberResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent = result.data ?: return@registerForActivityResult
            if (keyManager.mode() == KeyManager.Mode.AMBER) {
                (keyManager.signer() as? IActivityLauncher)?.newResponse(data)
            }
        }

    private val foregroundLauncher: (Intent) -> Unit = { intent ->
        amberResultLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DispatchTheme {
                Surface {
                    DispatchNav(
                        repository = (application as DispatchApp).repository,
                        keyManager = (application as DispatchApp).keyManager,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (keyManager.mode() == KeyManager.Mode.AMBER) {
            (keyManager.signer() as? IActivityLauncher)
                ?.registerForegroundLauncher(foregroundLauncher)
        }
    }

    override fun onPause() {
        if (keyManager.mode() == KeyManager.Mode.AMBER) {
            (keyManager.signer() as? IActivityLauncher)
                ?.unregisterForegroundLauncher(foregroundLauncher)
        }
        super.onPause()
    }
}