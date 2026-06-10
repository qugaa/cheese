package com.example.cheese.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.GridConfig
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch

private val LightSageGreen = Color(0xFFD5E8D4)
private val MediumMintGreen = Color(0xFFA5D6A7)
private val VibrantEmeraldGreen = Color(0xFF4CAF50)
private val DeepForestGreen = Color(0xFF1B5E20)

@Composable
private fun heatColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color.Transparent
        ratio <= 0.25f -> LightSageGreen
        ratio <= 0.50f -> MediumMintGreen
        ratio <= 0.75f -> VibrantEmeraldGreen
        else -> DeepForestGreen
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParticipantScreen(
    viewModel: ScheduleViewModel,
    onSubmitted: (Boolean) -> Unit,
    onEditEvent: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    val draftAvailability by viewModel.draftAvailability.collectAsState()
    val currentEventId by viewModel.currentEventId.collectAsState()
    val events by viewModel.events.collectAsState()
    
    // Find event state. If null (because not finalized), fallback to eventRequest
    val eventState = events.find { it.request.id == currentEventId }
    val eventRequest = eventState?.request ?: viewModel.eventRequest.collectAsState().value
    val organizerRestrictions = eventState?.organizerRestrictions ?: emptySet()
    
    val currentInvitee = viewModel.currentInvitee()
    val participantName = currentInvitee?.name ?: "Unknown"
    val isOrganizer = currentInvitee?.isHost == true

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour) {
        GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)
    }

    val responses = eventState?.responses ?: emptyMap()
    val heatmap = remember(responses) { viewModel.computeHeatmap(gridConfig) }
    val totalParticipants = eventRequest.invitees.size

    val conflicts = remember(events) { viewModel.getConflictingTimestamps() }
    val conflictingCells = remember(conflicts, gridConfig) {
        conflicts.mapNotNull { gridConfig.timestampToCell(it) }.toSet()
    }

    LaunchedEffect(currentEventId) {
        viewModel.loadDraftForCurrentParticipant()
    }

    // Placing the CTA in the bottomBar slot of the Scaffold ensures that the
    // SnackbarHost floats above it, preventing UI obstruction.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isOrganizer) "Set Possible Time Options" else "Share Your Available Times") },
                navigationIcon = {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOrganizer) {
                        IconButton(onClick = onEditEvent) {
                            Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Event Details")
                        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isEmpty = draftAvailability.isEmpty()
                    Button(
                        onClick = {
                            scope.launch {
                                val msg = if (isEmpty && !isOrganizer) "Marked as not available" else "Availability submitted for $participantName"
                                snackbarHostState.showSnackbar(msg)
                            }
                            viewModel.submitAvailability()
                            onSubmitted(true)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = if (isOrganizer) !isEmpty else true,
                        colors = if (!isOrganizer && isEmpty) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else androidx.compose.material3.ButtonDefaults.buttonColors(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(if (isOrganizer) "Send Event" else if (isEmpty) "Not Available" else "Submit Availability")
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
            // Context Header
            AnimatedVisibility(visible = true, enter = slideInVertically() + fadeIn()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "${eventRequest.eventEmoji} ${eventRequest.eventName.ifBlank { "Untitled" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Responding as: $participantName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invited:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            eventRequest.invitees.forEach { invitee ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(CuratedParticipantColors[invitee.colorIndex], CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = invitee.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Instruction banner
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tap to select a single time slot, or drag across slots to paint your availability.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // Scrollable grid area (Vertical AND Horizontal scrolling to support dynamic days)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AvailabilityGrid(
                    gridConfig = gridConfig,
                    selectedCells = draftAvailability,
                    heatmap = heatmap,
                    conflictingCells = conflictingCells,
                    totalParticipants = totalParticipants,
                    onCellToggled = { index ->
                        viewModel.toggleCell(index)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onCellPainted = { index ->
                        viewModel.paintCell(index, gridConfig.totalCells)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
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
private fun AvailabilityGrid(
    gridConfig: GridConfig,
    selectedCells: Set<Int>,
    heatmap: Map<Int, Int>,
    conflictingCells: Set<Int>,
    totalParticipants: Int,
    onCellToggled: (Int) -> Unit,
    onCellPainted: (Int) -> Unit
) {
    val labelColWidth: Dp = 48.dp
    val cellHeight: Dp = 40.dp
    val headerHeight: Dp = 24.dp
    val horizontalScrollState = rememberScrollState()

    // BoxWithConstraints is NOT under horizontalScroll, so maxWidth is the real,
    // finite viewport width. (Previously horizontalScroll wrapped it, handing the
    // content an unbounded width → cellWidth resolved to Infinity and the whole
    // grid failed to lay out / was invisible.) From a finite width we can size
    // each day column to fill the screen when there are few days, or fall back to
    // a comfortable touch-target width and scroll horizontally when there are many.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = gridConfig.cols.coerceAtLeast(1)
        val cellWidth: Dp = maxOf((maxWidth - labelColWidth) / cols, 56.dp)

        Row(modifier = Modifier.fillMaxWidth()) {

            // ── Fixed hour-label column (left edge, does not scroll) ──────────
            Column(modifier = Modifier.width(labelColWidth)) {
                Spacer(Modifier.height(headerHeight))
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

            // ── Scrollable day columns (header + cell matrix) ─────────────────
            Column(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .width(cellWidth * cols)
            ) {
                // Day header row
                Row(modifier = Modifier.height(headerHeight)) {
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

                // Cell grid with a single unified gesture handler.
                //
                // Two separate detectTapGestures/detectDragGestures blocks used to
                // sit here, nested inside both a verticalScroll (page) and a
                // horizontalScroll (this grid). Those scroll containers won the
                // touch-slop race and consumed the pointer stream before either
                // detector recognised it — so cells were entirely unselectable.
                //
                // We now run one raw awaitEachGesture loop in the Main pass
                // (innermost node sees events first) and explicitly consume() every
                // move once a paint drag is recognised. Consuming marks the change
                // as handled, so the parent scroll containers skip it and the
                // gesture stays with the grid. A pointer that lifts before crossing
                // touch slop is treated as a tap (toggle); anything past slop paints.
                //
                // Offsets here are relative to this Box, whose top-left is cell
                // (row 0, col 0) — the day header is a sibling above, so y maps
                // straight onto rows.
                Box(
                    modifier = Modifier
                        .width(cellWidth * cols)
                        .pointerInput(gridConfig, cellWidth) {
                            val cellWidthPx = cellWidth.toPx()
                            val cellHeightPx = cellHeight.toPx()

                            fun cellAt(offset: Offset): Int {
                                val col = (offset.x / cellWidthPx)
                                    .toInt().coerceIn(0, gridConfig.cols - 1)
                                val row = (offset.y / cellHeightPx)
                                    .toInt().coerceIn(0, gridConfig.rows - 1)
                                return gridConfig.cellIndex(row, col)
                            }

                            detectTapGestures(
                                onTap = { offset ->
                                    val idx = cellAt(offset)
                                    onCellToggled(idx)
                                }
                            )
                        }
                        .pointerInput(gridConfig, cellWidth) {
                            val cellWidthPx = cellWidth.toPx()
                            val cellHeightPx = cellHeight.toPx()

                            fun cellAt(offset: Offset): Int {
                                val col = (offset.x / cellWidthPx)
                                    .toInt().coerceIn(0, gridConfig.cols - 1)
                                val row = (offset.y / cellHeightPx)
                                    .toInt().coerceIn(0, gridConfig.rows - 1)
                                return gridConfig.cellIndex(row, col)
                            }

                            var lastPainted = -1
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val idx = cellAt(offset)
                                    onCellPainted(idx)
                                    lastPainted = idx
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val idx = cellAt(change.position)
                                    if (idx != lastPainted) {
                                        onCellPainted(idx)
                                        lastPainted = idx
                                    }
                                },
                                onDragEnd = { lastPainted = -1 },
                                onDragCancel = { lastPainted = -1 }
                            )
                        }
                ) {
                    Column {
                        gridConfig.hourLabels.forEachIndexed { rowIdx, _ ->
                            Row(modifier = Modifier.height(cellHeight)) {
                                repeat(gridConfig.cols) { colIdx ->
                                    val cellIndex = gridConfig.cellIndex(rowIdx, colIdx)
                                    val isSelected = cellIndex in selectedCells
                                    val isConflicting = cellIndex in conflictingCells
                                    val count = heatmap[cellIndex] ?: 0
                                    val safeTotal = totalParticipants.coerceAtLeast(1)
                                    val ratio = count.toFloat() / safeTotal

                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .fillMaxHeight()
                                            .padding(1.5.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    isSelected -> VibrantEmeraldGreen
                                                    count > 0 -> heatColor(ratio)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .drawWithContent {
                                                drawContent()
                                                if (isConflicting) {
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
                                        if (count > 0) {
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
    }
}
