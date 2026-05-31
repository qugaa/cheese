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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantScreen(
    viewModel: ScheduleViewModel,
    onSubmitted: () -> Unit,
    onEditEvent: () -> Unit,
    onBackToDashboard: () -> Unit
) {
    val draftAvailability by viewModel.draftAvailability.collectAsState()
    val participantIndex by viewModel.currentParticipantIndex.collectAsState()
    val currentEventId by viewModel.currentEventId.collectAsState()
    val events by viewModel.events.collectAsState()
    
    // Find event state. If null (because not finalized), fallback to eventRequest
    val eventState = events.find { it.request.id == currentEventId }
    val eventRequest = eventState?.request ?: viewModel.eventRequest.collectAsState().value
    val organizerRestrictions = eventState?.organizerRestrictions ?: emptySet()
    
    val currentInvitee = viewModel.currentInvitee()
    val participantName = currentInvitee?.name ?: "Unknown"
    val participantColor = currentInvitee?.colorIndex?.let { CuratedParticipantColors[it] } ?: Color(0xFFC8E6C9)
    val isOrganizer = participantIndex == 0

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis) {
        GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis)
    }

    // Placing the CTA in the bottomBar slot of the Scaffold ensures that the
    // SnackbarHost floats above it, preventing UI obstruction.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Share Your Available Times") },
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
                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Availability submitted for $participantName")
                        }
                        viewModel.submitAvailability()
                        onSubmitted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = draftAvailability.isNotEmpty(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Submit Availability")
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
                            text = "Responding as: $participantName (${participantIndex + 1}/${viewModel.totalParticipants()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    restrictedCells = if (isOrganizer) emptySet() else organizerRestrictions,
                    participantColor = participantColor,
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
    restrictedCells: Set<Int>,
    participantColor: Color,
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
                        .pointerInput(gridConfig, restrictedCells, cellWidth) {
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
                                    if (idx !in restrictedCells) onCellToggled(idx)
                                }
                            )
                        }
                        .pointerInput(gridConfig, restrictedCells, cellWidth) {
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
                                    if (idx !in restrictedCells) {
                                        onCellPainted(idx)
                                        lastPainted = idx
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val idx = cellAt(change.position)
                                    if (idx != lastPainted && idx !in restrictedCells) {
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
                                    val isRestricted = cellIndex in restrictedCells

                                    val outlineColor = MaterialTheme.colorScheme.outline
                                    val restrictionColor = MaterialTheme.colorScheme.onSurfaceVariant

                                    Box(
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                if (isSelected) {
                                                    scaleX = 1.05f
                                                    scaleY = 1.05f
                                                }
                                            }
                                            .background(
                                                when {
                                                    isRestricted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    isSelected -> participantColor
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .then(
                                                if (isRestricted) {
                                                    Modifier.drawBehind {
                                                        val step = 8.dp.toPx()
                                                        val strokeColor = restrictionColor.copy(alpha = 0.2f)
                                                        var x = 0f
                                                        while (x < size.width + size.height) {
                                                            drawLine(
                                                                color = strokeColor,
                                                                start = Offset(x, 0f),
                                                                end = Offset(x - size.height, size.height),
                                                                strokeWidth = 1.dp.toPx(),
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                                                            )
                                                            x += step
                                                        }
                                                    }
                                                } else Modifier
                                            )
                                            .border(width = 0.5.dp, color = outlineColor.copy(alpha = 0.2f))
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
