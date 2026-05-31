package com.example.cheese.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A participant invited to the event.
 *
 * @property name        Display name of the participant.
 * @property colorIndex  Index into the CuratedParticipantColors palette (0-7).
 * @property isHost      True if this participant is the event organizer (host).
 */
data class Invitee(
    val name: String,
    val colorIndex: Int,
    val isHost: Boolean = false
)

/**
 * Temporal constraints and metadata defined by the organizer.
 *
 * @property id              Unique identifier for the event.
 * @property eventEmoji      A selectable emoji icon for the event.
 * @property eventName       Human-readable label for the scheduling request.
 * @property startDateMillis Epoch millis for the first eligible day.
 * @property endDateMillis   Epoch millis for the last eligible day.
 * @property invitees        Ordered list of participants (with assigned colors/roles).
 */
data class EventRequest(
    val id: String = UUID.randomUUID().toString(),
    val eventEmoji: String = "📅",
    val eventName: String = "",
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val invitees: List<Invitee> = emptyList()
)

/**
 * A single participant's availability mapped across the time grid.
 *
 * [availability] is a Set of cell indices (row * COLS + col) that the
 * participant painted as "available" via the drag gesture. Using a Set
 * guarantees O(1) membership checks during heatmap computation.
 */
data class ParticipantResponse(
    val participantName: String,
    val availability: Set<Int> = emptySet()
)

/**
 * The complete state of a scheduled event, containing the initial request
 * and the aggregated responses from participants.
 */
data class EventState(
    val request: EventRequest,
    val responses: Map<String, ParticipantResponse> = emptyMap(),
    val finalCellIndex: Int? = null,
    val organizerRestrictions: Set<Int> = emptySet()
)

/**
 * Dynamic grid dimensions based on the event's date range.
 * The number of columns adapts dynamically to the concrete calendar dates.
 */
data class GridConfig(val startDateMillis: Long, val endDateMillis: Long) {
    /** Time slots: 08:00 – 21:00, one row per hour. */
    val rows: Int = 14
    
    /** Calculate columns based on the date range, min 1 column. */
    val cols: Int = run {
        if (startDateMillis == 0L || endDateMillis == 0L) return@run 7
        val diff = endDateMillis - startDateMillis
        val days = (diff / (1000 * 60 * 60 * 24)).toInt() + 1
        days.coerceAtLeast(1)
    }

    val totalCells: Int = rows * cols

    private val dateFormatter = SimpleDateFormat("EEE dd", Locale.getDefault())

    val dayLabels: List<String> = (0 until cols).map { colOffset ->
        if (startDateMillis > 0L) {
            val dateMillis = startDateMillis + colOffset * (1000 * 60 * 60 * 24L)
            dateFormatter.format(Date(dateMillis))
        } else {
            "Day ${colOffset + 1}"
        }
    }

    val hourLabels: List<String> = (8..21).map { h -> "%02d:00".format(h) }

    /** Converts a (row, col) pair to a flat cell index. */
    fun cellIndex(row: Int, col: Int): Int = row * cols + col

    /** Extracts the day label from a flat cell index. */
    fun cellToDay(index: Int): String = dayLabels.getOrElse(index % cols) { "?" }

    /** Extracts the hour label from a flat cell index. */
    fun cellToHour(index: Int): String = hourLabels.getOrElse(index / cols) { "?" }
}
