package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import com.example.cheese.data.DateOffset
import com.example.cheese.data.EventTemplate
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

val COMMON_EMOJIS = listOf("📅", "🍔", "🏀", "🎮", "🍻", "🎬", "📚", "✈️")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrganizerScreen(
    viewModel: ScheduleViewModel,
    onRequestSent: () -> Unit
) {
    val eventRequest by viewModel.eventRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var inviteeInput by remember { mutableStateOf("") }
    var saveAsTemplate by remember { mutableStateOf(false) }

    val isEventNameValid = eventRequest.eventName.isNotBlank()
    val isDateRangeValid = eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L
    val hasInvitees = eventRequest.invitees.isNotEmpty()
    val isFormValid = isEventNameValid && isDateRangeValid && hasInvitees

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Schedule New Event") },
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
                .verticalScroll(rememberScrollState()),
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
                        COMMON_EMOJIS.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (eventRequest.eventEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.updateEventEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = eventRequest.eventName,
                        onValueChange = { viewModel.updateEventName(it) },
                        label = { Text("Event Name") },
                        placeholder = { Text("e.g., Team Retrospective") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                }
            }

            // ── Card 2: Temporal Boundaries ──────────────────────────────────
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Availability Window",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val today = remember { LocalDate.now(ZoneOffset.UTC).toUtcMillis() }
                    val tomorrow = remember { LocalDate.now(ZoneOffset.UTC).plusDays(1).toUtcMillis() }
                    val weekendStart = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.SUNDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).toUtcMillis()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).toUtcMillis()
                        }
                    }
                    val weekendEnd = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.plusDays(7).toUtcMillis()
                        } else if (t.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                            t.plusDays(1).toUtcMillis()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY)).toUtcMillis()
                        }
                    }
                    
                    val weekdaysStart = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.FRIDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY || t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).toUtcMillis()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toUtcMillis()
                        }
                    }
                    val weekdaysEnd = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.FRIDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY || t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY)).toUtcMillis()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)).toUtcMillis()
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(onClick = { viewModel.updateDateRange(today, today) }, label = { Text("Today") })
                        SuggestionChip(onClick = { viewModel.updateDateRange(tomorrow, tomorrow) }, label = { Text("Tomorrow") })
                        SuggestionChip(onClick = { viewModel.updateDateRange(weekdaysStart, weekdaysEnd) }, label = { Text("Weekdays") })
                        SuggestionChip(onClick = { viewModel.updateDateRange(weekendStart, weekendEnd) }, label = { Text("Weekend") })
                    }
                    Spacer(Modifier.height(8.dp))

                    // Horizontal-swipe calendar. A horizontal swipe pages between
                    // months; a vertical swipe falls through to the form's
                    // verticalScroll. This is the clean separation that the old M3
                    // DateRangePicker (which scrolled months vertically) could not
                    // provide while nested inside a vertically-scrolling form.
                    HorizontalMonthCalendar(
                        startMillis = eventRequest.startDateMillis,
                        endMillis = eventRequest.endDateMillis,
                        onStartSelected = { start ->
                            // Begin a new range: set both anchor and end so a single day is valid immediately.
                            viewModel.updateDateRange(start, start)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onRangeSelected = { start, end ->
                            viewModel.updateDateRange(start, end)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }

            // ── Card 3: Invitee Management & Color Customization ─────────────
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
                                var expanded by remember { mutableStateOf(false) }

                                Box {
                                    InputChip(
                                        selected = true,
                                        onClick = { expanded = true },
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
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${invitee.name}",
                                                modifier = Modifier.clickable {
                                                    viewModel.removeInvitee(invitee.name)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )

                                    // Color Picker Dropdown
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        CuratedParticipantColors.forEachIndexed { index, color ->
                                            DropdownMenuItem(
                                                text = { Text("Color ${index + 1}") },
                                                leadingIcon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(color, CircleShape)
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.updateInviteeColor(invitee.name, index)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inviteeInput,
                        onValueChange = { inviteeInput = it },
                        placeholder = { Text("Add more name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inviteeInput.isNotBlank()) {
                                    viewModel.addInvitee(inviteeInput)
                                    inviteeInput = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    )
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

            Button(
                onClick = {
                    if (saveAsTemplate) {
                        viewModel.saveTemplate(
                            EventTemplate(
                                emoji = eventRequest.eventEmoji,
                                name = eventRequest.eventName,
                                dateOffset = DateOffset.CUSTOM
                            )
                        )
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar("Group Request Sent")
                    }
                    onRequestSent()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = isFormValid,
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Request Availability")
            }
        }
    }
}

private const val CALENDAR_PAGE_COUNT = 24

/**
 * A horizontal-swipe month calendar with range selection.
 *
 * Replaces the M3 [androidx.compose.material3.DateRangePicker], whose internal
 * vertical month list fought the form's own `verticalScroll`. Here a *horizontal*
 * swipe pages between months while a *vertical* swipe falls straight through to
 * the form scroll — the two intents never collide because they live on
 * orthogonal axes.
 *
 * Selection is fully driven by [startMillis]/[endMillis] (the ViewModel is the
 * single source of truth); taps are reported back via the callbacks:
 *  - [onStartSelected] — begin a new range (anchor day, end cleared)
 *  - [onRangeSelected] — close the range (anchor + end day)
 *
 * All conversions use UTC start-of-day millis to stay consistent with
 * `GridConfig`'s day-count maths.
 */
@Composable
private fun HorizontalMonthCalendar(
    startMillis: Long,
    endMillis: Long,
    onStartSelected: (Long) -> Unit,
    onRangeSelected: (Long, Long) -> Unit
) {
    val minDate = remember { LocalDate.now() }
    val baseMonth = remember(minDate) { YearMonth.from(minDate) }
    val pagerState = rememberPagerState(pageCount = { CALENDAR_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val startDate = remember(startMillis) { if (startMillis > 0L) startMillis.toUtcLocalDate() else null }
    val endDate = remember(endMillis) { if (endMillis > 0L) endMillis.toUtcLocalDate() else null }

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
            modifier = Modifier
                .fillMaxWidth()
                .height(296.dp)
        ) { page ->
            MonthDaysGrid(
                month = baseMonth.plusMonths(page.toLong()),
                startDate = startDate,
                endDate = endDate,
                minDate = minDate,
                onDayClick = { date ->
                    val tapped = date.toUtcMillis()
                    when {
                        startDate == null -> onStartSelected(tapped)
                        startDate == endDate -> {
                            if (date.isBefore(startDate)) onStartSelected(tapped)
                            else if (date.isAfter(startDate)) onRangeSelected(startDate.toUtcMillis(), tapped)
                            else onStartSelected(tapped)
                        }
                        else -> onStartSelected(tapped)
                    }
                }
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
    onDayClick: (LocalDate) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    // ISO day-of-week: Monday = 1 … Sunday = 7 → blank cells before day 1.
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val weeks = (leadingBlanks + daysInMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (dayOfWeek in 0 until 7) {
                    val dayOfMonth = week * 7 + dayOfWeek - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            day = dayOfMonth,
                            enabled = !date.isBefore(minDate),
                            isAnchor = date == startDate || date == endDate,
                            inRange = startDate != null && endDate != null &&
                                date.isAfter(startDate) && date.isBefore(endDate),
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) }
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
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

/** Epoch millis at UTC start-of-day — matches the convention used by GridConfig. */
private fun LocalDate.toUtcMillis(): Long =
    this.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
