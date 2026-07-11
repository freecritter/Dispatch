package com.freecritter.dispatch.nostr

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec

/**
 * Identity custody for internal-key mode (spec §7.1). The private key lives in
 * EncryptedSharedPreferences (AES-256, master key in Android Keystore) and never
 * leaves this class except via the explicit reveal/export methods.
 *
 * Amber mode (NostrSignerExternal) arrives in a later release; everything that
 * consumes this class already talks to Quartz's NostrSigner supertype, so that
 * swap is additive.
 */
class KeyManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "dispatch_identity",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Volatile
    private var cachedSigner: NostrSignerInternal? = null

    fun hasIdentity(): Boolean = prefs.contains(PRIV_HEX)

    /** The signer for all crypto. Throws if onboarding hasn't created/imported a key. */
    fun signer(): NostrSignerInternal =
        cachedSigner ?: synchronized(this) {
            cachedSigner ?: run {
                val hex = prefs.getString(PRIV_HEX, null)
                    ?: error("No identity — onboarding not completed")
                NostrSignerInternal(KeyPair(privKey = hex.hexToBytes())).also { cachedSigner = it }
            }
        }

    /** Onboarding path 1: fresh, never-published keypair (privacy default). */
    fun createNewIdentity(): NostrSignerInternal {
        val keyPair = KeyPair() // random
        store(keyPair)
        return signer()
    }

    /** Onboarding path 2: import an existing nsec. Throws on malformed input. */
    fun importNsec(nsec: String): NostrSignerInternal {
        val privKey = nsec.trim().bechToBytes(hrp = "nsec")
        require(privKey.size == 32) { "Invalid nsec" }
        store(KeyPair(privKey = privKey))
        return signer()
    }

    /** Bech32 npub for display. Null before onboarding. */
    fun npub(): String? = if (hasIdentity()) signer().keyPair.pubKey.toNpub() else null

    /** Explicit reveal for the backup flow (show-and-transcribe; no clipboard writes here). */
    fun revealNsec(): String? =
        prefs.getString(PRIV_HEX, null)?.hexToBytes()?.toNsec()

    /** Identity reset (settings). Does not touch Room data. */
    fun wipeIdentity() {
        prefs.edit().remove(PRIV_HEX).apply()
        cachedSigner = null
    }

    private fun store(keyPair: KeyPair) {
        val priv = keyPair.privKey ?: error("KeyPair has no private key")
        prefs.edit().putString(PRIV_HEX, priv.toHexString()).apply()
        cachedSigner = null
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val PRIV_HEX = "priv_hex"
    }
}
