package com.example.cheese.viewmodel

import androidx.lifecycle.ViewModel
import com.example.cheese.data.*
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScheduleViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // ── Auth & Users ──────────────────────────────────────────────────────────

    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _events = MutableStateFlow<List<EventState>>(emptyList())
    val events: StateFlow<List<EventState>> = _events.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private var userListener: ListenerRegistration? = null
    private var eventsListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null

    fun login(username: String, onSuccess: () -> Unit) {
        if (username.isBlank()) return
        _isLoggingIn.value = true

        val docRef = db.collection("users").document(username)
        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                _currentUser.value = username
                setupRealtimeListeners(username)
                onSuccess()
                _isLoggingIn.value = false
            } else {
                // Create new user
                val newUser = hashMapOf(
                    "name" to username,
                    "friends" to emptyList<String>(),
                    "templates" to emptyList<Map<String, Any>>()
                )
                docRef.set(newUser).addOnSuccessListener {
                    _currentUser.value = username
                    setupRealtimeListeners(username)
                    onSuccess()
                    _isLoggingIn.value = false
                }
            }
        }.addOnFailureListener {
            _isLoggingIn.value = false
        }
    }

    fun logout() {
        userListener?.remove()
        eventsListener?.remove()
        notificationsListener?.remove()
        _currentUser.value = null
        _friends.value = emptyList()
        _events.value = emptyList()
        _templates.value = emptyList()
        _notifications.value = emptyList()
    }

    private fun setupRealtimeListeners(username: String) {
        userListener?.remove()
        userListener = db.collection("users").document(username).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            val friendsList = snapshot.get("friends") as? List<String> ?: emptyList()
            // Map string array to local Friend UI objects (assigning unique colors)
            _friends.value = friendsList.mapIndexed { idx, name ->
                Friend(name = name, colorIndex = idx % CuratedParticipantColors.size)
            }

            val templatesMapList = snapshot.get("templates") as? List<Map<String, Any>> ?: emptyList()
            _templates.value = templatesMapList.mapNotNull { map ->
                try {
                    EventTemplate(
                        id = map["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                        emoji = map["emoji"] as? String ?: "",
                        name = map["name"] as? String ?: "",
                        invitees = map["invitees"] as? List<String> ?: emptyList()
                    )
                } catch (e: Exception) { null }
            }
        }

        eventsListener?.remove()
        eventsListener = db.collection("events")
            .whereArrayContains("request.inviteeNames", username)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val fetchedEvents = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(EventState::class.java)
                }
                _events.value = fetchedEvents
            }

        notificationsListener?.remove()
        notificationsListener = db.collection("notifications")
            .whereEqualTo("recipient", username)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val fetched = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Notification::class.java)
                }
                _notifications.value = fetched.sortedByDescending { it.createdAt }
            }
    }

    private fun sendNotification(
        recipient: String,
        sender: String,
        type: String,
        eventId: String,
        eventName: String,
        eventEmoji: String,
        message: String
    ) {
        val notification = Notification(
            recipient = recipient,
            sender = sender,
            type = type,
            eventId = eventId,
            eventName = eventName,
            eventEmoji = eventEmoji,
            message = message,
            createdAt = System.currentTimeMillis(),
            read = false
        )
        db.collection("notifications").document(notification.id).set(notification)
    }

    fun markNotificationAsRead(id: String) {
        db.collection("notifications").document(id).update("read", true)
    }

    fun markNotificationsForEventAsRead(eventId: String) {
        val current = _currentUser.value ?: return
        db.collection("notifications")
            .whereEqualTo("recipient", current)
            .whereEqualTo("eventId", eventId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.update(doc.reference, "read", true)
                }
                batch.commit()
            }
    }

    fun clearAllNotifications() {
        val current = _currentUser.value ?: return
        db.collection("notifications")
            .whereEqualTo("recipient", current)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit()
            }
    }

    private fun getProposedTimeframeString(request: EventRequest): String {
        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return if (request.startDateMillis > 0L && request.endDateMillis > 0L) {
            "${dateFormatter.format(Date(request.startDateMillis))} → ${dateFormatter.format(Date(request.endDateMillis))}"
        } else {
            "Not specified"
        }
    }

    fun addFriend(name: String, onResult: (Boolean, String) -> Unit) {
        val current = _currentUser.value ?: return
        val friendName = name.trim()

        if (friendName == current) {
            onResult(false, "You cannot add yourself as a friend.")
            return
        }

        db.collection("users").document(friendName).get().addOnSuccessListener { doc: com.google.firebase.firestore.DocumentSnapshot ->
            if (doc.exists()) {
                val userRef = db.collection("users").document(current)
                userRef.get().addOnSuccessListener { userDoc: com.google.firebase.firestore.DocumentSnapshot ->
                    val existingFriends = userDoc.get("friends") as? List<String> ?: emptyList()
                    if (!existingFriends.contains(friendName)) {
                        val newFriends = existingFriends + friendName
                        userRef.update("friends", newFriends).addOnSuccessListener {
                            onResult(true, "Friend added successfully.")
                        }
                    } else {
                        onResult(false, "Already friends with $friendName.")
                    }
                }
            } else {
                onResult(false, "User '$friendName' not found.")
            }
        }
    }

    fun searchUsers(query: String, onResult: (List<String>) -> Unit) {
        val q = query.trim()
        if (q.isBlank()) {
            onResult(emptyList())
            return
        }
        db.collection("users")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId())
            .startAt(q)
            .endAt(q + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot: com.google.firebase.firestore.QuerySnapshot ->
                val prefixMatches = snapshot.documents.map { it.id }
                if (prefixMatches.isNotEmpty()) {
                    onResult(prefixMatches)
                } else {
                    db.collection("users").limit(100).get()
                        .addOnSuccessListener { all: com.google.firebase.firestore.QuerySnapshot ->
                            onResult(all.documents.map { it.id }.filter { it.contains(q, ignoreCase = true) })
                        }
                        .addOnFailureListener { onResult(emptyList()) }
                }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun removeFriend(name: String) {
        val current = _currentUser.value ?: return
        val userRef = db.collection("users").document(current)
        userRef.get().addOnSuccessListener { userDoc ->
            val existingFriends = userDoc.get("friends") as? List<String> ?: emptyList()
            val newFriends = existingFriends.filterNot { it == name }
            userRef.update("friends", newFriends)
        }
    }

    // ── Global State: Multi-Event Dashboard ───────────────────────────────────

    private val _currentEventId = MutableStateFlow<String?>(null)
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    private val _dashboardMessage = MutableStateFlow<String?>(null)
    val dashboardMessage: StateFlow<String?> = _dashboardMessage.asStateFlow()

    fun setDashboardMessage(msg: String) {
        _dashboardMessage.value = msg
    }

    fun clearDashboardMessage() {
        _dashboardMessage.value = null
    }

    fun createNewEvent() {
        val newEvent = EventRequest(createdAt = System.currentTimeMillis())
        _eventRequest.value = newEvent
        _draftAvailability.value = emptySet()
        _currentEventId.value = newEvent.id
        
        // Auto-add current user as the host without verification since they are logged in
        val currentName = _currentUser.value ?: return
        addInviteeWithoutVerification(currentName)
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private val _templates = MutableStateFlow<List<EventTemplate>>(emptyList())
    val templates: StateFlow<List<EventTemplate>> = _templates.asStateFlow()

    fun createFromTemplate(template: EventTemplate) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val newEvent = EventRequest(
            eventEmoji = template.emoji,
            eventName = template.name,
            startDateMillis = 0L,
            endDateMillis = 0L,
            startHour = 0,
            endHour = 24,
            createdAt = System.currentTimeMillis()
        )
        _eventRequest.value = newEvent
        _draftAvailability.value = emptySet()
        _currentEventId.value = newEvent.id
        
        val currentName = _currentUser.value ?: return
        addInviteeWithoutVerification(currentName)
        
        template.invitees.forEach { inviteeName ->
            if (inviteeName != currentName) {
                addInviteeWithoutVerification(inviteeName)
            }
        }
    }

    fun hasMatchingTemplate(emoji: String, name: String, invitees: List<String>): Boolean {
        return _templates.value.any { 
            it.emoji == emoji && 
            it.name == name && 
            it.invitees.toSet() == invitees.toSet() 
        }
    }

    fun saveTemplate(template: EventTemplate) {
        val currentUsername = _currentUser.value ?: return
        if (!hasMatchingTemplate(template.emoji, template.name, template.invitees)) {
            val newList = _templates.value + template
            _templates.value = newList
            val serializedList = newList.map { 
                mapOf("id" to it.id, "emoji" to it.emoji, "name" to it.name, "invitees" to it.invitees)
            }
            db.collection("users").document(currentUsername).update("templates", serializedList)
        }
    }

    fun deleteTemplate(template: EventTemplate) {
        val currentUsername = _currentUser.value ?: return
        val newList = _templates.value.filter { it.id != template.id }
        _templates.value = newList
        val serializedList = newList.map { 
            mapOf("id" to it.id, "emoji" to it.emoji, "name" to it.name, "invitees" to it.invitees)
        }
        db.collection("users").document(currentUsername).update("templates", serializedList)
    }

    fun selectEvent(eventId: String) {
        _currentEventId.value = eventId
        val state = _events.value.find { it.request.id == eventId }
        if (state != null) {
            _eventRequest.value = state.request
        }
    }

    fun deleteEvent(eventId: String) {
        val currentUser = _currentUser.value ?: return
        val eventState = _events.value.find { it.request.id == eventId } ?: return
        
        val isHost = eventState.request.invitees.firstOrNull()?.name == currentUser

        if (isHost) {
            db.collection("events").document(eventId).delete()
            eventState.request.invitees.forEach { invitee ->
                if (invitee.name != currentUser) {
                    sendNotification(
                        recipient = invitee.name,
                        sender = currentUser,
                        type = "CANCELLED",
                        eventId = eventId,
                        eventName = eventState.request.eventName,
                        eventEmoji = eventState.request.eventEmoji,
                        message = "Host has cancelled ${eventState.request.eventName}"
                    )
                }
            }
        } else {
            val updatedInvitees = eventState.request.invitees.filter { it.name != currentUser }
            val updatedInviteeNames = eventState.request.inviteeNames.filter { it != currentUser }
            val updatedResponses = eventState.responses.toMutableMap()
            updatedResponses.remove(currentUser)
            
            val updatedRequest = eventState.request.copy(
                invitees = updatedInvitees,
                inviteeNames = updatedInviteeNames
            )
            val newState = eventState.copy(request = updatedRequest, responses = updatedResponses)
            
            db.collection("events").document(eventId).set(newState)
        }

        // Eagerly remove from local state to immediately dismiss the UI component
        _events.update { list -> list.filter { it.request.id != eventId } }

        if (_currentEventId.value == eventId) {
            _currentEventId.value = null
        }
    }

    fun addParticipantToExistingEvent(eventId: String, participantName: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = participantName.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Name cannot be empty")
            return
        }

        db.collection("users").document(trimmed).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val eventRef = db.collection("events").document(eventId)
                eventRef.get().addOnSuccessListener { eventDoc ->
                    val eventState = eventDoc.toObject(EventState::class.java)
                    if (eventState != null) {
                        val currentRequest = eventState.request
                        if (currentRequest.invitees.any { it.name.lowercase() == trimmed.lowercase() }) {
                            onResult(false, "Already a participant")
                            return@addOnSuccessListener
                        }
                        
                        val newColor = nextUniqueColorIndex(currentRequest.invitees.map { it.colorIndex })
                        val newInvitee = Invitee(name = trimmed, colorIndex = newColor, isHost = false)
                        val updatedInvitees = currentRequest.invitees + newInvitee
                        val updatedInviteeNames = currentRequest.inviteeNames + trimmed
                        
                        val updatedRequest = currentRequest.copy(
                            invitees = updatedInvitees,
                            inviteeNames = updatedInviteeNames
                        )
                        val updatedState = eventState.copy(request = updatedRequest)
                        
                        eventRef.set(updatedState).addOnSuccessListener {
                            val hostName = currentRequest.invitees.firstOrNull()?.name ?: _currentUser.value ?: "Organizer"
                            val proposedTimeframe = getProposedTimeframeString(currentRequest)
                            sendNotification(
                                recipient = trimmed,
                                sender = hostName,
                                type = "INVITATION",
                                eventId = eventId,
                                eventName = currentRequest.eventName,
                                eventEmoji = currentRequest.eventEmoji,
                                message = "$hostName invited you to ${currentRequest.eventName}! Proposed timeframe: $proposedTimeframe"
                            )
                            onResult(true, "Added $trimmed")
                        }.addOnFailureListener {
                            onResult(false, "Failed to update event in database")
                        }
                    } else {
                        onResult(false, "Event not found")
                    }
                }.addOnFailureListener {
                    onResult(false, "Failed to retrieve event details")
                }
            } else {
                onResult(false, "User '$trimmed' not found.")
            }
        }.addOnFailureListener {
            onResult(false, "Network error checking user.")
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

    fun toggleSelectedDateInRequest(dayMillis: Long) {
        _eventRequest.update { current ->
            val list = current.selectedDatesList
            val newList = if (dayMillis in list) list - dayMillis else list + dayMillis
            val minDate = newList.minOrNull() ?: 0L
            val maxDate = newList.maxOrNull() ?: 0L
            current.copy(
                selectedDatesList = newList,
                startDateMillis = minDate,
                endDateMillis = maxDate
            )
        }
        pruneDraftToSelectedRequestDates()
    }

    fun addSelectedDatesRangeInRequest(startMillis: Long, endMillis: Long, isSelecting: Boolean) {
        _eventRequest.update { current ->
            val rangeDays = mutableListOf<Long>()
            var curr = minOf(startMillis, endMillis)
            val end = maxOf(startMillis, endMillis)
            while (curr <= end) {
                rangeDays.add(curr)
                curr += 86400000L
            }
            val newList = if (isSelecting) {
                (current.selectedDatesList.toSet() + rangeDays).toList()
            } else {
                (current.selectedDatesList.toSet() - rangeDays.toSet()).toList()
            }
            val minDate = newList.minOrNull() ?: 0L
            val maxDate = newList.maxOrNull() ?: 0L
            current.copy(
                selectedDatesList = newList,
                startDateMillis = minDate,
                endDateMillis = maxDate
            )
        }
        pruneDraftToSelectedRequestDates()
    }

    fun pruneDraftToSelectedRequestDates() {
        val request = _eventRequest.value
        if (request.startDateMillis <= 0L) return
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        val allowedCols = request.selectedDatesList
            .map { ((it - request.startDateMillis) / 86400000L).toInt() }
            .toSet()
        _draftAvailability.update { current ->
            current.filter { idx -> (idx % config.cols) in allowedCols }.toSet()
        }
    }

    fun updateTimeRange(startHour: Int, endHour: Int) {
        _eventRequest.update {
            it.copy(startHour = startHour, endHour = endHour)
        }
    }

    fun updateDateOnlyMode(enabled: Boolean) {
        _eventRequest.update { it.copy(dateOnlyMode = enabled) }
    }

    fun updateStartDate(millis: Long) {
        _eventRequest.update { it.copy(startDateMillis = millis) }
    }

    fun updateEndDate(millis: Long) {
        _eventRequest.update { it.copy(endDateMillis = millis) }
    }

    fun addInvitee(name: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onResult?.invoke(false, "Name cannot be empty")
            return
        }

        // Verify user exists in Firestore
        db.collection("users").document(trimmed).get().addOnSuccessListener { snapshot: com.google.firebase.firestore.DocumentSnapshot ->
            if (snapshot.exists()) {
                var wasAdded = false
                _eventRequest.update { current ->
                    if (current.invitees.any { it.name.lowercase() == trimmed.lowercase() }) {
                        wasAdded = true
                        return@update current
                    }
                    val newColor = nextUniqueColorIndex(current.invitees.map { it.colorIndex })
                    val isHost = current.invitees.isEmpty()
                    val newInvitee = Invitee(name = trimmed, colorIndex = newColor, isHost = isHost)
                    val newInvitees = current.invitees + newInvitee
                    current.copy(
                        invitees = newInvitees,
                        inviteeNames = newInvitees.map { it.name }
                    )
                }
                if (wasAdded) {
                    onResult?.invoke(false, "Already added")
                } else {
                    onResult?.invoke(true, "Added $trimmed")
                }
            } else {
                onResult?.invoke(false, "User '$trimmed' not found.")
            }
        }.addOnFailureListener {
            onResult?.invoke(false, "Network error checking user.")
        }
    }

    fun addInviteeWithoutVerification(name: String, colorIndex: Int? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _eventRequest.update { current ->
            if (current.invitees.any { it.name == trimmed }) return@update current

            // First invitee is automatically the host
            val isHost = current.invitees.isEmpty()
            val assignedColors = current.invitees.map { it.colorIndex }
            val resolvedColor = if (colorIndex != null && colorIndex !in assignedColors) {
                colorIndex
            } else {
                nextUniqueColorIndex(assignedColors)
            }
            val newInvitee = Invitee(name = trimmed, colorIndex = resolvedColor, isHost = isHost)

            val newInvitees = current.invitees + newInvitee
            current.copy(
                invitees = newInvitees,
                inviteeNames = newInvitees.map { it.name }
            )
        }
    }

    private fun nextUniqueColorIndex(assigned: List<Int>): Int {
        val used = assigned.toSet()
        val available = CuratedParticipantColors.indices.filter { it !in used }
        return if (available.isNotEmpty()) available.first() else 0
    }

    fun removeInvitee(name: String) {
        _eventRequest.update { current ->
            val newInvitees = current.invitees.filterNot { it.name == name }
            current.copy(
                invitees = newInvitees,
                inviteeNames = newInvitees.map { it.name }
            )
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

    fun finalizeEventRequest() {
        val request = _eventRequest.value
        
        // Calculate initial organizerRestrictions for days that were not selected in the window:
        val initialRestrictions = mutableListOf<Long>()
        if (request.startDateMillis > 0L && request.endDateMillis > 0L && request.selectedDatesList.isNotEmpty()) {
            val selectedSet = request.selectedDatesList.toSet()
            var current = request.startDateMillis
            while (current <= request.endDateMillis) {
                if (current !in selectedSet) {
                    if (request.dateOnlyMode) {
                        initialRestrictions.add(current)
                    } else {
                        // Add all hour slots for this day as restricted
                        for (h in request.startHour until request.endHour) {
                            initialRestrictions.add(current + h * 3600000L)
                        }
                    }
                }
                current += 86400000L
            }
        }
        
        val newState = EventState(
            request = request,
            organizerRestrictions = initialRestrictions
        )
        
        val docRef = db.collection("events").document(request.id)
        docRef.get().addOnSuccessListener { document ->
            val exists = document.exists()
            docRef.set(newState).addOnSuccessListener {
                val hostName = request.invitees.firstOrNull()?.name ?: _currentUser.value ?: "Organizer"
                if (exists) {
                    val oldState = document.toObject(EventState::class.java)
                    val oldRequest = oldState?.request
                    if (oldRequest != null) {
                        val changed = oldRequest.eventName != request.eventName ||
                                oldRequest.eventEmoji != request.eventEmoji ||
                                oldRequest.startDateMillis != request.startDateMillis ||
                                oldRequest.endDateMillis != request.endDateMillis ||
                                oldRequest.startHour != request.startHour ||
                                oldRequest.endHour != request.endHour ||
                                oldRequest.dateOnlyMode != request.dateOnlyMode ||
                                oldRequest.selectedDatesList != request.selectedDatesList
                        
                        if (changed) {
                            request.invitees.forEach { invitee ->
                                if (invitee.name != hostName) {
                                    sendNotification(
                                        recipient = invitee.name,
                                        sender = hostName,
                                        type = "UPDATED",
                                        eventId = request.id,
                                        eventName = request.eventName,
                                        eventEmoji = request.eventEmoji,
                                        message = "$hostName has updated the details for ${request.eventName}. Please review your availability."
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val proposedTimeframe = getProposedTimeframeString(request)
                    request.invitees.forEach { invitee ->
                        if (invitee.name != hostName) {
                            sendNotification(
                                recipient = invitee.name,
                                sender = hostName,
                                type = "INVITATION",
                                eventId = request.id,
                                eventName = request.eventName,
                                eventEmoji = request.eventEmoji,
                                message = "$hostName invited you to ${request.eventName}! Proposed timeframe: $proposedTimeframe"
                            )
                        }
                    }
                }
            }
        }
    }

    fun finalizeDateOnlyEventRequest() {
        val request = _eventRequest.value.copy(dateOnlyMode = true)
        val hostName = _currentUser.value ?: "Organizer"
        
        // Host response contains their selectedDatesList
        val hostResponse = ParticipantResponse(hostName, request.selectedDatesList)
        val responses = mapOf(hostName to hostResponse)
        
        // Restrictions are unselected dates in the window:
        val initialRestrictions = mutableListOf<Long>()
        if (request.startDateMillis > 0L && request.endDateMillis > 0L && request.selectedDatesList.isNotEmpty()) {
            val selectedSet = request.selectedDatesList.toSet()
            var current = request.startDateMillis
            while (current <= request.endDateMillis) {
                if (current !in selectedSet) {
                    initialRestrictions.add(current)
                }
                current += 86400000L
            }
        }
        
        val newState = EventState(
            request = request,
            responses = responses,
            organizerRestrictions = initialRestrictions
        )
        
        val docRef = db.collection("events").document(request.id)
        docRef.get().addOnSuccessListener { document ->
            val exists = document.exists()
            docRef.set(newState).addOnSuccessListener {
                if (exists) {
                    val oldState = document.toObject(EventState::class.java)
                    val oldRequest = oldState?.request
                    if (oldRequest != null) {
                        val changed = oldRequest.eventName != request.eventName ||
                                oldRequest.eventEmoji != request.eventEmoji ||
                                oldRequest.startDateMillis != request.startDateMillis ||
                                oldRequest.endDateMillis != request.endDateMillis ||
                                oldRequest.dateOnlyMode != request.dateOnlyMode ||
                                oldRequest.selectedDatesList != request.selectedDatesList
                        
                        if (changed) {
                            request.invitees.forEach { invitee ->
                                if (invitee.name != hostName) {
                                    sendNotification(
                                        recipient = invitee.name,
                                        sender = hostName,
                                        type = "UPDATED",
                                        eventId = request.id,
                                        eventName = request.eventName,
                                        eventEmoji = request.eventEmoji,
                                        message = "$hostName has updated the details for ${request.eventName}. Please review your availability."
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val proposedTimeframe = getProposedTimeframeString(request)
                    request.invitees.forEach { invitee ->
                        if (invitee.name != hostName) {
                            sendNotification(
                                recipient = invitee.name,
                                sender = hostName,
                                type = "INVITATION",
                                eventId = request.id,
                                eventName = request.eventName,
                                eventEmoji = request.eventEmoji,
                                message = "$hostName invited you to ${request.eventName}! Proposed timeframe: $proposedTimeframe"
                            )
                        }
                    }
                }
            }
        }

        _selectedDates.value = emptySet()
        _draftAvailability.value = emptySet()
    }

    // ── View 2 state: Participant Availability Input ──────────────────────────

    private val _draftAvailability = MutableStateFlow<Set<Int>>(emptySet())
    val draftAvailability: StateFlow<Set<Int>> = _draftAvailability.asStateFlow()

    private val _selectedDates = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDates: StateFlow<Set<Long>> = _selectedDates.asStateFlow()

    fun toggleSelectedDate(dayMillis: Long) {
        val invitee = currentInvitee()
        val eventState = getCurrentEventState()
        if (invitee != null && !invitee.isHost && eventState != null) {
            val organizerRestrictions = eventState.organizerRestrictions
            if (organizerRestrictions.isNotEmpty()) {
                val dateOnly = eventState.request.dateOnlyMode
                if (dateOnly) {
                    if (dayMillis in organizerRestrictions) return
                } else {
                    val request = eventState.request
                    val restrictedSet = organizerRestrictions.toSet()
                    val allSlotsOnDayRestricted = (request.startHour until request.endHour).all { h ->
                        val slotTimestamp = dayMillis + h * 3600000L
                        slotTimestamp in restrictedSet
                    }
                    if (allSlotsOnDayRestricted) return
                }
            }
        }
        _selectedDates.update { current ->
            if (dayMillis in current) current - dayMillis else current + dayMillis
        }
    }

    fun addSelectedDatesRange(startMillis: Long, endMillis: Long, isSelecting: Boolean) {
        val invitee = currentInvitee()
        val eventState = getCurrentEventState()
        val rangeDays = mutableListOf<Long>()
        var curr = minOf(startMillis, endMillis)
        val end = maxOf(startMillis, endMillis)
        while (curr <= end) {
            var restricted = false
            if (invitee != null && !invitee.isHost && eventState != null) {
                val organizerRestrictions = eventState.organizerRestrictions
                if (organizerRestrictions.isNotEmpty()) {
                    val dateOnly = eventState.request.dateOnlyMode
                    if (dateOnly) {
                        if (curr in organizerRestrictions) restricted = true
                    } else {
                        val request = eventState.request
                        val restrictedSet = organizerRestrictions.toSet()
                        val allSlotsOnDayRestricted = (request.startHour until request.endHour).all { h ->
                            val slotTimestamp = curr + h * 3600000L
                            slotTimestamp in restrictedSet
                        }
                        if (allSlotsOnDayRestricted) restricted = true
                    }
                }
            }
            if (!restricted) {
                rangeDays.add(curr)
            }
            curr += 86400000L
        }
        _selectedDates.update { current ->
            if (isSelecting) {
                current + rangeDays
            } else {
                current - rangeDays.toSet()
            }
        }
    }

    fun clearSelectedDates() {
        _selectedDates.value = emptySet()
    }

    fun pruneDraftToSelectedDates() {
        val eventId = _currentEventId.value ?: return
        val request = _events.value.find { it.request.id == eventId }?.request ?: _eventRequest.value
        if (request.startDateMillis <= 0L) return
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        val allowedCols = _selectedDates.value
            .map { ((it - request.startDateMillis) / 86400000L).toInt() }
            .toSet()
        _draftAvailability.update { current ->
            current.filter { idx -> (idx % config.cols) in allowedCols }.toSet()
        }
    }

    fun setSelectedDatesFromDraft() {
        val eventId = _currentEventId.value ?: return
        val request = _events.value.find { it.request.id == eventId }?.request ?: _eventRequest.value
        if (request.startDateMillis <= 0L) return
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        _selectedDates.value = _draftAvailability.value
            .map { idx -> request.startDateMillis + (idx % config.cols) * 86400000L }
            .toSet()
    }

    fun isCurrentUserHost(): Boolean {
        val currentUser = _currentUser.value ?: return false
        val state = _events.value.find { it.request.id == _currentEventId.value }
        val request = state?.request ?: _eventRequest.value
        return request.invitees.firstOrNull()?.name == currentUser
    }

    fun currentInvitee(): Invitee? {
        val request = if (isCurrentUserHost()) _eventRequest.value else (_events.value.find { it.request.id == _currentEventId.value }?.request ?: _eventRequest.value)
        val name = _currentUser.value ?: return null
        return request.invitees.find { it.name == name }
    }

    fun totalParticipants(): Int {
        val request = if (isCurrentUserHost()) _eventRequest.value else (_events.value.find { it.request.id == _currentEventId.value }?.request ?: _eventRequest.value)
        return request.invitees.size
    }

    fun toggleCell(index: Int) {
        val eventState = getCurrentEventState()
        val invitee = currentInvitee()
        if (eventState != null && invitee != null && !invitee.isHost) {
            val request = eventState.request
            val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
            val timestamp = config.cellToTimestamp(index)
            if (timestamp in eventState.organizerRestrictions) return
        }
        _draftAvailability.update { current ->
            if (index in current) current - index else current + index
        }
    }

    fun paintCell(index: Int, isSelecting: Boolean) {
        val eventState = getCurrentEventState()
        val invitee = currentInvitee() ?: return
        
        val request = if (invitee.isHost) _eventRequest.value else (eventState?.request ?: _eventRequest.value)
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        if (index !in 0 until config.totalCells) return

        // Enforce organizer restrictions for non-hosts
        if (!invitee.isHost && eventState != null) {
            val timestamp = config.cellToTimestamp(index)
            if (timestamp in eventState.organizerRestrictions) return
        }
        
        _draftAvailability.update { current ->
            if (isSelecting) current + index else current - index
        }
    }

    fun loadDraftForCurrentParticipant() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        val request = if (invitee.isHost) _eventRequest.value else (state?.request ?: _eventRequest.value)
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        
        val existingResponse = state?.responses?.get(invitee.name)
        val timestamps = existingResponse?.availability ?: emptyList()
        
        _draftAvailability.value = timestamps.mapNotNull { config.timestampToCell(it) }.toSet()
    }

    fun saveCurrentDraft() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        val request = if (invitee.isHost) _eventRequest.value else (state?.request ?: _eventRequest.value)
        val responses = state?.responses ?: emptyMap()
        
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        
        val timestamps = _draftAvailability.value.map { config.cellToTimestamp(it) }.toList()
        
        val updatedResponses = responses + (invitee.name to ParticipantResponse(invitee.name, timestamps))
        
        var updatedRestrictions = state?.organizerRestrictions ?: emptyList()
        if (invitee.isHost) {
            val allCells = (0 until config.totalCells).toSet()
            val restrictedCells = allCells - _draftAvailability.value
            updatedRestrictions = restrictedCells.map { config.cellToTimestamp(it) }.toList()
        }
        
        val newState = EventState(
            request = request,
            responses = updatedResponses,
            organizerRestrictions = updatedRestrictions,
            finalCellIndex = state?.finalCellIndex
        )
        db.collection("events").document(eventId).set(newState).addOnSuccessListener {
            val previouslyAllResponded = request.invitees.isNotEmpty() && request.invitees.all { responses.containsKey(it.name) }
            val nowAllResponded = request.invitees.isNotEmpty() && request.invitees.all { updatedResponses.containsKey(it.name) }
            
            if (!previouslyAllResponded && nowAllResponded) {
                val hostName = request.invitees.firstOrNull { it.isHost }?.name ?: request.invitees.firstOrNull()?.name
                if (hostName != null) {
                    sendNotification(
                        recipient = hostName,
                        sender = invitee.name,
                        type = "RESPONSE_COMPLETE",
                        eventId = eventId,
                        eventName = request.eventName,
                        eventEmoji = request.eventEmoji,
                        message = "Everyone has responded to ${request.eventName}! The event is now in Action Needed status."
                    )
                }
            }
        }
    }

    fun submitAvailability() {
        saveCurrentDraft()
        _draftAvailability.value = emptySet()
    }

    fun clearDraftAvailability() {
        _draftAvailability.value = emptySet()
    }

    fun finalizeEventWithSpecificTimeSlots() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val request = _eventRequest.value.copy(dateOnlyMode = false)
        
        val config = GridConfig(request.startDateMillis, request.endDateMillis, request.startHour, request.endHour)
        val timestamps = _draftAvailability.value.map { config.cellToTimestamp(it) }.toList()
        val updatedResponses = mapOf(invitee.name to ParticipantResponse(invitee.name, timestamps))
        
        val allCells = (0 until config.totalCells).toSet()
        val restrictedCells = allCells - _draftAvailability.value
        val updatedRestrictions = restrictedCells.map { config.cellToTimestamp(it) }.toList()
        
        val newState = EventState(
            request = request,
            responses = updatedResponses,
            organizerRestrictions = updatedRestrictions
        )
        db.collection("events").document(eventId).set(newState)
        
        _draftAvailability.value = emptySet()
    }

    fun loadDateOnlyDraft() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        val timestamps = state?.responses?.get(invitee.name)?.availability ?: emptyList()
        _selectedDates.value = timestamps.toSet()
        _draftAvailability.value = emptySet()
    }

    fun submitDateOnlyAvailability() {
        val invitee = currentInvitee() ?: return
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId }
        
        val request = if (invitee.isHost) _eventRequest.value else (state?.request ?: _eventRequest.value)
        val responses = state?.responses ?: emptyMap()

        val timestamps = _selectedDates.value.toList()
        val updatedResponses = responses + (invitee.name to ParticipantResponse(invitee.name, timestamps))
        
        var updatedRestrictions = state?.organizerRestrictions ?: emptyList()
        if (invitee.isHost) {
            val allDates = mutableListOf<Long>()
            if (request.startDateMillis > 0L && request.endDateMillis > 0L) {
                var current = request.startDateMillis
                while (current <= request.endDateMillis) {
                    allDates.add(current)
                    current += 86400000L
                }
            }
            updatedRestrictions = allDates - _selectedDates.value
        }
        
        val newState = EventState(
            request = request,
            responses = updatedResponses,
            finalCellIndex = state?.finalCellIndex,
            organizerRestrictions = updatedRestrictions
        )
        db.collection("events").document(eventId).set(newState).addOnSuccessListener {
            val previouslyAllResponded = request.invitees.isNotEmpty() && request.invitees.all { responses.containsKey(it.name) }
            val nowAllResponded = request.invitees.isNotEmpty() && request.invitees.all { updatedResponses.containsKey(it.name) }
            
            if (!previouslyAllResponded && nowAllResponded) {
                val hostName = request.invitees.firstOrNull { it.isHost }?.name ?: request.invitees.firstOrNull()?.name
                if (hostName != null) {
                    sendNotification(
                        recipient = hostName,
                        sender = invitee.name,
                        type = "RESPONSE_COMPLETE",
                        eventId = eventId,
                        eventName = request.eventName,
                        eventEmoji = request.eventEmoji,
                        message = "Everyone has responded to ${request.eventName}! The event is now in Action Needed status."
                    )
                }
            }
        }

        _selectedDates.value = emptySet()
        _draftAvailability.value = emptySet()
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

    fun getConflictingTimestamps(): Set<Long> {
        val currentName = _currentUser.value ?: return emptySet()
        val currentEventId = _currentEventId.value
        
        val conflictingTimestamps = mutableSetOf<Long>()
        for (state in _events.value) {
            if (state.request.id == currentEventId) continue
            if (!state.request.inviteeNames.contains(currentName)) continue
            
            val finalIndex = state.finalCellIndex ?: continue
            val config = GridConfig(
                state.request.startDateMillis, 
                state.request.endDateMillis, 
                state.request.startHour, 
                state.request.endHour
            )
            conflictingTimestamps.add(config.cellToTimestamp(finalIndex))
        }
        return conflictingTimestamps
    }

    fun computeOptimalCell(gridConfig: GridConfig): Int? {
        val heatmap = computeHeatmap(gridConfig)
        return heatmap.maxByOrNull { it.value }?.key
    }

    fun setFinalEvent(cellIndex: Int, endCellIndex: Int? = null) {
        val eventId = _currentEventId.value ?: return
        val state = _events.value.find { it.request.id == eventId } ?: return
        val newState = state.copy(finalCellIndex = cellIndex, finalCellEndIndex = endCellIndex)
        db.collection("events").document(eventId).set(newState).addOnSuccessListener {
            val isDateOnly = state.request.dateOnlyMode
            val config = if (isDateOnly) {
                GridConfig(state.request.startDateMillis, state.request.endDateMillis, 0, 1)
            } else {
                GridConfig(state.request.startDateMillis, state.request.endDateMillis, state.request.startHour, state.request.endHour)
            }
            val dayStr = if (endCellIndex == null || cellIndex == endCellIndex) {
                config.cellToDay(cellIndex)
            } else {
                val sCol = cellIndex % config.cols
                val eCol = endCellIndex % config.cols
                val minCol = minOf(sCol, eCol)
                val maxCol = maxOf(sCol, eCol)
                if (minCol == maxCol) config.cellToDay(cellIndex)
                else "${config.dayLabels.getOrElse(minCol) { "?" }} → ${config.dayLabels.getOrElse(maxCol) { "?" }}"
            }
            val hourStr = if (isDateOnly) {
                "all day"
            } else if (endCellIndex == null || cellIndex == endCellIndex) {
                config.cellToHour(cellIndex)
            } else {
                val sRow = cellIndex / config.cols
                val eRow = endCellIndex / config.cols
                val minRow = minOf(sRow, eRow)
                val maxRow = maxOf(sRow, eRow)
                "${config.hourLabels.getOrElse(minRow) { "?" }} → ${config.hourLabels.getOrElse(maxRow) { "?" }}"
            }
            val timeLabel = if (isDateOnly) "$dayStr ($hourStr)" else "$dayStr, $hourStr"
            val hostName = state.request.invitees.firstOrNull()?.name ?: _currentUser.value ?: "Organizer"
            
            state.request.invitees.forEach { invitee ->
                sendNotification(
                    recipient = invitee.name,
                    sender = hostName,
                    type = "CONFIRMATION",
                    eventId = eventId,
                    eventName = state.request.eventName,
                    eventEmoji = state.request.eventEmoji,
                    message = "Event confirmed: ${state.request.eventName} is scheduled for $timeLabel!"
                )
            }
        }
    }
}
