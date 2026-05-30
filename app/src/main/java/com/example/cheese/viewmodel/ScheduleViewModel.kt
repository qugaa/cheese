package com.example.cheese.viewmodel

import androidx.lifecycle.ViewModel
import com.example.cheese.data.EventRequest
import com.example.cheese.data.GridConfig
import com.example.cheese.data.MOCK_PARTICIPANTS
import com.example.cheese.data.ParticipantResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central state container for the Cheese scheduling flow.
 *
 * Architecture rationale: A single ViewModel shared across all three screens
 * prevents redundant state hoisting through navigation arguments and keeps the
 * data model the single source of truth — critical for the Noun-Verb interaction
 * paradigm where the selected "noun" (event / availability cell) must persist
 * across screen transitions without serialization overhead.
 */
class ScheduleViewModel : ViewModel() {

    // ── View 1 state ──────────────────────────────────────────────────────────

    /** Organizer's event constraints; mutable until "Request Availability" fires. */
    private val _eventRequest = MutableStateFlow(EventRequest())
    val eventRequest: StateFlow<EventRequest> = _eventRequest.asStateFlow()

    fun updateEventName(name: String) {
        _eventRequest.update { it.copy(eventName = name) }
    }

    fun updateStartDate(millis: Long) {
        _eventRequest.update { it.copy(startDateMillis = millis) }
    }

    fun updateEndDate(millis: Long) {
        _eventRequest.update { it.copy(endDateMillis = millis) }
    }

    // ── View 2 state ──────────────────────────────────────────────────────────

    /**
     * Index of the mock participant currently filling in availability.
     * Cycles through [MOCK_PARTICIPANTS] to simulate multiple users without
     * a real authentication layer.
     */
    private val _currentParticipantIndex = MutableStateFlow(0)
    val currentParticipantIndex: StateFlow<Int> = _currentParticipantIndex.asStateFlow()

    /** Cells painted during the current drag session (pre-submit). */
    private val _draftAvailability = MutableStateFlow<Set<Int>>(emptySet())
    val draftAvailability: StateFlow<Set<Int>> = _draftAvailability.asStateFlow()

    /** All confirmed participant responses, keyed by participant name. */
    private val _responses = MutableStateFlow<Map<String, ParticipantResponse>>(emptyMap())
    val responses: StateFlow<Map<String, ParticipantResponse>> = _responses.asStateFlow()

    fun toggleCell(index: Int) {
        _draftAvailability.update { current ->
            if (index in current) current - index else current + index
        }
    }

    fun paintCell(index: Int) {
        // Called during drag; only adds — never removes — to reduce mis-selection errors.
        _draftAvailability.update { it + index }
    }

    fun submitAvailability() {
        val name = MOCK_PARTICIPANTS[_currentParticipantIndex.value]
        _responses.update { current ->
            current + (name to ParticipantResponse(name, _draftAvailability.value))
        }
        _draftAvailability.value = emptySet()
        if (_currentParticipantIndex.value < MOCK_PARTICIPANTS.lastIndex) {
            _currentParticipantIndex.update { it + 1 }
        }
    }

    /** Resets draft so a new participant starts with a clean slate. */
    fun clearDraft() {
        _draftAvailability.value = emptySet()
    }

    // ── View 3 state ──────────────────────────────────────────────────────────

    /**
     * Heatmap: maps cell index → count of participants available in that slot.
     * Derived lazily from [_responses] so it is always consistent with submitted
     * data — no separate mutation path that could introduce stale state.
     */
    fun computeHeatmap(): Map<Int, Int> {
        val totalCells = GridConfig.ROWS * GridConfig.COLS
        val counts = IntArray(totalCells)
        for (response in _responses.value.values) {
            for (cellIndex in response.availability) {
                if (cellIndex in 0 until totalCells) counts[cellIndex]++
            }
        }
        return counts.mapIndexed { idx, count -> idx to count }.filter { it.second > 0 }.toMap()
    }

    /**
     * OptimalTime: returns the cell index with the highest participant consensus.
     * Ties broken by lower index (earlier time slot) — deterministic output
     * eliminates ambiguity in the final decision.
     */
    fun computeOptimalCell(): Int? {
        val heatmap = computeHeatmap()
        return heatmap.maxByOrNull { it.value }?.key
    }

    /** Total number of participants who have submitted a response. */
    val submittedCount: Int get() = _responses.value.size

    // ── Final event state ─────────────────────────────────────────────────────

    private val _finalCellIndex = MutableStateFlow<Int?>(null)
    val finalCellIndex: StateFlow<Int?> = _finalCellIndex.asStateFlow()

    fun setFinalEvent(cellIndex: Int) {
        _finalCellIndex.value = cellIndex
    }
}
