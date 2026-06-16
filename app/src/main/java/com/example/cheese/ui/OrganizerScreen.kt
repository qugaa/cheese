package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import com.example.cheese.data.DateOffset
import com.example.cheese.data.EventTemplate
import com.example.cheese.data.GridConfig
import com.example.cheese.data.ParticipantResponse
import com.example.cheese.data.Invitee
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

val COMMON_EMOJIS = listOf("📅", "🍔", "🏀", "🎮", "🍻", "🎬", "📚", "✈️")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun OrganizerScreen(
    viewModel: ScheduleViewModel,
    onRequestSent: () -> Unit,
    onBack: () -> Unit
) {
    val eventRequest by viewModel.eventRequest.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var scrollEnabled by remember { mutableStateOf(true) }
    var inviteeInput by remember { mutableStateOf("") }
    val hasMatching = viewModel.hasMatchingTemplate(
        eventRequest.eventEmoji, 
        eventRequest.eventName, 
        eventRequest.invitees.map { it.name }
    )
    var saveAsTemplate by remember(hasMatching) { mutableStateOf(hasMatching) }

    var emojiList by remember { mutableStateOf(COMMON_EMOJIS) }
    var showAddEmojiDialog by remember { mutableStateOf(false) }
    var emojiToDelete by remember { mutableStateOf<String?>(null) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }

    val isEventNameValid = eventRequest.eventName.isNotBlank()
    val isDateRangeValid = eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L
    val hasInvitees = eventRequest.invitees.isNotEmpty()
    val isFormValid = isEventNameValid && isDateRangeValid && hasInvitees

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
        topBar = {
            TopAppBar(
                title = { Text("Schedule New Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = scrollEnabled),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Card 1: Event Context & Emoji ────────────────────────────────
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Emoji Picker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojiList.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (eventRequest.eventEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .combinedClickable(
                                        onClick = { viewModel.updateEventEmoji(emoji) },
                                        onLongClick = {
                                            if (emojiList.size > 1) {
                                                emojiToDelete = emoji
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                        
                        // Add custom emoji button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showAddEmojiDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Custom Emoji")
                        }
                    }

                    if (emojiToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { emojiToDelete = null },
                            title = { Text("Remove Emoji") },
                            text = { Text("Are you sure you want to remove $emojiToDelete from the list?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        emojiList = emojiList.filter { it != emojiToDelete }
                                        emojiToDelete = null
                                    }
                                ) {
                                    Text("Remove")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { emojiToDelete = null }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showAddEmojiDialog) {
                        var newEmoji by remember { mutableStateOf("") }
                        val isEmojiOnly = newEmoji.isNotBlank() && newEmoji.all {
                            it.isSurrogate() || it.category == kotlin.text.CharCategory.OTHER_SYMBOL || it.category == kotlin.text.CharCategory.MATH_SYMBOL || it.category == kotlin.text.CharCategory.NON_SPACING_MARK || it.category == kotlin.text.CharCategory.ENCLOSING_MARK
                        }
                        AlertDialog(
                            onDismissRequest = { showAddEmojiDialog = false },
                            title = { Text("Add Custom Emoji") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = newEmoji,
                                        onValueChange = { input ->
                                            if (getGraphemeCount(input) <= 1) {
                                                newEmoji = input
                                            }
                                        },
                                        singleLine = true,
                                        placeholder = { Text("Type an emoji...") },
                                        isError = newEmoji.isNotBlank() && !isEmojiOnly,
                                        supportingText = {
                                            if (newEmoji.isNotBlank() && !isEmojiOnly) {
                                                Text("Only emojis are allowed")
                                            }
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (isEmojiOnly) {
                                            emojiList = emojiList + newEmoji
                                            viewModel.updateEventEmoji(newEmoji)
                                        }
                                        showAddEmojiDialog = false
                                    },
                                    enabled = isEmojiOnly
                                ) {
                                    Text("Add")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { showAddEmojiDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = eventRequest.eventName,
                        onValueChange = { 
                            if (it.length <= 25) {
                                viewModel.updateEventName(it)
                            }
                        },
                        label = {
                            Row {
                                Text("Event Name")
                                Text(" *", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        placeholder = { Text("e.g., Team Retrospective") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        isError = showValidationErrors && !isEventNameValid,
                        supportingText = {
                            if (showValidationErrors && !isEventNameValid) {
                                Text("Event name is required", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
            // ── Card 2: Invitee Management & Color Customization ─────────────
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Invite People",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (eventRequest.invitees.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            eventRequest.invitees.forEach { invitee ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(invitee.name)
                                            if (invitee.isHost) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                SuggestionChip(
                                                    onClick = { },
                                                    label = { Text("Host", fontSize = 10.sp) },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(CuratedParticipantColors[invitee.colorIndex], CircleShape)
                                        )
                                    },
                                    trailingIcon = if (!invitee.isHost) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${invitee.name}",
                                                modifier = Modifier.clickable {
                                                    viewModel.removeInvitee(invitee.name)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            )
                                        }
                                    } else null,
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                    if (friends.isNotEmpty()) {
                        Text("Saved Friends", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(friends, key = { it.id }) { friend ->
                                val isAdded = eventRequest.invitees.any { it.name == friend.name }
                                FilterChip(
                                    selected = isAdded,
                                    onClick = {
                                        if (isAdded) {
                                            viewModel.removeInvitee(friend.name)
                                        } else {
                                            viewModel.addInviteeWithoutVerification(friend.name, friend.colorIndex)
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    label = { Text(friend.name) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 4. Manual Add Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteeInput,
                            onValueChange = { inviteeInput = it },
                            label = { Text("Invite by name") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (inviteeInput.isNotBlank()) {
                                        viewModel.addInvitee(inviteeInput) { success, msg ->
                                            if (success) {
                                                inviteeInput = ""
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (inviteeInput.isNotBlank()) {
                                            viewModel.addInvitee(inviteeInput) { success, msg ->
                                                if (success) {
                                                    inviteeInput = ""
                                                } else {
                                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Invitee")
                                }
                            }
                        )
                    }
                }
            }

            // ── Card 3: Temporal Boundaries ──────────────────────────────────
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = "Availability Window",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " *",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalMonthCalendar(
                        selectedDayMillis = eventRequest.selectedDatesList,
                        onDayToggled = { day ->
                            viewModel.toggleSelectedDateInRequest(day)
                        },
                        onRangeDragged = { start, end, isSelecting ->
                            viewModel.addSelectedDatesRangeInRequest(start, end, isSelecting)
                        },
                        onDragStateChanged = { interacting ->
                            scrollEnabled = !interacting
                        }
                    )

                    val selectedDatesList = eventRequest.selectedDatesList
                    if (selectedDatesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Specific Hours",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val draftCells by viewModel.draftAvailability.collectAsState()
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTimePickerDialog = true
                                },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (draftCells.isEmpty()) "All-Day" else "You Selected Specific Hours",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (draftCells.isEmpty()) {
                                            "Tap to specify particular hours of the day."
                                        } else {
                                            "Tap to edit"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit times",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Save as Reusable Template",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
            }

            Spacer(Modifier.height(16.dp))

            val draftCells by viewModel.draftAvailability.collectAsState()

            Button(
                onClick = {
                    showValidationErrors = true
                    if (!isEventNameValid) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Event Name is required")
                        }
                        return@Button
                    }
                    if (!isDateRangeValid) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Availability window (dates) must be selected")
                        }
                        return@Button
                    }
                    if (!hasInvitees) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please invite at least one participant")
                        }
                        return@Button
                    }

                    if (draftCells.isEmpty()) {
                        viewModel.finalizeDateOnlyEventRequest()
                    } else {
                        viewModel.finalizeEventWithSpecificTimeSlots()
                    }
                    if (saveAsTemplate) {
                        viewModel.saveTemplate(
                            EventTemplate(
                                emoji = eventRequest.eventEmoji,
                                name = eventRequest.eventName,
                                invitees = eventRequest.invitees.map { it.name }
                            )
                        )
                    }
                    onRequestSent()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Request Availabilities"
                )
            }
        }

        if (showTimePickerDialog) {
            val config = remember(eventRequest.selectedDatesList) {
                GridConfig(
                    startDateMillis = eventRequest.startDateMillis,
                    endDateMillis = eventRequest.endDateMillis,
                    startHour = 0,
                    endHour = 24
                )
            }
            
            var gridScrollEnabled by remember { mutableStateOf(true) }
            val draftCells by viewModel.draftAvailability.collectAsState()
            
            val visibleCols = remember(eventRequest.selectedDatesList, eventRequest.startDateMillis) {
                eventRequest.selectedDatesList.map {
                    ((it - eventRequest.startDateMillis) / 86400000L).toInt()
                }.sorted()
            }

            val gridScrollState = rememberScrollState()
            LaunchedEffect(gridScrollState.maxValue) {
                if (gridScrollState.maxValue > 0) {
                    gridScrollState.scrollTo(gridScrollState.maxValue)
                }
            }
            
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showTimePickerDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.98f)
                        .fillMaxHeight(0.95f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Specify Time Slots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var showDragHint by remember { mutableStateOf(true) }
                        Text(
                            text = "Paint the hours that work. Participants can only choose within these times.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            AvailabilityGrid(
                                gridConfig = config,
                                selectedCells = draftCells,
                                heatmap = emptyMap(),
                                conflictingCells = emptySet(),
                                totalParticipants = 1,
                                onCellToggled = { index ->
                                    viewModel.toggleCell(index)
                                },
                                onCellPainted = { index, isSelecting ->
                                    viewModel.paintCell(index, isSelecting)
                                },
                                scrollEnabled = gridScrollEnabled,
                                onDragStateChanged = { interacting ->
                                    gridScrollEnabled = !interacting
                                },
                                visibleCols = visibleCols,
                                verticalScrollState = gridScrollState
                            )
                            if (showDragHint) {
                                DragGestureHintOverlay(
                                    onDismiss = { showDragHint = false },
                                    backgroundAlpha = 0.35f
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.clearDraftAvailability()
                                    showTimePickerDialog = false
                                }
                            ) {
                                Text("Reset to All-Day")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { showTimePickerDialog = false }
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val CALENDAR_PAGE_COUNT = 24

/**
 * A horizontal-swipe month calendar with multi-select day semantics.
 *
 * Selection is driven by a list of selected dates [selectedDayMillis].
 * Drag gestures allow selecting and deselecting multiple dates.
 */
@Composable
private fun HorizontalMonthCalendar(
    selectedDayMillis: List<Long>,
    onDayToggled: (Long) -> Unit,
    onRangeDragged: (Long, Long, Boolean) -> Unit,
    onDragStateChanged: ((Boolean) -> Unit)? = null
) {
    val minDate = remember { LocalDate.now() }
    val baseMonth = remember(minDate) { YearMonth.from(minDate) }
    val pagerState = rememberPagerState(pageCount = { CALENDAR_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val selectedDates = remember(selectedDayMillis) {
        selectedDayMillis.map { it.toUtcLocalDate() }.toSet()
    }

    val visibleMonth = baseMonth.plusMonths(pagerState.currentPage.toLong())
    val monthLabel =
        "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${visibleMonth.year}"

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Month navigation header ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(CALENDAR_PAGE_COUNT - 1)
                        )
                    }
                },
                enabled = pagerState.currentPage < CALENDAR_PAGE_COUNT - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // ── Static weekday header (does not page) ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Paged month-day grids (horizontal swipe = change month) ──────────
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(296.dp)
        ) { page ->
            MonthDaysGrid(
                month = baseMonth.plusMonths(page.toLong()),
                startDate = null,
                endDate = null,
                minDate = minDate,
                selectedDates = selectedDates,
                onDayClick = { date -> onDayToggled(date.toUtcMillis()) },
                onRangeDragged = onRangeDragged,
                onDragStateChanged = onDragStateChanged
            )
        }
    }
}

/** One month laid out as week rows of seven day cells (Monday-first). */
@Composable
private fun MonthDaysGrid(
    month: YearMonth,
    startDate: LocalDate?,
    endDate: LocalDate?,
    minDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    maxDate: LocalDate? = null,
    selectedDates: Set<LocalDate> = emptySet(),
    restrictedDaysMillis: Set<Long> = emptySet(),
    onRangeDragged: ((Long, Long, Boolean) -> Unit)? = null,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    gridConfig: GridConfig? = null,
    heatmap: Map<Int, Int> = emptyMap(),
    totalParticipants: Int = 0,
    invitees: List<Invitee> = emptyList(),
    responses: Map<String, ParticipantResponse> = emptyMap(),
    showFriendAvailabilities: Boolean = false,
    participantName: String? = null,
    selectedFriend: String? = null
) {
    val daysInMonth = month.lengthOfMonth()
    // ISO day-of-week: Monday = 1 … Sunday = 7 → blank cells before day 1.
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val weeks = (leadingBlanks + daysInMonth + 6) / 7

    val currentOnRangeDragged by rememberUpdatedState(onRangeDragged)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
    val currentOnDayClick by rememberUpdatedState(onDayClick)
    val currentSelectedDates by rememberUpdatedState(selectedDates)

    val showCounts = remember(responses, participantName) {
        responses.keys.any { it != participantName }
    }

    val density = LocalDensity.current
    val cellHeightPx = with(density) { 44.dp.toPx() }

    val pointerModifier = if (onRangeDragged != null) {
        Modifier.pointerInput(month, leadingBlanks, weeks, restrictedDaysMillis) {
            val gridWidth = size.width
            val cellWidthPx = gridWidth / 7f

            fun dateAt(offset: Offset): LocalDate? {
                val col = (offset.x / cellWidthPx).toInt().coerceIn(0, 6)
                val row = (offset.y / cellHeightPx).toInt().coerceIn(0, weeks - 1)
                val dayOfMonth = row * 7 + col - leadingBlanks + 1
                return if (dayOfMonth in 1..daysInMonth) {
                    month.atDay(dayOfMonth)
                } else null
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val firstDate = dateAt(down.position)
                if (firstDate != null) {
                    val isFirstEnabled = !firstDate.isBefore(minDate) &&
                        (maxDate == null || !firstDate.isAfter(maxDate)) &&
                        firstDate.toUtcMillis() !in restrictedDaysMillis
                    if (isFirstEnabled) {
                        var isDrag = false
                        var currentEnd = firstDate
                        val isSelecting = firstDate !in currentSelectedDates

                        var hasNotifiedDragStart = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                break
                            }
                            val dragDate = dateAt(change.position)
                            val dragAmount = change.position - down.position
                            if (!isDrag) {
                                if (abs(dragAmount.x) > viewConfiguration.touchSlop) {
                                    isDrag = true
                                    if (!hasNotifiedDragStart) {
                                        currentOnDragStateChanged?.invoke(true)
                                        hasNotifiedDragStart = true
                                    }
                                } else if (abs(dragAmount.y) > viewConfiguration.touchSlop) {
                                    // Vertical drag detected first; abort range selection so the parent can scroll
                                    break
                                }
                            }
                            if (isDrag && dragDate != null) {
                                val isDragEnabled = !dragDate.isBefore(minDate) &&
                                    (maxDate == null || !dragDate.isAfter(maxDate)) &&
                                    dragDate.toUtcMillis() !in restrictedDaysMillis
                                if (isDragEnabled && dragDate != currentEnd) {
                                    currentEnd = dragDate
                                    currentOnRangeDragged?.invoke(firstDate.toUtcMillis(), currentEnd.toUtcMillis(), isSelecting)
                                }
                            }
                            if (isDrag) {
                                change.consume()
                            }
                        }

                        if (hasNotifiedDragStart) {
                            currentOnDragStateChanged?.invoke(false)
                        }
                    }
                }
            }
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(pointerModifier),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (dayOfWeek in 0 until 7) {
                    val dayOfMonth = week * 7 + dayOfWeek - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = month.atDay(dayOfMonth)
                        val enabled = !date.isBefore(minDate) &&
                            (maxDate == null || !date.isAfter(maxDate)) &&
                            date.toUtcMillis() !in restrictedDaysMillis

                        if (gridConfig != null) {
                            val cellIndex = gridConfig.timestampToCell(date.toUtcMillis())
                            if (cellIndex != null && enabled) {
                                val count = heatmap[cellIndex] ?: 0
                                val ratio = count.toFloat() / totalParticipants.coerceAtLeast(1)
                                val isSelected = date in selectedDates

                                val availableInvitees = if (!showFriendAvailabilities) emptyList()
                                else invitees.filter { invitee ->
                                    if (invitee.name == participantName) {
                                        isSelected
                                    } else {
                                        responses[invitee.name]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                    }
                                }

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

                                HeatmapDayCell(
                                    day = dayOfMonth,
                                    enabled = true,
                                    isSelected = isSelected,
                                    count = count,
                                    ratio = ratio,
                                    availableInvitees = if (selectedFriend != null) emptyList() else availableInvitees,
                                    showFriendAvailabilities = showFriendAvailabilities,
                                    selectedFriendColor = selectedFriendColor,
                                    selectedFriendAvailable = isSelectedFriendAvailable,
                                    showCounts = showCounts,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onDayClick(date) }
                                )
                            } else {
                                DisabledDayCell(day = dayOfMonth, modifier = Modifier.weight(1f))
                            }
                        } else {
                            DayCell(
                                day = dayOfMonth,
                                enabled = enabled,
                                isAnchor = date == startDate || date == endDate || date in selectedDates,
                                inRange = startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(endDate),
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(date) }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun MultiSelectMonthCalendar(
    selectedDayMillis: Set<Long>,
    minDateMillis: Long,
    maxDateMillis: Long,
    onDayToggled: (Long) -> Unit,
    restrictedDaysMillis: Set<Long> = emptySet(),
    onRangeDragged: ((Long, Long, Boolean) -> Unit)? = null,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    gridConfig: GridConfig? = null,
    heatmap: Map<Int, Int> = emptyMap(),
    totalParticipants: Int = 0,
    invitees: List<Invitee> = emptyList(),
    responses: Map<String, ParticipantResponse> = emptyMap(),
    showFriendAvailabilities: Boolean = false,
    participantName: String? = null,
    selectedFriend: String? = null
) {
    val minDate = remember(minDateMillis) { minDateMillis.toUtcLocalDate() }
    val maxDate = remember(maxDateMillis) { maxDateMillis.toUtcLocalDate() }
    val baseMonth = remember(minDate) { YearMonth.from(minDate) }
    val pageCount = remember(minDate, maxDate) {
        (java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(minDate), YearMonth.from(maxDate)).toInt() + 1)
            .coerceAtLeast(1)
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    val selectedDates = remember(selectedDayMillis) {
        selectedDayMillis.map { it.toUtcLocalDate() }.toSet()
    }

    val visibleMonth = baseMonth.plusMonths(pagerState.currentPage.toLong())
    val monthLabel =
        "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${visibleMonth.year}"

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Month navigation header ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                        )
                    }
                },
                enabled = pagerState.currentPage < pageCount - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // ── Static weekday header (does not page) ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Paged month-day grids (horizontal swipe = change month) ──────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(296.dp)
        ) { page ->
            MonthDaysGrid(
                month = baseMonth.plusMonths(page.toLong()),
                startDate = null,
                endDate = null,
                minDate = minDate,
                maxDate = maxDate,
                selectedDates = selectedDates,
                onDayClick = { date -> onDayToggled(date.toUtcMillis()) },
                restrictedDaysMillis = restrictedDaysMillis,
                onRangeDragged = onRangeDragged,
                onDragStateChanged = onDragStateChanged,
                gridConfig = gridConfig,
                heatmap = heatmap,
                totalParticipants = totalParticipants,
                invitees = invitees,
                responses = responses,
                showFriendAvailabilities = showFriendAvailabilities,
                participantName = participantName,
                selectedFriend = selectedFriend
            )
        }
    }
}

/** A single tappable day. Anchors are filled, in-range days tinted, past days dimmed. */
@Composable
private fun DayCell(
    day: Int,
    enabled: Boolean,
    isAnchor: Boolean,
    inRange: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = when {
        isAnchor -> MaterialTheme.colorScheme.primary
        inRange -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        isAnchor -> MaterialTheme.colorScheme.onPrimary
        inRange -> MaterialTheme.colorScheme.onPrimaryContainer
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = foreground
        )
    }
}

@Composable
private fun HeatmapDayCell(
    day: Int,
    enabled: Boolean,
    isSelected: Boolean,
    count: Int,
    ratio: Float,
    availableInvitees: List<Invitee>,
    showFriendAvailabilities: Boolean,
    selectedFriendColor: Color? = null,
    selectedFriendAvailable: Boolean = false,
    showCounts: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        showFriendAvailabilities && selectedFriendColor != null -> {
            if (selectedFriendAvailable) selectedFriendColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
        showFriendAvailabilities -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        count > 0 -> heatColor(ratio)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val foreground = when {
        showFriendAvailabilities && selectedFriendColor != null -> {
            if (selectedFriendAvailable) Color.White else MaterialTheme.colorScheme.onSurface
        }
        !showFriendAvailabilities && count > 0 -> {
            if (ratio > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
        }
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = foreground
            )
            
            if (showFriendAvailabilities) {
                if (availableInvitees.isNotEmpty()) {
                    val dotSize = when {
                        availableInvitees.size > 6 -> 4.dp
                        availableInvitees.size > 4 -> 5.dp
                        else -> 6.dp
                    }
                    val spacing = when {
                        availableInvitees.size > 6 -> (-1.5).dp
                        availableInvitees.size > 4 -> 0.5.dp
                        else -> 2.dp
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        availableInvitees.forEach { invitee ->
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .background(CuratedParticipantColors.getOrElse(invitee.colorIndex) { Color.Gray }, CircleShape)
                            )
                        }
                    }
                }
            } else if (count > 0 && showCounts) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                    color = foreground
                )
            }
        }
    }
}

@Composable
private fun DisabledDayCell(
    day: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun heatColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color.Transparent
        ratio <= 0.2f -> Color(0xFFE8F5E9)
        ratio <= 0.4f -> Color(0xFFC8E6C9)
        ratio <= 0.6f -> Color(0xFFA5D6A7)
        ratio <= 0.8f -> Color(0xFF81C784)
        else -> Color(0xFF66BB6A)
    }
}

/** Epoch millis at UTC start-of-day — matches the convention used by GridConfig. */
internal fun LocalDate.toUtcMillis(): Long =
    this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun getGraphemeCount(text: String): Int {
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var count = 0
    while (iterator.next() != java.text.BreakIterator.DONE) {
        count++
    }
    return count
}
