package dev.tinow.brainrotstopper.data.anki

/**
 * Provider constants of AnkiDroid v2.25.3 (`FlashCardsContract`, public API contract; strings written out here, nothing
 * copied from the AnkiDroid sources). Columns that were measured in the M0 spikes are noted in gotchas.md.
 */
object AnkiContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    const val PACKAGE = "com.ichi2.anki"

    const val DECKS = "decks"
    const val SCHEDULE = "schedule"
    const val MODELS = "models"
    fun deck(id: Long) = "decks/$id"
    fun note(noteId: Long) = "notes/$noteId"
    fun card(noteId: Long, ord: Int) = "notes/$noteId/cards/$ord"
    fun model(id: Long) = "models/$id"

    // decks
    const val DECK_ID = "deck_id"
    const val DECK_NAME = "deck_name"
    const val DECK_COUNT = "deck_count"
    const val DECK_DYN = "deck_dyn"

    // schedule (query selection `limit=?,deckID=?`; update values below)
    const val SCHEDULE_SELECTION = "limit=?,deckID=?"
    const val NOTE_ID = "note_id"
    const val ORD = "ord"
    const val BUTTON_COUNT = "button_count"
    const val NEXT_REVIEW_TIMES = "next_review_times"
    const val MEDIA_FILES = "media_files"
    const val ANSWER_EASE = "answer_ease"
    const val TIME_TAKEN = "time_taken"

    // cards
    const val QUESTION = "question"
    const val ANSWER = "answer"
    const val ANSWER_PURE = "answer_pure"
    const val REPS = "reps"

    // notes and models
    const val MID = "mid"
    const val CSS = "css"
}
