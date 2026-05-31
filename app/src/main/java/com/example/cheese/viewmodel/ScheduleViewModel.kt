package com.example.cheese.viewmodel

import androidx.lifecycle.ViewModel
import com.example.cheese.data.EventRequest
import com.example.cheese.data.EventState
import com.example.cheese.data.GridConfig
import com.example.cheese.data.Invitee
import com.example.cheese.data.ParticipantResponse
import com.example.cheese.ui.theme.CuratedParticipantColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScheduleViewModel : ViewModel() {

    // ── Global State: Multi-Event Dashboard ───────────────────────────────────

    private val _events = MutableStateFlow<List<EventState>>(emptyList())
    val events: StateFlow<List<EventState>> = _events.asStateFlow()

    private val _currentEventId = MutableStateFlow<String?>(null)
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    fun createNewEvent() {
        val newEvent = EventRequest()
        _eventRequest.value = newEvent
        _currentParticipantIndex.value = 0
        _draftAvailability.value = emptySet()
        _currentEventId.value = newEvent.id
    }

    fun selectEvent(eventId: String) {
        _currentEventId.value = eventId
    }

    fun deleteEvent(eventId: String) {
        _events.update { list -> list.filterNot { it.request.id == eventId } }
        if (_currentEventId.value == eventId) {
            _currentEventId.value = null
        }
    }

    private fun getCurrentEventState(): EventState? {
        val id = _currentEventId.value ?: return null
        return _events.value.find { it.request.id == id }
    }

    // ── View 1 state: Organizer Initiation ────────────────────────────────────

    private val _eventRequest = MutableStateFlow(EventRequest())
    val eventRequest: StateFlow<EventRequest> = _eventRequest.asStateFlow()

    fun updateEventEmoji(emoji: String) {
        _eventRequest.update { it.copy(eventEmoji = emoji) }
    }

    fun updateEventName(name: String) {
        _eventRequest.update { it.copy(eventName = name) }
    }

    fun updateDateRange(startMillis: Long, endMillis: Long) {
        _eventRequest.update {
            it.copy(startDateMillis = startMillis, endDateMillis = endMillis)
        }
    }

    fun updateStartDate(millis: Long) {
        _eventRequest.update { it.copy(startDateMillis = millis) }
    }

    fun updateEndDate(millis: Long) {
        _eventRequest.update { it.copy(endDateMillis = millis) }
    }

    // ── Invitee management ────────────────────────────────────────────────────

    fun addInvitee(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _eventRequest.update { current ->
            if (current.invitees.any { it.name == trimmed }) return@update current

            // First invitee is automatically the host
            val isHost = current.invitees.isEmpty()
            // Assign a color from the unused pool. The existing invitees ARE the
            // tracking set, so removals automatically free a color back up and we
            // never desync. Once every curated color is in use, the pool wraps and
            // we sample from the full palette again.
            val colorIndex = nextUniqueColorIndex(current.invitees.map { it.colorIndex })
            val newInvitee = Invitee(name = trimmed, colorIndex = colorIndex, isHost = isHost)

            current.copy(invitees = current.invitees + newInvitee)
        }
    }

    /**
     * Picks a palette index not already assigned to an existing participant.
     * Falls back to a full-palette sample once every curated color is taken.
     */
    private fun nextUniqueColorIndex(assigned: List<Int>): Int {
        val used = assigned.toSet()
        val available = CuratedParticipantColors.indices.filter { it !in used }
        return if (available.isNotEmpty()) available.random() else CuratedParticipantColors.indices.random()
    }

    fun removeInvitee(name: String) {
        _eventRequest.update { current ->
            current.copy(invitees = current.invitees.filterNot { it.name == name })
        }
    }

    fun updateInviteeColor(name: String, colorIndex: Int) {
        _eventRequest.update { current ->
            val updatedInvitees = current.invitees.map { invitee ->
                if (invitee.name == name) invitee.copy(colorIndex = colorIndex)
                else invitee
            }
            current.copy(invitees = updatedInvitees)
        }
    }

    /** Called when the organizer finalizes the event request and moves to View 2. */
    fun finalizeEventRequest() {
        val request = _eventRequest.value
        val newState = EventState(request = request)
        _events.update { list ->
            // Replace if exists, otherwise add
            val idx = list.indexOfFirst { it.request.id == request.id }
            if (idx >= 0) {
                val mut = list.toMutableList()
                mut[idx] = newState
                mut
            } else {
                list + newState
            }
        }
    }

    // ── View 2 state: Participant Availability Input ──────────────────────────

    private val _currentParticipantIndex = MutableStateFlow(0)
    val currentParticipantIndex: StateFlow<Int> = _currentParticipantIndex.asStateFlow()

    private val _draftAvailability = MutableStateFlow<Set<Int>>(emptySet())
    val draftAvailability: StateFlow<Set<Int>> = _draftAvailability.asStateFlow()

    fun currentInvitee(): Invitee? {
        val request = _events.value.find { it.request.id == _currentEventId.value }?.request ?: _eventRequest.value
        val index = _currentParticipantIndex.value
        return request.invitees.getOrNull(index)
    }

    fun totalParticipants(): Int {
        val request = _events.value.find { it.request.id == _currentEventId.value }?.request ?: _eventRequest.value
        return request.invitees.size
    }

    fun toggleCell(index: Int) {
        _draftAvailability.update { current ->
            if (index in current) current - index else current + index
        }
    }

    fun paintCell(index: Int, totalCells: Int) {
        if (index !in 0 until totalCells) return
        val eventState = getCurrentEventState() ?: return
        
        // Enforce organizer restrictions for non-first participants
        if (_currentParticipantIndex.value > 0 && index in eventState.organizerRestrictions) return
        
        _draftAvailability.update { it + index }
    }

    fun submitAvailability() {
        val invitee = currentInvitee() ?: return
        val name = invitee.name
        val draft = _draftAvailability.value
        val eventId = _currentEventId.value ?: return

        _events.update { list ->
            list.map { state ->
                if (state.request.id == eventId) {
                    val updatedResponses = state.responses + (name to ParticipantResponse(name, draft))
                    
                    var updatedRestrictions = state.organizerRestrictions
                    if (_currentParticipantIndex.value == 0) {
                        val gridConfig = GridConfig(state.request.startDateMillis, state.request.endDateMillis)
                        val allCells = (0 until gridConfig.totalCells).toSet()
                        updatedRestrictions = allCells - draft
                    }

                    state.copy(
                        responses = updatedResponses,
                        organizerRestrictions = updatedRestrictions
                    )
                } else {
                    state
                }
            }
        }

        _draftAvailability.value = emptySet()
        val total = totalParticipants()
        if (_currentParticipantIndex.value < total - 1) {
            _currentParticipantIndex.update { it + 1 }
        }
    }

    // ── View 3 state: Algorithmic Resolution ──────────────────────────────────

    fun getResponses(): Map<String, ParticipantResponse> {
        return getCurrentEventState()?.responses ?: emptyMap()
    }

    fun getFinalCellIndex(): Int? {
        return getCurrentEventState()?.finalCellIndex
    }

    fun computeHeatmap(gridConfig: GridConfig): Map<Int, Int> {
        val state = getCurrentEventState() ?: return emptyMap()
        val counts = IntArray(gridConfig.totalCells)
        for (response in state.responses.values) {
            for (cellIndex in response.availability) {
                if (cellIndex in 0 until gridConfig.totalCells) counts[cellIndex]++
            }
        }
        return counts.mapIndexed { idx, count -> idx to count }
            .filter { it.second > 0 }
            .toMap()
    }

    fun computeOptimalCell(gridConfig: GridConfig): Int? {
        val heatmap = computeHeatmap(gridConfig)
        return heatmap.maxByOrNull { it.value }?.key
    }

    fun setFinalEvent(cellIndex: Int) {
        val eventId = _currentEventId.value ?: return
        _events.update { list ->
            list.map {
                if (it.request.id == eventId) it.copy(finalCellIndex = cellIndex)
                else it
            }
        }
    }
}
