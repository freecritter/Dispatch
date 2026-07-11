package com.freecritter.dispatch.nostr

/**
 * THE crypto boundary (spec §2). Everything — sign, NIP-44 encrypt, NIP-44 decrypt —
 * routes through this interface. No code outside this package touches key material
 * or assumes a key exists locally.
 *
 * Implementations:
 *  - NostrSignerInternal: key held in Android Keystore-protected storage (created in app / imported)
 *  - NostrSignerExternal: Amber via NIP-55 (intents + content resolver for background)
 *
 * Both are TODO() pending the de-risk slice (build order step 1), which selects
 * Quartz / rust-nostr / hand-rolled and fills these in. The rest of the app compiles
 * and runs offline against this interface without any implementation existing.
 */
interface NostrSigner {
    /** Hex public key of the data identity (the fresh, never-published npub). */
    suspend fun pubkeyHex(): String

    /** Build + sign a kind-30078 event with the given tags and (already encrypted) content. */
    suspend fun signEvent(kind: Int, tags: List<List<String>>, content: String, createdAt: Long): NostrEvent

    /** NIP-44 v2 encrypt to counterparty (pass own pubkey for encrypt-to-self backups). */
    suspend fun nip44Encrypt(plaintext: String, counterpartyPubkeyHex: String): String

    /** NIP-44 v2 decrypt from counterparty (pass own pubkey for own backups). */
    suspend fun nip44Decrypt(ciphertext: String, counterpartyPubkeyHex: String): String
}

/** Placeholder implementations — replaced during the de-risk slice. */
class NostrSignerInternal : NostrSigner {
    override suspend fun pubkeyHex(): String = TODO("De-risk slice: key generation + storage")
    override suspend fun signEvent(kind: Int, tags: List<List<String>>, content: String, createdAt: Long): NostrEvent =
        TODO("De-risk slice: BIP-340 signing via selected library")
    override suspend fun nip44Encrypt(plaintext: String, counterpartyPubkeyHex: String): String =
        TODO("De-risk slice: NIP-44 v2 via selected library — never hand-rolled")
    override suspend fun nip44Decrypt(ciphertext: String, counterpartyPubkeyHex: String): String =
        TODO("De-risk slice: NIP-44 v2 via selected library — never hand-rolled")
}
