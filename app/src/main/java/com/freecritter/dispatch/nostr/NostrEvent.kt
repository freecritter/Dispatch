package com.freecritter.dispatch.nostr

import kotlinx.serialization.Serializable

/**
 * Minimal NIP-01 event shape, library-agnostic. The de-risk slice decides whether
 * Quartz's own event types replace this or we map to/from it at the boundary.
 */
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val created_at: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
)

/** Dispatch event conventions (spec §5). */
object DispatchEvents {
    const val KIND_APP_DATA = 30078            // NIP-78 addressable app data — all Dispatch events
    const val PAYLOAD_SCHEMA_VERSION = 1       // "v" field inside every encrypted payload

    /** Tags for a backup/share event: opaque d only; p tag only on share copies. */
    fun tags(d: String, recipientPubkeyHex: String? = null): List<List<String>> =
        buildList {
            add(listOf("d", d))
            recipientPubkeyHex?.let { add(listOf("p", it)) }
        }
}
