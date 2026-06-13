package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.EventRequest
import com.example.cheese.data.GridConfig
import com.example.cheese.data.ParticipantResponse
import com.example.cheese.data.Invitee
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import androidx.compose.material3.Switch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.time.Instant
import kotlinx.coroutines.launch

private val LightSageGreen = Color(0xFFE8F5E9)
private val MediumMintGreen = Color(0xFFC8E6C9)
private val VibrantEmeraldGreen = Color(0xFFA5D6A7)
private val DeepForestGreen = Color(0xFF81C784)
private val EmeraldPastel = Color(0xFF66BB6A)

@Composable
private fun heatColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color.Transparent
        ratio <= 0.2f -> LightSageGreen
        ratio <= 0.4f -> MediumMintGreen
        ratio <= 0.6f -> VibrantEmeraldGreen
        ratio <= 0.8f -> DeepForestGreen
        else -> EmeraldPastel
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionScreen(
    viewModel: ScheduleViewModel,
    onEditEvent: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val currentEventId by viewModel.currentEventId.collectAsState()
    val events by viewModel.events.collectAsState()
    
    val eventState = events.find { it.request.id == currentEventId }
    val eventRequest = eventState?.request ?: viewModel.eventRequest.collectAsState().value
    
    val finalCellIndex = eventState?.finalCellIndex
    val responses = eventState?.responses ?: emptyMap()

    val dateOnly = eventRequest.dateOnlyMode

    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis, dateOnly) {
        if (dateOnly) {
            // One row per day: aggregation happens by date, not time slot
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, 0, 1)
        } else {
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis)
        }
    }

    val heatmap = remember(responses) { viewModel.computeHeatmap(gridConfig) }
    val optimalCell = remember(responses) { viewModel.computeOptimalCell(gridConfig) }
    val totalParticipants = eventRequest.invitees.size

    val currentUser by viewModel.currentUser.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showFriendAvailabilities by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<String?>(null) }
    var selectMultipleDays by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Organizer's selection is a time range (startCell, endCell).
    // First tap sets the anchor (start == end); a second tap in the same column
    // or later closes the range; tapping the anchor again cancels.
    var selectedRange by remember(optimalCell) {
        mutableStateOf(optimalCell?.let { it to it })
    }

    // Time-order key: column (day) first, then row (hour).
    fun timeKey(index: Int): Int =
        (index % gridConfig.cols) * gridConfig.rows + (index / gridConfig.cols)

    // The cell that starts the selected range, in time order.
    val startCell = selectedRange?.let { (a, b) -> if (timeKey(a) <= timeKey(b)) a else b }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Final Confirmation")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .fillMaxSize()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${eventRequest.eventEmoji} ${eventRequest.eventName.ifBlank { "Untitled Event" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${responses.size} / $totalParticipants responded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (dateOnly) {
                // Select Multiple Days Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Multiple Days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = selectMultipleDays,
                        onCheckedChange = {
                            selectMultipleDays = it
                            // Reset selection when toggling selection mode to avoid inconsistent states
                            selectedRange = null
                        }
                    )
                }
            }

            // Show Friend Availabilities Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = if (dateOnly) 2.dp else 8.dp, bottom = 4.dp),
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

            // Legend Row on top of the calendar/timetable (fixed height box to prevent shifting)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showFriendAvailabilities) {
                    LegendRow(
                        invitees = eventRequest.invitees,
                        currentUser = currentUser,
                        selectedFriend = selectedFriend,
                        onFriendSelected = { selectedFriend = it }
                    )
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
                            LightSageGreen,
                            MediumMintGreen,
                            VibrantEmeraldGreen,
                            DeepForestGreen,
                            EmeraldPastel
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

            // Heatmap grid or calendar month view with vertical scrolling
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (dateOnly) {
                    HeatmapMonthCalendar(
                        gridConfig = gridConfig,
                        heatmap = heatmap,
                        totalParticipants = totalParticipants,
                        selectedRange = selectedRange,
                        invitees = eventRequest.invitees,
                        responses = responses,
                        showFriendAvailabilities = showFriendAvailabilities,
                        selectedFriend = selectedFriend,
                        onCellTapped = { tapped ->
                            if (selectMultipleDays) {
                                val current = selectedRange
                                selectedRange = when {
                                    // No selection yet → set the anchor
                                    current == null -> tapped to tapped

                                    // Tapping the open anchor again → cancel
                                    current.first == current.second && tapped == current.first -> null

                                    // Anchor is open → close the range
                                    current.first == current.second -> {
                                        current.first to tapped
                                    }

                                    // Range already closed → start a new anchor
                                    else -> tapped to tapped
                                }
                            } else {
                                // In date-only single select mode, tapping selects that single day.
                                // If tapped cell is already the only one selected, deselect it.
                                selectedRange = if (selectedRange?.first == tapped && selectedRange?.second == tapped) {
                                    null
                                } else {
                                    tapped to tapped
                                }
                            }
                        }
                    )
                } else {
                    HeatmapGrid(
                        gridConfig = gridConfig,
                        heatmap = heatmap,
                        totalParticipants = totalParticipants,
                        dateOnly = dateOnly,
                        selectedRange = selectedRange,
                        invitees = eventRequest.invitees,
                        responses = responses,
                        showFriendAvailabilities = showFriendAvailabilities,
                        selectedFriend = selectedFriend,
                        onCellTapped = { tapped ->
                            val current = selectedRange
                            selectedRange = when {
                                // No selection yet → set the anchor
                                current == null -> tapped to tapped

                                // Tapping the open anchor again → cancel
                                current.first == current.second && tapped == current.first -> null

                                // Anchor is open → close the range if the tap is in the
                                // same column or later; otherwise re-anchor
                                current.first == current.second -> {
                                    val anchorCol = current.first % gridConfig.cols
                                    val tappedCol = tapped % gridConfig.cols
                                    if (tappedCol >= anchorCol) {
                                        val minK = minOf(timeKey(current.first), timeKey(tapped))
                                        val maxK = maxOf(timeKey(current.first), timeKey(tapped))
                                        val hasGrayCell = (0 until gridConfig.totalCells).any { cellIndex ->
                                            timeKey(cellIndex) in minK..maxK && (heatmap[cellIndex] ?: 0) == 0
                                        }
                                        if (!hasGrayCell) {
                                            current.first to tapped
                                        } else {
                                            tapped to tapped
                                        }
                                    } else {
                                        tapped to tapped
                                    }
                                }

                                // Range already closed → start a new anchor
                                else -> tapped to tapped
                            }
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            selectedRange?.let { range ->
                                viewModel.setFinalEvent(range.first, range.second)
                            }
                            showBottomSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedRange != null,
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Set Final Event")
                    }
                    
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

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            FinalSummarySheet(
                gridConfig = gridConfig,
                eventRequest = eventRequest,
                dateOnly = dateOnly,
                selectedRange = selectedRange,
                responses = responses,
                totalParticipants = totalParticipants,
                heatmap = heatmap,
                onConfirm = {
                    showBottomSheet = false
                    onConfirm()
                },
                onDismiss = {
                    showBottomSheet = false
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun HeatmapGrid(
    gridConfig: GridConfig,
    heatmap: Map<Int, Int>,
    totalParticipants: Int,
    selectedRange: Pair<Int, Int>?,
    onCellTapped: (Int) -> Unit,
    dateOnly: Boolean = false,
    invitees: List<Invitee> = emptyList(),
    responses: Map<String, ParticipantResponse> = emptyMap(),
    showFriendAvailabilities: Boolean = false,
    selectedFriend: String? = null
) {
    val labelColWidth = 48.dp
    val cellHeight = 28.dp

    // Time-order key: column (day) first, then row (hour).
    fun timeKey(index: Int): Int =
        (index % gridConfig.cols) * gridConfig.rows + (index / gridConfig.cols)

    val rangeKeys: IntRange? = selectedRange?.let { (a, b) ->
        val ka = timeKey(a)
        val kb = timeKey(b)
        minOf(ka, kb)..maxOf(ka, kb)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val cols = gridConfig.cols.coerceAtLeast(1)
        val cellWidth: Dp = maxOf((maxWidth - labelColWidth) / cols, 56.dp)

        Row(modifier = Modifier.fillMaxWidth()) {
            // Hour label column
            Column(
                modifier = Modifier
                    .width(labelColWidth)
                    .padding(top = 18.dp) // Adjust based on Day header height
            ) {
                gridConfig.hourLabels.forEach { label ->
                    Box(
                        modifier = Modifier
                            .height(cellHeight)
                            .width(labelColWidth),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = if (dateOnly) "" else label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            // Grid column
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(cellWidth * cols)
            ) {
                // Day headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    gridConfig.dayLabels.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.width(cellWidth),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                // Heat cells
                gridConfig.hourLabels.forEachIndexed { rowIdx, _ ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cellHeight)
                    ) {
                        repeat(gridConfig.cols) { colIdx ->
                            val cellIndex = gridConfig.cellIndex(rowIdx, colIdx)
                            val count = heatmap[cellIndex] ?: 0
                            val safeTotal = totalParticipants.coerceAtLeast(1)
                            val ratio = count.toFloat() / safeTotal
                            val isSelected = rangeKeys != null && timeKey(cellIndex) in rangeKeys

                            val isSelectedFriendAvailable = if (showFriendAvailabilities && selectedFriend != null) {
                                responses[selectedFriend]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                            } else false

                            val selectedFriendColor = if (showFriendAvailabilities && selectedFriend != null) {
                                val friendInvitee = invitees.find { it.name == selectedFriend }
                                friendInvitee?.let { CuratedParticipantColors.getOrElse(it.colorIndex) { Color.Gray } } ?: Color.Gray
                            } else null

                            val availableInvitees = remember(invitees, responses, cellIndex, showFriendAvailabilities) {
                                if (!showFriendAvailabilities) emptyList()
                                else invitees.filter { invitee ->
                                    responses[invitee.name]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                }
                            }

                            val bg = when {
                                showFriendAvailabilities && selectedFriendColor != null -> {
                                    if (isSelectedFriendAvailable) selectedFriendColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                                showFriendAvailabilities -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                count > 0 -> heatColor(ratio)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .fillMaxHeight()
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bg)
                                    .then(
                                        if (isSelected) {
                                            // Static border for the selected range (pulse removed)
                                            Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable { if (count > 0) onCellTapped(cellIndex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (showFriendAvailabilities) {
                                    if (selectedFriend == null) {
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
                                    }
                                } else if (count > 0) {
                                    Text(
                                        text = "$count",
                                        fontSize = 9.sp,
                                        color = if (ratio > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
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
private fun FinalSummarySheet(
    gridConfig: GridConfig,
    eventRequest: EventRequest,
    selectedRange: Pair<Int, Int>?,
    dateOnly: Boolean = false,
    responses: Map<String, ParticipantResponse>,
    totalParticipants: Int,
    heatmap: Map<Int, Int>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val startCell = selectedRange?.first
    val endCell = selectedRange?.second ?: startCell

    val dayLabel = remember(startCell, endCell, gridConfig) {
        if (startCell == null) "—"
        else if (endCell == null || startCell == endCell) gridConfig.cellToDay(startCell)
        else {
            val minCell = minOf(startCell, endCell)
            val maxCell = maxOf(startCell, endCell)
            "${gridConfig.cellToDay(minCell)} → ${gridConfig.cellToDay(maxCell)}"
        }
    }

    val hourLabel = remember(startCell, endCell, gridConfig) {
        if (startCell == null) "—"
        else if (endCell == null || startCell == endCell) gridConfig.cellToHour(startCell)
        else {
            val minCell = minOf(startCell, endCell)
            val maxCell = maxOf(startCell, endCell)
            "${gridConfig.cellToHour(minCell)} → ${gridConfig.cellToHour(maxCell)}"
        }
    }

    val availabilityConfig =
        if (dateOnly) gridConfig
        else GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)

    val selectedTimestamps = remember(selectedRange, availabilityConfig) {
        selectedRange?.let { (start, end) ->
            (minOf(start, end)..maxOf(start, end)).map { availabilityConfig.cellToTimestamp(it) }
        } ?: emptyList()
    }

    val consensusCount = remember(selectedTimestamps, responses, eventRequest) {
        if (selectedTimestamps.isEmpty()) 0
        else eventRequest.invitees.count { invitee ->
            responses[invitee.name]?.let { r ->
                selectedTimestamps.all { ts -> r.availability.contains(ts) }
            } == true
        }
    }
    val consensusPct = if (totalParticipants > 0) (consensusCount * 100f / totalParticipants).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📅 Event Confirmed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        SummaryRow(label = "Event", value = "${eventRequest.eventEmoji} ${eventRequest.eventName.ifBlank { "Untitled" }}")
        SummaryRow(
            label = "Window",
            value = if (eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L)
                "${dateFormatter.format(Date(eventRequest.startDateMillis))} → ${dateFormatter.format(Date(eventRequest.endDateMillis))}"
            else "Not specified"
        )
        SummaryRow(label = "Day", value = dayLabel)
        SummaryRow(label = "Time", value = if (dateOnly) "All day" else hourLabel)
        SummaryRow(
            label = "Consensus",
            value = "$consensusCount / $totalParticipants participants ($consensusPct%)"
        )

        HorizontalDivider()

        Text(
            text = "Participant Availability",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        eventRequest.invitees.forEach { invitee ->
            val response = responses[invitee.name]
            val isAvailable = selectedTimestamps.isNotEmpty() && response?.availability?.let { avail ->
                selectedTimestamps.all { ts -> avail.contains(ts) }
            } == true

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        )
                )
                Text(
                    text = invitee.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (invitee.isHost) {
                    Spacer(modifier = Modifier.width(4.dp))
                    SuggestionChip(
                        onClick = { },
                        label = { Text("Host", fontSize = 8.sp) },
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }

        Text(
            text = "$consensusCount out of $totalParticipants Available",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Confirm Final Schedule")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HeatmapMonthCalendar(
    gridConfig: GridConfig,
    heatmap: Map<Int, Int>,
    totalParticipants: Int,
    selectedRange: Pair<Int, Int>?,
    invitees: List<Invitee>,
    responses: Map<String, ParticipantResponse>,
    showFriendAvailabilities: Boolean,
    selectedFriend: String? = null,
    onCellTapped: (Int) -> Unit
) {
    val minDate = remember(gridConfig.startDateMillis) { gridConfig.startDateMillis.toUtcLocalDate() }
    val maxDate = remember(gridConfig.endDateMillis) { gridConfig.endDateMillis.toUtcLocalDate() }
    val baseMonth = remember(minDate) { YearMonth.from(minDate) }
    val pageCount = remember(minDate, maxDate) {
        (ChronoUnit.MONTHS.between(YearMonth.from(minDate), YearMonth.from(maxDate)).toInt() + 1)
            .coerceAtLeast(1)
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    val visibleMonth = baseMonth.plusMonths(pagerState.currentPage.toLong())
    val monthLabel =
        "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${visibleMonth.year}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Month navigation header
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

        // Static weekday header (Mo, Tu, We, Th, Fr, Sa, Su)
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

        // Paged month-day grids
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
        ) { page ->
            HeatmapMonthDaysGrid(
                month = baseMonth.plusMonths(page.toLong()),
                minDate = minDate,
                maxDate = maxDate,
                gridConfig = gridConfig,
                heatmap = heatmap,
                totalParticipants = totalParticipants,
                selectedRange = selectedRange,
                invitees = invitees,
                responses = responses,
                showFriendAvailabilities = showFriendAvailabilities,
                selectedFriend = selectedFriend,
                onCellTapped = onCellTapped
            )
        }
    }
}

@Composable
private fun HeatmapMonthDaysGrid(
    month: YearMonth,
    minDate: LocalDate,
    maxDate: LocalDate,
    gridConfig: GridConfig,
    heatmap: Map<Int, Int>,
    totalParticipants: Int,
    selectedRange: Pair<Int, Int>?,
    invitees: List<Invitee>,
    responses: Map<String, ParticipantResponse>,
    showFriendAvailabilities: Boolean,
    selectedFriend: String? = null,
    onCellTapped: (Int) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val weeks = (leadingBlanks + daysInMonth + 6) / 7

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (dayOfWeek in 0 until 7) {
                    val dayOfMonth = week * 7 + dayOfWeek - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = month.atDay(dayOfMonth)
                        val enabled = !date.isBefore(minDate) && !date.isAfter(maxDate)
                        
                        if (enabled) {
                            val cellIndex = gridConfig.timestampToCell(date.toUtcMillis())
                            if (cellIndex != null) {
                                val count = heatmap[cellIndex] ?: 0
                                val ratio = count.toFloat() / totalParticipants.coerceAtLeast(1)
                                val isSelected = selectedRange != null && cellIndex in minOf(selectedRange.first, selectedRange.second)..maxOf(selectedRange.first, selectedRange.second)

                                val isSelectedFriendAvailable = if (showFriendAvailabilities && selectedFriend != null) {
                                    responses[selectedFriend]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                } else false

                                val selectedFriendColor = if (showFriendAvailabilities && selectedFriend != null) {
                                    val friendInvitee = invitees.find { it.name == selectedFriend }
                                    friendInvitee?.let { CuratedParticipantColors.getOrElse(it.colorIndex) { Color.Gray } } ?: Color.Gray
                                } else null

                                val availableInvitees = remember(invitees, responses, cellIndex, showFriendAvailabilities) {
                                    if (!showFriendAvailabilities) emptyList()
                                    else invitees.filter { invitee ->
                                        responses[invitee.name]?.availability?.contains(gridConfig.cellToTimestamp(cellIndex)) == true
                                    }
                                }

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
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (count > 0) {
                                            onCellTapped(cellIndex)
                                        }
                                    }
                                )
                            } else {
                                DisabledDayCell(day = dayOfMonth, modifier = Modifier.weight(1f))
                            }
                        } else {
                            DisabledDayCell(day = dayOfMonth, modifier = Modifier.weight(1f))
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
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .clickable(enabled = enabled && count > 0) { onClick() },
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
            } else if (count > 0) {
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
