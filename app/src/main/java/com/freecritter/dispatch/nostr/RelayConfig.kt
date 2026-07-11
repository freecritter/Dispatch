package com.freecritter.dispatch.nostr

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Relay list in ONE place (spec §3): relay-agnostic, no single relay load-bearing.
 * User-editable relay management arrives with the settings screen; changing
 * defaults is a one-line edit here, zero code elsewhere.
 */
object RelayConfig {
    val defaults: List<String> = listOf(
        "wss://nos.lol",
        "wss://relay.primal.net",
    )

    fun normalized(): Set<NormalizedRelayUrl> =
        defaults.map { RelayUrlNormalizer.normalize(it) }.toSet()
}