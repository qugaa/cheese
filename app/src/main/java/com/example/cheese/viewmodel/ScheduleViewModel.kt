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
import com.example.cheese.data.DateOffset
import com.example.cheese.data.EventTemplate
import com.example.cheese.data.Friend
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

class ScheduleViewModel : ViewModel() {

    // ── Global State: Multi-Event Dashboard ───────────────────────────────────

    private val _events = MutableStateFlow<List<EventState>>(emptyList())
    val events: StateFlow<List<EventState>> = _events.asStateFlow()

    private val _currentEventId = MutableStateFlow<String?>(null)
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    fun addFriend(name: String, colorIndex: Int) {
        _friends.update { list -> 
            if (list.none { it.name == name }) {
                list + Friend(name = name, colorIndex = colorIndex)
            } else list
        }
    }

    fun removeFriend(name: String) {
        _friends.update { list -> list.filterNot { it.name == name } }
    }

    fun createNewEvent() {
        val newEvent = EventRequest()
        _eventRequest.value = newEvent
        _currentParticipantIndex.value = 0
        _draftAvailability.value = emptySet()
        _currentEventId.value = newEvent.id
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private val _templates = MutableStateFlow<List<EventTemplate>>(
        listOf(
            EventTemplate(emoji = "🍿", name = "Cinema", dateOffset = DateOffset.TOMORROW),
            EventTemplate(emoji = "🍻", name = "Bar", dateOffset = DateOffset.TODAY),
            EventTemplate(emoji = "📚", name = "Study Session", dateOffset = DateOffset.WEEKEND)
        )
    )
    val templates: StateFlow<List<EventTemplate>> = _templates.asStateFlow()

    fun createFromTemplate(template: EventTemplate) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val date = when (template.dateOffset) {
            DateOffset.TODAY -> today
            DateOffset.TOMORROW -> today.plusDays(1)
            DateOffset.WEEKEND -> {
                if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) today
                else today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            }
            DateOffset.CUSTOM -> today
        }
        val dateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val newEvent = EventRequest(
            eventEmoji = template.emoji,
            eventName = template.name,
            startDateMillis = dateMillis,
            endDateMillis = dateMillis,
            startHour = 8,
            endHour = 22
        )
        _eventRequest.value = newEvent
        _currentParticipantIndex.value = 0
        _draftAvailability.value = emptySet()
        _currentEventId.value = newEvent.id
    }

    fun saveTemplate(template: EventTemplate) {
        _templates.update { it + template }
    }

    fun selectEvent(eventId: String) {
        _currentEventId.value = eventId
    }

    fun editEvent(eventId: String) {
        _currentEventId.value = eventId
        val state = _events.value.find { it.request.id == eventId }
        if (state != null) {
            _eventRequest.value = state.request
        }
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

    fun updateTimeRange(startHour: Int, endHour: Int) {
        _eventRequest.update {
            it.copy(startHour = startHour, endHour = endHour)
        }
    }

    fun updateStartDate(millis: Long) {
        _eventRequest.update { it.copy(startDateMillis = millis) }
    }

    fun updateEndDate(millis: Long) {
        _eventRequest.update { it.copy(endDateMillis = millis) }
    }

    // ── Invitee management ────────────────────────────────────────────────────

    fun addInvitee(name: String, providedColorIndex: Int? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _eventRequest.update { current ->
            if (current.invitees.any { it.name == trimmed }) return@update current

            // First invitee is automatically the host
            val isHost = current.invitees.isEmpty()
            val colorIndex = providedColorIndex ?: nextUniqueColorIndex(current.invitees.map { it.colorIndex })
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
        _events.update { list ->
            val idx = list.indexOfFirst { it.request.id == request.id }
            if (idx >= 0) {
                val existing = list[idx]
                val mut = list.toMutableList()
                mut[idx] = existing.copy(request = request)
                mut
            } else {
                list + EventState(request = request)
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
        if (_currentParticipantIndex.value > 0) {
            val request = eventState.request
            val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
            val timestamp = config.cellToTimestamp(index)
            if (timestamp in eventState.organizerRestrictions) return
        }
        
        _draftAvailability.update { it + index }
    }

    private fun loadDraftForCurrentParticipant() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        val request = state?.request ?: _eventRequest.value
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        
        val existingResponse = state?.responses?.get(invitee.name)
        val timestamps = existingResponse?.availability ?: emptySet()
        
        _draftAvailability.value = timestamps.mapNotNull { config.timestampToCell(it) }.toSet()
    }

    fun saveCurrentDraft() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        val request = state?.request ?: _eventRequest.value
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        
        val timestamps = _draftAvailability.value.map { config.cellToTimestamp(it) }.toSet()
        
        _events.update { list ->
            list.map { ev ->
                if (ev.request.id == eventId) {
                    val updatedResponses = ev.responses + (invitee.name to ParticipantResponse(invitee.name, timestamps))
                    var updatedRestrictions = ev.organizerRestrictions
                    if (_currentParticipantIndex.value == 0) {
                        val allCells = (0 until config.totalCells).toSet()
                        val restrictedCells = allCells - _draftAvailability.value
                        updatedRestrictions = restrictedCells.map { config.cellToTimestamp(it) }.toSet()
                    }
                    ev.copy(responses = updatedResponses, organizerRestrictions = updatedRestrictions)
                } else ev
            }
        }
    }

    fun previousParticipant() {
        if (_currentParticipantIndex.value > 0) {
            saveCurrentDraft()
            _currentParticipantIndex.update { it - 1 }
            loadDraftForCurrentParticipant()
        }
    }

    fun submitAvailability() {
        saveCurrentDraft()

        val total = totalParticipants()
        if (_currentParticipantIndex.value < total - 1) {
            _currentParticipantIndex.update { it + 1 }
            loadDraftForCurrentParticipant()
        } else {
            _draftAvailability.value = emptySet()
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
            for (timestamp in response.availability) {
                val cellIndex = gridConfig.timestampToCell(timestamp)
                if (cellIndex != null && cellIndex in 0 until gridConfig.totalCells) {
                    counts[cellIndex]++
                }
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
