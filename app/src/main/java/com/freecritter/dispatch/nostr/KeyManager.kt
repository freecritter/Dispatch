package com.freecritter.dispatch.nostr

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal

/**
 * Identity custody, two modes (spec §7.1 / 0.3):
 *  - INTERNAL: private key in EncryptedSharedPreferences (Keystore master key)
 *  - AMBER: key lives in Amber (NIP-55); we store only pubkey + signer package
 * Everything downstream consumes the NostrSigner supertype and never knows which.
 */
class KeyManager(private val context: Context) {

    enum class Mode { NONE, INTERNAL, AMBER }

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
    private var cachedSigner: NostrSigner? = null

    fun mode(): Mode = when {
        prefs.contains(AMBER_PUBKEY) -> Mode.AMBER
        prefs.contains(PRIV_HEX) -> Mode.INTERNAL
        else -> Mode.NONE
    }

    fun hasIdentity(): Boolean = mode() != Mode.NONE

    /** The signer for all crypto, whichever mode. Throws before onboarding. */
    fun signer(): NostrSigner =
        cachedSigner ?: synchronized(this) {
            cachedSigner ?: buildSigner().also { cachedSigner = it }
        }

    private fun buildSigner(): NostrSigner = when (mode()) {
        Mode.INTERNAL -> {
            val hex = prefs.getString(PRIV_HEX, null) ?: error("No identity")
            NostrSignerInternal(KeyPair(privKey = hex.hexToBytes()))
        }
        Mode.AMBER -> {
            val pub = prefs.getString(AMBER_PUBKEY, null) ?: error("No identity")
            val pkg = prefs.getString(AMBER_PACKAGE, null) ?: error("No signer package")
            NostrSignerExternal(pub, pkg, context.applicationContext.contentResolver)
        }
        Mode.NONE -> error("No identity — onboarding not completed")
    }

    /** Onboarding path 1: fresh, never-published keypair (privacy default). */
    fun createNewIdentity(): NostrSigner {
        store(KeyPair())
        return signer()
    }

    /** Onboarding path 2: import an existing nsec. Throws on malformed input. */
    fun importNsec(nsec: String): NostrSigner {
        val privKey = nsec.trim().bechToBytes(hrp = "nsec")
        require(privKey.size == 32) { "Invalid nsec" }
        store(KeyPair(privKey = privKey))
        return signer()
    }

    /** Onboarding path 3: Amber login result (NIP-55). */
    fun loginWithAmber(pubkeyHex: String, signerPackage: String): NostrSigner {
        prefs.edit()
            .remove(PRIV_HEX)
            .putString(AMBER_PUBKEY, pubkeyHex)
            .putString(AMBER_PACKAGE, signerPackage)
            .apply()
        cachedSigner = null
        return signer()
    }

    /** Bech32 npub for display, any mode. Null before onboarding. */
    fun npub(): String? = when (mode()) {
        Mode.NONE -> null
        else -> signer().pubKey.hexToBytes().toNpub()
    }

    /** True only when a local secret exists to reveal (INTERNAL mode). */
    fun canRevealNsec(): Boolean = mode() == Mode.INTERNAL

    fun revealNsec(): String? =
        prefs.getString(PRIV_HEX, null)?.hexToBytes()?.toNsec()

    /** Identity reset (settings). Does not touch Room data. */
    fun wipeIdentity() {
        prefs.edit()
            .remove(PRIV_HEX)
            .remove(AMBER_PUBKEY)
            .remove(AMBER_PACKAGE)
            .apply()
        cachedSigner = null
    }

    private fun store(keyPair: KeyPair) {
        val priv = keyPair.privKey ?: error("KeyPair has no private key")
        prefs.edit()
            .remove(AMBER_PUBKEY)
            .remove(AMBER_PACKAGE)
            .putString(PRIV_HEX, priv.toHexString())
            .apply()
        cachedSigner = null
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val PRIV_HEX = "priv_hex"
        private const val AMBER_PUBKEY = "amber_pubkey"
        private const val AMBER_PACKAGE = "amber_package"
    }
}