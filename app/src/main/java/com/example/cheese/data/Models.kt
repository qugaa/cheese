package com.example.cheese.data

/**
 * Temporal constraints defined by the organizer (Noun in Noun-Verb paradigm).
 * Bounding the date range reduces the cognitive load on participants by
 * shrinking the decision space (Hick's Law).
 */
data class EventRequest(
    val eventName: String = "",
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L
)

/**
 * A single participant's availability mapped across the time grid.
 * [availability] is a Set of cell indices (row * COLS + col) that the
 * participant painted as "available" via the drag gesture.
 */
data class ParticipantResponse(
    val participantName: String,
    val availability: Set<Int> = emptySet()
)

/**
 * Canonical grid dimensions shared across View 2 (input) and View 3 (heatmap).
 * Fixed constants eliminate layout ambiguity and keep cell sizing deterministic.
 */
object GridConfig {
    /** Time slots: 08:00 – 21:00, one row per hour. */
    const val ROWS = 14
    /** Days of the week shown in the grid. */
    const val COLS = 7
    val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val HOUR_LABELS = (8..21).map { h -> "%02d:00".format(h) }

    fun cellIndex(row: Int, col: Int): Int = row * COLS + col
}

/** Mock participant names used to simulate multi-user responses. */
val MOCK_PARTICIPANTS = listOf("Alice", "Bob", "Carol", "Dave")
