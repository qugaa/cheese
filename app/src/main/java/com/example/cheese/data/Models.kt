package com.example.cheese.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


enum class DateOffset(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    WEEKEND("This Weekend"),
    CUSTOM("Custom")
}

data class EventTemplate(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val name: String,
    val invitees: List<String> = emptyList()
)

data class Friend(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorIndex: Int
)

/**
 * A participant invited to the event.
 *
 * @property name        Display name of the participant.
 * @property colorIndex  Index into the CuratedParticipantColors palette (0-7).
 * @property isHost      True if this participant is the event organizer (host).
 */
data class Invitee(
    val name: String = "",
    val colorIndex: Int = 0,
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
 * @property dateOnlyMode    When true (e.g. multi-day trips), participants pick
 *                           whole dates instead of hourly time slots, and the
 *                           final selection is a date rather than a time slot.
 */
data class EventRequest(
    val id: String = UUID.randomUUID().toString(),
    val eventEmoji: String = "📅",
    val eventName: String = "",
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val startHour: Int = 0,
    val endHour: Int = 24,
    val invitees: List<Invitee> = emptyList(),
    val inviteeNames: List<String> = emptyList(),
    val dateOnlyMode: Boolean = false,
    val selectedDatesList: List<Long> = emptyList(),
    val createdAt: Long = 0L
)

/**
 * A single participant's availability mapped across the time grid.
 *
 * [availability] is a Set of UTC epoch millis representing the specific hour block.
 * This ensures that if the organizer changes the grid dimensions (adds/removes days),
 * the selections stay anchored to their real-world times.
 */
data class ParticipantResponse(
    val participantName: String = "",
    val availability: List<Long> = emptyList()
)

/**
 * The complete state of a scheduled event, containing the initial request
 * and the aggregated responses from participants.
 */
data class EventState(
    val request: EventRequest = EventRequest(),
    val responses: Map<String, ParticipantResponse> = emptyMap(),
    val finalCellIndex: Int? = null,
    val finalCellEndIndex: Int? = null,
    val organizerRestrictions: List<Long> = emptyList()
)

/**
 * Dynamic grid dimensions based on the event's date range.
 * The number of columns adapts dynamically to the concrete calendar dates.
 */
data class GridConfig(
    val startDateMillis: Long, 
    val endDateMillis: Long,
    val startHour: Int = 0,
    val endHour: Int = 24
) {
    /** Time slots: one row per hour. */
    val rows: Int = (endHour - startHour).coerceAtLeast(1)
    
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

    val hourLabels: List<String> = (startHour until endHour).map { h -> 
        val displayHour = if (h >= 24) h - 24 else h
        "%02d:00".format(displayHour)
    }

    /** Converts a (row, col) pair to a flat cell index. */
    fun cellIndex(row: Int, col: Int): Int = row * cols + col

    /** Extracts the day label from a flat cell index. */
    fun cellToDay(index: Int): String = dayLabels.getOrElse(index % cols) { "?" }

    /** Extracts the hour label from a flat cell index. */
    fun cellToHour(index: Int): String = hourLabels.getOrElse(index / cols) { "?" }

    /** Convert a flat cell index to absolute UTC millis for that hour block. */
    fun cellToTimestamp(index: Int): Long {
        val row = index / cols
        val col = index % cols
        val hour = startHour + row
        return startDateMillis + col * 86400000L + hour * 3600000L
    }

    /** Convert an absolute UTC millis timestamp back to a flat cell index, or null if out of bounds. */
    fun timestampToCell(timestamp: Long): Int? {
        val diff = timestamp - startDateMillis
        if (diff < 0) return null
        val col = (diff / 86400000L).toInt()
        if (col >= cols) return null
        
        val hourMillis = diff % 86400000L
        val hour = (hourMillis / 3600000L).toInt()
        val row = hour - startHour
        if (row < 0 || row >= rows) return null
        
        return cellIndex(row, col)
    }
}

fun EventState.isPastEvent(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
    val finalIndex = finalCellIndex ?: return false
    val finalEndIndex = finalCellEndIndex ?: finalIndex

    val isDateOnly = request.dateOnlyMode
    val config = if (isDateOnly) {
        GridConfig(
            startDateMillis = request.startDateMillis,
            endDateMillis = request.endDateMillis,
            startHour = 0,
            endHour = 1
        )
    } else {
        GridConfig(
            startDateMillis = request.startDateMillis,
            endDateMillis = request.endDateMillis,
            startHour = request.startHour,
            endHour = request.endHour
        )
    }

    val startTs = config.cellToTimestamp(finalIndex)
    val endTs = config.cellToTimestamp(finalEndIndex)
    val endTimestamp = maxOf(startTs, endTs)

    val endOfEventMillis = if (isDateOnly) {
        endTimestamp + 24L * 60 * 60 * 1000L
    } else {
        endTimestamp + 60L * 60 * 1000L
    }

    return currentTimeMillis > endOfEventMillis + 24L * 60 * 60 * 1000L
}

fun EventState.getFinalTimestamps(): List<Long> {
    val startCell = finalCellIndex ?: return emptyList()
    val endCell = finalCellEndIndex ?: startCell
    val dateOnly = request.dateOnlyMode
    val config = if (dateOnly) {
        GridConfig(request.startDateMillis, request.endDateMillis, 0, 1)
    } else {
        GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
    }
    val minCell = minOf(startCell, endCell)
    val maxCell = maxOf(startCell, endCell)
    
    return if (dateOnly) {
        (minCell..maxCell).map { config.cellToTimestamp(it) }
    } else {
        fun timeKey(index: Int): Int = (index % config.cols) * config.rows + (index / config.cols)
        val minKey = minOf(timeKey(startCell), timeKey(endCell))
        val maxKey = maxOf(timeKey(startCell), timeKey(endCell))
        (0 until config.totalCells)
            .filter { cell -> timeKey(cell) in minKey..maxKey }
            .map { config.cellToTimestamp(it) }
    }
}

fun List<EventState>.getConfirmedEventDates(): Map<java.time.LocalDate, List<String>> {
    val map = mutableMapOf<java.time.LocalDate, MutableList<String>>()
    this.forEach { state ->
        if (state.finalCellIndex != null) {
            val emoji = state.request.eventEmoji
            state.getFinalTimestamps().forEach { ts ->
                val date = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                map.getOrPut(date) { mutableListOf() }.add(emoji)
            }
        }
    }
    return map.mapValues { it.value.distinct() }
}

fun List<EventState>.getConfirmedEventCells(currentGridConfig: GridConfig): Map<Int, List<String>> {
    val map = mutableMapOf<Int, MutableList<String>>()
    this.forEach { state ->
        if (state.finalCellIndex != null) {
            val emoji = state.request.eventEmoji
            if (state.request.dateOnlyMode) {
                state.getFinalTimestamps().forEach { ts ->
                    val date = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                    for (hour in currentGridConfig.startHour until currentGridConfig.endHour) {
                        val hourTs = date.atStartOfDay(java.time.ZoneId.of("UTC")).plusHours(hour.toLong()).toInstant().toEpochMilli()
                        val cell = currentGridConfig.timestampToCell(hourTs)
                        if (cell != null) {
                            map.getOrPut(cell) { mutableListOf() }.add(emoji)
                        }
                    }
                }
            } else {
                state.getFinalTimestamps().forEach { ts ->
                    val cell = currentGridConfig.timestampToCell(ts)
                    if (cell != null) {
                        map.getOrPut(cell) { mutableListOf() }.add(emoji)
                    }
                }
            }
        }
    }
    return map.mapValues { it.value.distinct() }
}
