package com.example.cheese.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.GridConfig
import com.example.cheese.data.Invitee
import com.example.cheese.data.ParticipantResponse
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val LightSageGreen = Color(0xFFE8F5E9)
private val MediumMintGreen = Color(0xFFC8E6C9)
private val VibrantEmeraldGreen = Color(0xFFA5D6A7)
private val DeepForestGreen = Color(0xFF81C784)

@Composable
private fun heatColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color.Transparent
        ratio <= 0.2f -> Color(0xFFE8F5E9) // Very light sage
        ratio <= 0.4f -> Color(0xFFC8E6C9) // Light mint
        ratio <= 0.6f -> Color(0xFFA5D6A7) // Soft mint
        ratio <= 0.8f -> Color(0xFF81C784) // Soft green
        else -> Color(0xFF66BB6A)          // Emerald pastel
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParticipantScreen(
    viewModel: ScheduleViewModel,
    onSubmitted: (String?) -> Unit,
    onEditEvent: () -> Unit,
    onBack: () -> Unit
) {
    val draftAvailability by viewModel.draftAvailability.collectAsState()
    val currentEventId by viewModel.currentEventId.collectAsState()
    val events by viewModel.events.collectAsState()
    
    // Find event state. If null (because not finalized), fallback to eventRequest
    val eventState = events.find { it.request.id == currentEventId }
    val eventRequest = eventState?.request ?: viewModel.eventRequest.collectAsState().value
    val organizerRestrictions = eventState?.organizerRestrictions ?: emptyList()
    
    val dateOnly = eventRequest.dateOnlyMode

    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour, dateOnly) {
        if (dateOnly) {
            // One row per day: cell index == day column, timestamps are start-of-day
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, 0, 1)
        } else {
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)
        }
    }

    val restrictedDays = remember(organizerRestrictions, eventRequest, dateOnly) {
        if (organizerRestrictions.isEmpty()) {
            emptySet<Long>()
        } else if (dateOnly) {
            organizerRestrictions.toSet()
        } else {
            val config = GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)
            val restrictedSet = organizerRestrictions.toSet()
            val days = mutableSetOf<Long>()
            if (eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L) {
                var current = eventRequest.startDateMillis
                while (current <= eventRequest.endDateMillis) {
                    val allSlotsOnDayRestricted = (eventRequest.startHour until eventRequest.endHour).all { h ->
                        val slotTimestamp = current + h * 3600000L
                        slotTimestamp in restrictedSet
                    }
                    if (allSlotsOnDayRestricted) {
                        days.add(current)
                    }
                    current += 86400000L
                }
            }
            days
        }
    }

    val restrictedCells = remember(organizerRestrictions, gridConfig, dateOnly) {
        if (dateOnly) {
            organizerRestrictions.mapNotNull { gridConfig.timestampToCell(it) }.toSet()
        } else {
            organizerRestrictions.mapNotNull { gridConfig.timestampToCell(it) }.toSet()
        }
    }
    
    val currentUser by viewModel.currentUser.collectAsState()
    val currentInvitee = viewModel.currentInvitee()
    val participantName = currentUser ?: "Unknown"
    val isNewEvent = events.none { it.request.id == currentEventId }
    val isOrganizer = currentUser != null && (isNewEvent || eventState?.request?.invitees?.firstOrNull()?.name == currentUser)
    val participantColor = remember(currentInvitee) {
        currentInvitee?.let { CuratedParticipantColors.getOrElse(it.colorIndex) { Color(0xFF6C5CE7) } } ?: Color(0xFF6C5CE7)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val responses = eventState?.responses ?: emptyMap()
    val hostName = eventRequest.invitees.firstOrNull()?.name
    val hostHasSubmitted = hostName != null && responses.containsKey(hostName)
    val heatmap = remember(responses) { viewModel.computeHeatmap(gridConfig) }
    val participantSubmittedCells = remember(responses, participantName, gridConfig) {
        responses[participantName]?.availability?.mapNotNull { gridConfig.timestampToCell(it) }?.toSet() ?: emptySet()
    }
    val totalParticipants = eventRequest.invitees.size
    val showFriendAvailabilityOption = remember(responses, participantName) {
        responses.keys.any { it != participantName }
    }

    val conflicts = remember(events) { viewModel.getConflictingTimestamps() }
    val conflictingCells = remember(conflicts, gridConfig) {
        conflicts.mapNotNull { gridConfig.timestampToCell(it) }.toSet()
    }

    LaunchedEffect(currentEventId, dateOnly) {
        if (dateOnly) {
            viewModel.loadDateOnlyDraft()
        } else {
            viewModel.loadDraftForCurrentParticipant()
            viewModel.setSelectedDatesFromDraft()
        }
    }

    // Participant whose submitted availability is being viewed in the bottom sheet
    var viewedInvitee by remember { mutableStateOf<Invitee?>(null) }
    var showFriendAvailabilities by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<String?>(null) }

    // ── Date-first flow ────────────────────────────────────────────────────────
    // Non-hosts first pick the days they are free (step 1), then drill into the
    // time grid for just those days (step 2). The host set the window already,
    // so they go straight to the grid.
    val selectedDates by viewModel.selectedDates.collectAsState()
    var scrollEnabled by remember { mutableStateOf(true) }
    var step by remember(currentEventId, isOrganizer, dateOnly) {
        mutableStateOf(if (isOrganizer && !dateOnly) 2 else 1)
    }

    // Full-grid column indices for the days picked in step 1 (sorted, in-window)
    val selectedColumns = remember(selectedDates, gridConfig, eventRequest.startDateMillis) {
        selectedDates
            .map { ((it - eventRequest.startDateMillis) / 86400000L).toInt() }
            .filter { it in 0 until gridConfig.cols }
            .sorted()
    }

    // Placing the CTA in the bottomBar slot of the Scaffold ensures that the
    // SnackbarHost floats above it, preventing UI obstruction.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            dateOnly && isOrganizer -> "Pick Possible Days"
                            dateOnly -> "Pick the Days You're Free"
                            isOrganizer -> "Set Possible Time Options"
                            step == 1 -> "Pick the Days You're Free"
                            else -> "Share Your Available Times"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isOrganizer && !dateOnly && step == 2) {
                            step = 1
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasSubmittedBefore = responses.containsKey(participantName)

                        if (!isOrganizer && !isNewEvent && !hostHasSubmitted) {
                            Button(
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Back to Dashboard")
                            }
                        } else if (dateOnly) {
                            // Date-only mode: the calendar selection IS the response
                            val noDates = selectedDates.isEmpty()
                            Button(
                                onClick = {
                                    scope.launch {
                                        val msg = if (noDates && !isOrganizer) "Marked as not available" else "Dates submitted for $participantName"
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                    viewModel.submitDateOnlyAvailability()
                                    val dashboardMsg = if (isOrganizer && !hasSubmittedBefore) "Event Created!" else if (!isOrganizer) "Availabilities Sent!" else "Availability Updated!"
                                    onSubmitted(dashboardMsg)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = if (isOrganizer) !noDates else true,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text(
                                    text = if (isOrganizer) {
                                        if (!hasSubmittedBefore) "Send Event" else "Submit Dates"
                                    } else if (noDates) {
                                        "Not Available"
                                    } else {
                                        "Submit Dates"
                                    }
                                )
                            }
                        } else if (!isOrganizer && step == 1) {
                            // Step 1 CTA: advance to the time grid, or mark not available
                            val noDates = selectedDates.isEmpty()
                            Button(
                                onClick = {
                                    if (noDates) {
                                        scope.launch { snackbarHostState.showSnackbar("Marked as not available") }
                                        viewModel.submitAvailability()
                                        val dashboardMsg = if (isOrganizer && !hasSubmittedBefore) "Event Created!" else if (!isOrganizer) "Availabilities Sent!" else "Availability Updated!"
                                        onSubmitted(dashboardMsg)
                                    } else {
                                        viewModel.pruneDraftToSelectedDates()
                                        step = 2
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text(if (noDates) "Not Available" else "Next: Pick Times")
                            }
                        } else {
                            val isEmpty = draftAvailability.isEmpty()
                            Button(
                                onClick = {
                                    scope.launch {
                                        val msg = if (isEmpty && !isOrganizer) "Marked as not available" else "Availability submitted for $participantName"
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                    viewModel.submitAvailability()
                                    val dashboardMsg = if (isOrganizer && !hasSubmittedBefore) "Event Created!" else if (!isOrganizer) "Availabilities Sent!" else "Availability Updated!"
                                    onSubmitted(dashboardMsg)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = if (isOrganizer) !isEmpty else true,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text(
                                    text = if (isOrganizer) {
                                        if (!hasSubmittedBefore) "Send Event" else "Submit Availability"
                                    } else if (isEmpty) {
                                        "Not Available"
                                    } else {
                                        "Submit Availability"
                                    }
                                )
                            }
                        }
                    }

                    if (isOrganizer && !isNewEvent) {
                        TextButton(
                            onClick = {
                                currentEventId?.let { id ->
                                    viewModel.deleteEvent(id)
                                    onBack()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Delete Event")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
 
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            if (!isOrganizer && !isNewEvent && !hostHasSubmitted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏳", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Waiting for Host Availability",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The organizer has not yet submitted their availability. You will be able to share your available times once they have completed their selection.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (dateOnly || (!isOrganizer && step == 1)) {
                // ── Step 1: pick free days within the host's window ───────────
                Surface(
                    color = Color(0xFFF3E5F5), // Lavender-purple background
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = remember(dateOnly) {
                            buildAnnotatedString {
                                append("💡 ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Tap")
                                }
                                if (dateOnly) {
                                    append(" days that work for you.")
                                } else {
                                    append(" days you are free (times are picked next).")
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF6A1B9A) // Deep amethyst-purple text
                    )
                }

                if (dateOnly && showFriendAvailabilityOption) {
                    // Show Friend Availabilities Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Friend Availabilities",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = showFriendAvailabilities,
                            onCheckedChange = { 
                                showFriendAvailabilities = it
                                if (!it) selectedFriend = null
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showFriendAvailabilities) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select a Friend to see when they’re available",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6C5CE7),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                                )
                                LegendRow(
                                    invitees = eventRequest.invitees,
                                    currentUser = currentUser,
                                    selectedFriend = selectedFriend,
                                    onFriendSelected = { selectedFriend = it }
                                )
                            }
                        } else if (dateOnly) {
                            // Gradient legend (heatmap legend)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("0%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                val legendColors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    Color(0xFFE8F5E9),
                                    Color(0xFFC8E6C9),
                                    Color(0xFFA5D6A7),
                                    Color(0xFF81C784),
                                    Color(0xFF66BB6A)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    legendColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(color, RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                                Text("100%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.Transparent)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.tertiary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Text("Selected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
 
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    MultiSelectMonthCalendar(
                        selectedDayMillis = selectedDates,
                        minDateMillis = eventRequest.startDateMillis,
                        maxDateMillis = eventRequest.endDateMillis,
                        restrictedDaysMillis = if (!isOrganizer) restrictedDays else emptySet(),
                        onDayToggled = { day ->
                            viewModel.toggleSelectedDate(day)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onRangeDragged = { start, end, isSelecting ->
                            viewModel.addSelectedDatesRange(start, end, isSelecting)
                        },
                        onDragStateChanged = { interacting ->
                            scrollEnabled = !interacting
                        },
                        gridConfig = if (dateOnly) gridConfig else null,
                        heatmap = heatmap,
                        totalParticipants = totalParticipants,
                        invitees = eventRequest.invitees,
                        responses = responses,
                        showFriendAvailabilities = showFriendAvailabilities,
                        participantName = participantName,
                        selectedFriend = selectedFriend
                    )
                }
            } else {
                // ── Step 2 (or host): time grid ────────────────────────────────
                // Instruction banner
                Surface(
                    color = Color(0xFFF3E5F5), // Lavender-purple background
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = remember {
                            buildAnnotatedString {
                                append("💡 ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Tap")
                                }
                                append(" slots or ")
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("drag")
                                }
                                append(" to paint availability.")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF6A1B9A) // Deep amethyst-purple text
                    )
                }

                if (showFriendAvailabilityOption) {
                    // Show Friend Availabilities Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Friend Availabilities",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = showFriendAvailabilities,
                            onCheckedChange = { 
                                showFriendAvailabilities = it
                                if (!it) selectedFriend = null
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showFriendAvailabilities) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select a Friend to see when they’re available",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6C5CE7),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                                )
                                LegendRow(
                                    invitees = eventRequest.invitees,
                                    currentUser = currentUser,
                                    selectedFriend = selectedFriend,
                                    onFriendSelected = { selectedFriend = it }
                                )
                            }
                        } else {
                            // Gradient legend (heatmap legend)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("0%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                val legendColors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    Color(0xFFE8F5E9),
                                    Color(0xFFC8E6C9),
                                    Color(0xFFA5D6A7),
                                    Color(0xFF81C784),
                                    Color(0xFF66BB6A)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    legendColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(color, RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                                Text("100%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.Transparent)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.tertiary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                                Text("Selected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                val context = androidx.compose.ui.platform.LocalContext.current
                val sharedPrefs = remember { context.getSharedPreferences("cheese_prefs", android.content.Context.MODE_PRIVATE) }
                var showDragHint by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_drag_hint", true)) }
                val verticalScrollState = rememberScrollState()

                LaunchedEffect(verticalScrollState.maxValue) {
                    if (verticalScrollState.maxValue > 0) {
                        verticalScrollState.scrollTo(verticalScrollState.maxValue)
                    }
                }

                // Scrollable grid area (Vertical AND Horizontal scrolling to support dynamic days)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AvailabilityGrid(
                        gridConfig = gridConfig,
                        selectedCells = draftAvailability,
                        heatmap = heatmap,
                        conflictingCells = conflictingCells,
                        totalParticipants = totalParticipants,
                        restrictedCells = if (!isOrganizer) restrictedCells else emptySet(),
                        submittedSelectedCells = participantSubmittedCells,
                        invitees = eventRequest.invitees,
                        responses = responses,
                        showFriendAvailabilities = showFriendAvailabilities,
                        participantName = participantName,
                        selectedFriend = selectedFriend,
                        onCellToggled = { index ->
                            viewModel.toggleCell(index)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onCellPainted = { index, isSelecting ->
                            viewModel.paintCell(index, isSelecting)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        // Non-hosts only see the day columns they picked in step 1
                        visibleCols = if (!isOrganizer && selectedColumns.isNotEmpty()) selectedColumns else null,
                        scrollEnabled = scrollEnabled,
                        onDragStateChanged = { interacting ->
                            scrollEnabled = !interacting
                        },
                        verticalScrollState = verticalScrollState
                    )

                    if (showDragHint) {
                        DragGestureHintOverlay(
                            onDismiss = { showDragHint = false },
                            onNeverShowAgain = {
                                context.getSharedPreferences("cheese_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("show_drag_hint", false).apply()
                                showDragHint = false
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Read-only view of another participant's submitted availability ────────
    val viewed = viewedInvitee
    if (viewed != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val viewedCells = remember(viewed, responses, gridConfig) {
            (responses[viewed.name]?.availability ?: emptyList())
                .mapNotNull { gridConfig.timestampToCell(it) }
                .toSet()
        }
        ModalBottomSheet(
            onDismissRequest = { viewedInvitee = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(CuratedParticipantColors[viewed.colorIndex], CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewed.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (viewedCells.isEmpty()) {
                    Text(
                        text = "${viewed.name} hasn't submitted any availability yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                } else {
                    Text(
                        text = "Submitted availability (read-only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AvailabilityGrid(
                            gridConfig = gridConfig,
                            selectedCells = viewedCells,
                            heatmap = emptyMap(),
                            conflictingCells = emptySet(),
                            totalParticipants = totalParticipants,
                            restrictedCells = emptySet(),
                            onCellToggled = {},
                            onCellPainted = { _, _ -> },
                            readOnly = true,
                            selectionColor = CuratedParticipantColors[viewed.colorIndex]
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dynamic Availability Grid.
 * - Width adapts to the number of columns.
 * - Supports both tap (toggle) and drag (paint) interactions.
 */
@Composable
fun AvailabilityGrid(
    gridConfig: GridConfig,
    selectedCells: Set<Int>,
    heatmap: Map<Int, Int>,
    conflictingCells: Set<Int>,
    totalParticipants: Int,
    restrictedCells: Set<Int> = emptySet(),
    submittedSelectedCells: Set<Int> = emptySet(),
    invitees: List<Invitee> = emptyList(),
    responses: Map<String, ParticipantResponse> = emptyMap(),
    showFriendAvailabilities: Boolean = false,
    participantName: String = "",
    onCellToggled: (Int) -> Unit,
    onCellPainted: (Int, Boolean) -> Unit,
    readOnly: Boolean = false,
    selectionColor: Color = VibrantEmeraldGreen,
    visibleCols: List<Int>? = null,
    scrollEnabled: Boolean = true,
    onDragStateChanged: (Boolean) -> Unit = {},
    selectedFriend: String? = null,
    verticalScrollState: ScrollState = rememberScrollState()
) {
    val currentSelectedCells by rememberUpdatedState(selectedCells)
    val currentOnCellPainted by rememberUpdatedState(onCellPainted)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)

    val showCounts = remember(responses, participantName) {
        responses.keys.any { it != participantName }
    }

    val labelColWidth: Dp = 48.dp
    val cellHeight: Dp = 28.dp
    val headerHeight: Dp = 24.dp
    val horizontalScrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Columns (days) actually rendered. Cell indices stay in full-grid space so
    // timestamps remain correct even when only a subset of days is shown.
    val colList = visibleCols ?: (0 until gridConfig.cols).toList()

    val gapWidth = 16.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = colList.size.coerceAtLeast(1)
        val cellWidth: Dp = maxOf((maxWidth - labelColWidth) / cols, 56.dp)

        val totalGridWidth = with(density) {
            var w = cellWidth.toPx() * cols
            colList.forEachIndexed { index, colIdx ->
                if (index < colList.size - 1 && colList[index + 1] != colIdx + 1) {
                    w += gapWidth.toPx()
                }
            }
            w.toDp()
        }

        // We need precomputed starting X coordinates for each column to handle tap/drag gesture offsets correctly when gaps are present.
        val colStartX = remember(colList, cellWidth, density) {
            with(density) {
                var currentX = 0f
                val cellWidthPx = cellWidth.toPx()
                val gapWidthPx = gapWidth.toPx()
                FloatArray(colList.size) { index ->
                    val x = currentX
                    val colIdx = colList[index]
                    currentX += cellWidthPx
                    if (index < colList.size - 1 && colList[index + 1] != colIdx + 1) {
                        currentX += gapWidthPx
                    }
                    x
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Day headers row (Fixed vertically, scrolls horizontally in sync with cells)
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(labelColWidth))

                Row(
                    modifier = Modifier
                        .horizontalScroll(horizontalScrollState, enabled = scrollEnabled)
                        .width(totalGridWidth)
                        .height(headerHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colList.forEachIndexed { index, colIdx ->
                        Text(
                            text = gridConfig.dayLabels.getOrElse(colIdx) { "?" },
                            modifier = Modifier.width(cellWidth),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        if (index < colList.size - 1 && colList[index + 1] != colIdx + 1) {
                            Spacer(modifier = Modifier.width(gapWidth))
                        }
                    }
                }
            }

            // 2. Hour labels column & scrollable cells (scrollable vertically)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(verticalScrollState, enabled = scrollEnabled)
            ) {
                // Fixed hour-label column (left edge, scrolls vertically but not horizontally)
                Column(modifier = Modifier.width(labelColWidth)) {
                    gridConfig.hourLabels.forEach { label ->
                        Box(
                            modifier = Modifier
                                .height(cellHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                // Scrollable cells Box
                Box(
                    modifier = Modifier
                        .horizontalScroll(horizontalScrollState, enabled = scrollEnabled)
                        .width(totalGridWidth)
                        .then(
                            if (readOnly) Modifier
                            else Modifier
                                .pointerInput(gridConfig, cellWidth, colList, restrictedCells, colStartX) {
                                    val cellWidthPx = cellWidth.toPx()
                                    val cellHeightPx = cellHeight.toPx()

                                    fun cellAt(offset: Offset): Int {
                                        var visIdx = 0
                                        for (i in colStartX.indices) {
                                            if (offset.x >= colStartX[i] && offset.x <= colStartX[i] + cellWidthPx) {
                                                visIdx = i
                                                break
                                            } else if (i < colStartX.size - 1 && offset.x < colStartX[i + 1]) {
                                                visIdx = i
                                                break
                                            } else if (i == colStartX.size - 1) {
                                                visIdx = i
                                            }
                                        }
                                        val col = colList[visIdx]
                                        val row = (offset.y / cellHeightPx)
                                            .toInt().coerceIn(0, gridConfig.rows - 1)
                                        return gridConfig.cellIndex(row, col)
                                    }

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val firstIdx = cellAt(down.position)
                                        val isSelecting = firstIdx !in currentSelectedCells
                                        var lastPainted = -1

                                        if (down.type == PointerType.Mouse) {
                                            if (firstIdx !in restrictedCells) {
                                                currentOnCellPainted(firstIdx, isSelecting)
                                                lastPainted = firstIdx
                                            }
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) {
                                                    break
                                                }
                                                val idx = cellAt(change.position)
                                                if (idx != lastPainted && idx !in restrictedCells) {
                                                    currentOnCellPainted(idx, isSelecting)
                                                    lastPainted = idx
                                                }
                                                change.consume()
                                            }
                                        } else {
                                            var isTap = false
                                            val isLongPress = withTimeoutOrNull(400L) {
                                                var result = true
                                                while (result) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) {
                                                        isTap = true
                                                        result = false
                                                    } else {
                                                        val distance = (change.position - down.position).getDistance()
                                                        if (distance > 24f) {
                                                            result = false
                                                        }
                                                    }
                                                }
                                                false
                                            } ?: true

                                            if (isLongPress) {
                                                currentOnDragStateChanged(true)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (firstIdx !in restrictedCells) {
                                                    currentOnCellPainted(firstIdx, isSelecting)
                                                    lastPainted = firstIdx
                                                }

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) {
                                                        break
                                                    }
                                                    val idx = cellAt(change.position)
                                                    if (idx != lastPainted && idx !in restrictedCells) {
                                                        currentOnCellPainted(idx, isSelecting)
                                                        lastPainted = idx
                                                    }
                                                    change.consume()
                                                }
                                                currentOnDragStateChanged(false)
                                            } else if (isTap) {
                                                if (firstIdx !in restrictedCells) {
                                                    onCellToggled(firstIdx)
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                ) {
                    Column {
                        gridConfig.hourLabels.forEachIndexed { rowIdx, _ ->
                            Row(modifier = Modifier.height(cellHeight)) {
                                colList.forEachIndexed { index, colIdx ->
                                    val cellIndex = gridConfig.cellIndex(rowIdx, colIdx)
                                    val isSelected = cellIndex in selectedCells
                                    val isConflicting = cellIndex in conflictingCells
                                    val isRestricted = cellIndex in restrictedCells
                                    val count = heatmap[cellIndex] ?: 0

                                    val wasSelectedInSubmitted = cellIndex in submittedSelectedCells
                                    val projectedCount = if (readOnly) count else (count - (if (wasSelectedInSubmitted) 1 else 0) + (if (isSelected) 1 else 0))
                                    val safeTotal = totalParticipants.coerceAtLeast(1)
                                    val projectedRatio = projectedCount.toFloat() / safeTotal

                                    val isSelectedFriendAvailable = if (showFriendAvailabilities && selectedFriend != null) {
                                        if (selectedFriend == participantName) {
                                            isSelected
                                        } else {
                                            responses[selectedFriend]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                        }
                                    } else false

                                    val selectedFriendColor = if (showFriendAvailabilities && selectedFriend != null) {
                                        val friendInvitee = invitees.find { it.name == selectedFriend }
                                        friendInvitee?.let { CuratedParticipantColors.getOrElse(it.colorIndex) { Color.Gray } } ?: Color.Gray
                                    } else null

                                    val bg = when {
                                        isRestricted -> Color(0xFFE5E7EB)
                                        showFriendAvailabilities && selectedFriendColor != null -> {
                                            if (isSelectedFriendAvailable) selectedFriendColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                        readOnly && isSelected -> selectionColor
                                        showFriendAvailabilities -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        projectedCount > 0 -> heatColor(projectedRatio)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }

                                    val availableInvitees = remember(invitees, responses, selectedCells, cellIndex, showFriendAvailabilities, readOnly) {
                                        if (!showFriendAvailabilities) emptyList()
                                        else invitees.filter { invitee ->
                                            if (!readOnly && invitee.name == participantName) {
                                                cellIndex in selectedCells
                                            } else {
                                                responses[invitee.name]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .fillMaxHeight()
                                            .padding(1.5.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bg)
                                            .then(
                                                if (!readOnly && isSelected && !isRestricted && selectedFriend == null) {
                                                    Modifier.border(2.dp, Color(0xFF2E7D32), RoundedCornerShape(6.dp))
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .drawWithContent {
                                                drawContent()
                                                if (isRestricted) {
                                                    drawLine(
                                                        color = Color(0xFF9CA3AF).copy(alpha = 0.6f),
                                                        start = Offset(0f, size.height),
                                                        end = Offset(size.width, 0f),
                                                        strokeWidth = 1.5.dp.toPx()
                                                    )
                                                } else if (isConflicting) {
                                                    drawRect(Color.Gray.copy(alpha = 0.4f))
                                                    drawLine(
                                                        color = Color.DarkGray.copy(alpha = 0.6f),
                                                        start = Offset(0f, 0f),
                                                        end = Offset(size.width, size.height),
                                                        strokeWidth = 2.dp.toPx()
                                                    )
                                                    drawLine(
                                                        color = Color.DarkGray.copy(alpha = 0.6f),
                                                        start = Offset(size.width, 0f),
                                                        end = Offset(0f, size.height),
                                                        strokeWidth = 2.dp.toPx()
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (showFriendAvailabilities && !isRestricted && selectedFriend == null) {
                                            val dotSize = when {
                                                availableInvitees.size > 6 -> 5.dp
                                                availableInvitees.size > 4 -> 7.dp
                                                else -> 9.dp
                                            }
                                            val spacing = when {
                                                availableInvitees.size > 6 -> (-2).dp
                                                availableInvitees.size > 4 -> 1.dp
                                                else -> 3.dp
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                availableInvitees.forEach { invitee ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(dotSize)
                                                            .background(CuratedParticipantColors.getOrElse(invitee.colorIndex) { Color.Gray }, CircleShape)
                                                    )
                                                }
                                            }
                                        } else if (projectedCount > 0 && !isRestricted && !readOnly && showCounts) {
                                            Text(
                                                text = "$projectedCount",
                                                fontSize = 9.sp,
                                                color = Color(0xFF1B5E20),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (index < colList.size - 1 && colList[index + 1] != colIdx + 1) {
                                        Spacer(modifier = Modifier.width(gapWidth))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    invitees: List<Invitee>,
    currentUser: String?,
    selectedFriend: String? = null,
    onFriendSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        invitees.forEach { invitee ->
            val displayName = if (invitee.name == currentUser) "You" else invitee.name
            val isSelected = selectedFriend == invitee.name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (isSelected) {
                            onFriendSelected(null)
                        } else {
                            onFriendSelected(invitee.name)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(CuratedParticipantColors.getOrElse(invitee.colorIndex) { Color.Gray }, CircleShape)
                )
                Text(
                    text = displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DragGestureHintOverlay(
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.65f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "drag_hint")
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                -40f at 0
                -40f at 550
                60f at 1650
                60f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "y_offset"
    )
    
    val handScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                1.0f at 0
                0.82f at 400
                0.85f at 550
                0.85f at 1650
                1.0f at 1850
                1.0f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "hand_scale"
    )
    
    val touchScale by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0.0f at 0
                1.2f at 400
                1.0f at 550
                1.0f at 1650
                0.0f at 1850
                0.0f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "touch_scale"
    )
    
    val touchAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0.0f at 0
                0.7f at 400
                0.7f at 1650
                0.0f at 1850
                0.0f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "touch_alpha"
    )
    
    val handAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0.0f at 0
                1.0f at 200
                1.0f at 1650
                0.0f at 1950
                0.0f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "hand_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        translationY = yOffset.dp.toPx()
                        alpha = handAlpha
                        scaleX = handScale
                        scaleY = handScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Pointing hand custom canvas drawing
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val centerX = width * 0.37f
                    val centerY = height * 0.07f
                    
                    // Touch point pulse ripple indicator
                    if (touchAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFF6C5CE7).copy(alpha = touchAlpha * 0.3f),
                            radius = 24.dp.toPx() * touchScale,
                            center = Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = Color(0xFF6C5CE7).copy(alpha = touchAlpha),
                            radius = 8.dp.toPx() * touchScale,
                            center = Offset(centerX, centerY)
                        )
                    }
                    
                    // Path outline for the pointing hand
                    val path = Path().apply {
                        // Start at outer thumb base
                        moveTo(width * 0.28f, height * 0.78f)
                        
                        // Outer thumb edge to thumb tip
                        quadraticTo(width * 0.12f, height * 0.62f, width * 0.14f, height * 0.44f)
                        // Thumb tip (goes up more)
                        quadraticTo(width * 0.14f, height * 0.32f, width * 0.22f, height * 0.38f)
                        // Crotch between thumb and index
                        quadraticTo(width * 0.26f, height * 0.54f, width * 0.33f, height * 0.50f)
                        
                        // Index left edge
                        lineTo(width * 0.33f, height * 0.12f)
                        // Index tip
                        quadraticTo(width * 0.38f, height * 0.02f, width * 0.43f, height * 0.12f)
                        // Index right edge
                        lineTo(width * 0.43f, height * 0.50f)
                        
                        // Crease to middle
                        lineTo(width * 0.45f, height * 0.50f)
                        // Middle left edge
                        lineTo(width * 0.45f, height * 0.22f)
                        // Middle tip
                        quadraticTo(width * 0.50f, height * 0.12f, width * 0.55f, height * 0.22f)
                        // Middle right edge
                        lineTo(width * 0.55f, height * 0.50f)
                        
                        // Crease to ring
                        lineTo(width * 0.57f, height * 0.50f)
                        // Ring left edge
                        lineTo(width * 0.57f, height * 0.28f)
                        // Ring tip
                        quadraticTo(width * 0.62f, height * 0.18f, width * 0.67f, height * 0.28f)
                        // Ring right edge
                        lineTo(width * 0.67f, height * 0.50f)
                        
                        // Crease to pinky
                        lineTo(width * 0.69f, height * 0.50f)
                        // Pinky left edge
                        lineTo(width * 0.69f, height * 0.38f)
                        // Pinky tip
                        quadraticTo(width * 0.74f, height * 0.28f, width * 0.79f, height * 0.38f)
                        // Pinky right edge
                        lineTo(width * 0.79f, height * 0.60f)
                        
                        // Palm right outer edge (slopes slightly inwards to the wrist, no bump!)
                        lineTo(width * 0.76f, height * 0.78f)
                        
                        // Smooth U-shaped bottom of palm/wrist back to thumb base
                        quadraticTo(width * 0.52f, height * 1.05f, width * 0.28f, height * 0.78f)
                        
                        close()
                    }
                    
                    // Draw hand shadow (slightly offset to the bottom-right)
                    drawContext.canvas.save()
                    drawContext.canvas.translate(3.dp.toPx(), 3.dp.toPx())
                    drawPath(
                        path = path,
                        color = Color.Black.copy(alpha = 0.12f),
                        style = Fill
                    )
                    drawContext.canvas.restore()
                    
                    // Draw hand body
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Fill
                    )
                    
                    // Draw hand outline
                    drawPath(
                        path = path,
                        color = Color(0xFF6C5CE7),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
            
            Text(
                text = "Press & Drag Downwards\nto select multiple slots",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Tap anywhere to start",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onNeverShowAgain) {
                Text(
                    text = "Never show me again",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
