package dev.tinow.brainrotstopper.domain.anki

import dev.tinow.brainrotstopper.domain.model.CardRef
import dev.tinow.brainrotstopper.domain.model.DeckSelection
import dev.tinow.brainrotstopper.domain.model.Ease

/**
 * A [CardSource] that follows a changing choice (debug `SET_SOURCE`). Each call reads [key]; the delegate built by
 * [create] is kept while the key stays the same (fake sources hold state, e.g. the remaining cards) and replaced as soon
 * as it changes. So a lock screen that survives in the background picks up a new source instead of keeping the one it
 * was created with (M7-T1: e2e9/e2e_config failed on that race).
 */
class SwitchingCardSource<K>(
    private val key: () -> K,
    private val create: (K) -> CardSource,
) : CardSource {
    private var current: Pair<K, CardSource>? = null

    @Synchronized
    private fun pick(): CardSource {
        val k = key()
        current?.let { (ck, source) -> if (ck == k) return source }
        return create(k).also { current = k to it }
    }

    override suspend fun probe() = pick().probe()
    override suspend fun listDecks() = pick().listDecks()
    override suspend fun deckCounts(selection: DeckSelection) = pick().deckCounts(selection)
    override suspend fun nextCard(selection: DeckSelection) = pick().nextCard(selection)
    override suspend fun render(ref: CardRef) = pick().render(ref)
    override suspend fun cardProgress(ref: CardRef) = pick().cardProgress(ref)
    override suspend fun answer(ref: CardRef, ease: Ease, takenMs: Long) = pick().answer(ref, ease, takenMs)
}
