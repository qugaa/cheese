package com.example.cheese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.GridConfig
import com.example.cheese.data.MOCK_PARTICIPANTS
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch


/**
 * VIEW 2 — Participant Availability Input
 *
 * HCI rationale:
 * - Drag-to-paint interaction replaces N individual toggle switches.
 *   Per Fitts' Law, a single continuous gesture over a wide surface is far
 *   faster than N discrete point-click operations spread across the display.
 * - The grid cell width fills the available screen width proportionally,
 *   ensuring touch targets meet the 44 dp minimum recommended by Apple HIG
 *   and the 48 dp guideline in Material Design.
 * - Green fill provides immediate visual feedback (Norman: feedback principle),
 *   confirming which cells are selected without a secondary confirmation step.
 * - "Submit Availability" is sticky at the bottom (Noun-Verb: user selects
 *   cells (Noun) then submits (Verb)), preventing premature commit errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantScreen(
    viewModel: ScheduleViewModel,
    onSubmitted: () -> Unit
) {
    val draftAvailability by viewModel.draftAvailability.collectAsState()
    val participantIndex by viewModel.currentParticipantIndex.collectAsState()
    val participantName = MOCK_PARTICIPANTS[participantIndex]
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Your Availability — $participantName") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Instruction banner — reduces cold-start uncertainty (Norman: knowledge
            // in the world vs. knowledge in the head).
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Drag across time slots to paint your availability",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // Scrollable grid area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AvailabilityGrid(
                    selectedCells = draftAvailability,
                    onCellPainted = { viewModel.paintCell(it) }
                )
            }

            // ── Primary CTA — sticky bottom ───────────────────────────────────
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
                enabled = draftAvailability.isNotEmpty()
            ) {
                Text("Submit Availability")
            }
        }
    }
}

/**
 * The drag-to-paint availability grid.
 *
 * Implementation notes:
 * - [detectDragGestures] provides a single continuous pointer stream; we convert
 *   the drag position to a (row, col) pair on every move event.
 * - We record grid root position via [onGloballyPositioned] so that absolute
 *   pointer coordinates can be mapped to local grid coordinates reliably,
 *   regardless of scroll offset or parent padding.
 * - Cell size is derived from BoxWithConstraints so the grid always fills its
 *   parent width — no hardcoded dp values that would break on different densities.
 */
@Composable
private fun AvailabilityGrid(
    selectedCells: Set<Int>,
    onCellPainted: (Int) -> Unit
) {
    val density = LocalDensity.current
    val labelColWidth: Dp = 48.dp

    // Grid root position in root coordinates — needed to map drag offset to cell.
    var gridTopLeft by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = labelColWidth, end = 4.dp)
    ) {
        val cellWidth: Dp = maxWidth / GridConfig.COLS
        val cellHeight: Dp = 36.dp

        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Day header row ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                GridConfig.DAY_LABELS.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.width(cellWidth),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Cell grid with drag detector ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        gridTopLeft = coords.positionInRoot()
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // Resolve start cell on drag initiation.
                                val col = (offset.x / with(density) { cellWidth.toPx() })
                                    .toInt().coerceIn(0, GridConfig.COLS - 1)
                                val row = (offset.y / with(density) { cellHeight.toPx() })
                                    .toInt().coerceIn(0, GridConfig.ROWS - 1)
                                onCellPainted(GridConfig.cellIndex(row, col))
                            },
                            onDrag = { change, _ ->
                                // Resolve cell from pointer position relative to grid Box.
                                val localX = change.position.x
                                val localY = change.position.y
                                val col = (localX / with(density) { cellWidth.toPx() })
                                    .toInt().coerceIn(0, GridConfig.COLS - 1)
                                val row = (localY / with(density) { cellHeight.toPx() })
                                    .toInt().coerceIn(0, GridConfig.ROWS - 1)
                                onCellPainted(GridConfig.cellIndex(row, col))
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GridConfig.HOUR_LABELS.forEachIndexed { rowIdx, _ ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                        ) {
                            repeat(GridConfig.COLS) { colIdx ->
                                val cellIndex = GridConfig.cellIndex(rowIdx, colIdx)
                                val isSelected = cellIndex in selectedCells
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                        .background(
                                            if (isSelected)
                                                Color(0xFF2E7D32) // Material Green 800
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Hour labels column — overlaid to the left of the grid ────────────────
    Column(
        modifier = Modifier
            .width(labelColWidth)
            .padding(top = 20.dp) // offset past the day-header row
    ) {
        GridConfig.HOUR_LABELS.forEach { label ->
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(labelColWidth),
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
}
