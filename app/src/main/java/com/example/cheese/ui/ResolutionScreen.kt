package com.example.cheese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.GridConfig
import com.example.cheese.data.MOCK_PARTICIPANTS
import com.example.cheese.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * VIEW 3 — Algorithmic Resolution & Finalization
 *
 * HCI rationale:
 * - Color-coded heatmap encodes participant consensus as a perceptual variable
 *   (Bertin's visual variables) — saturation level directly maps to agreement
 *   density, enabling pre-attentive processing without conscious counting.
 * - The selected optimal cell is highlighted with a distinct accent border
 *   (figure-ground principle) to guide the organizer's locus of attention.
 * - ModalBottomSheet for the final summary is a spatially contextual overlay —
 *   it does not fully remove the underlying heatmap from peripheral vision,
 *   maintaining spatial memory while presenting the finalization summary.
 * - "Set Final Event" CTA uses fillMaxWidth() per Fitts' Law.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit
) {
    val eventRequest by viewModel.eventRequest.collectAsState()
    val finalCellIndex by viewModel.finalCellIndex.collectAsState()
    val responses by viewModel.responses.collectAsState()

    // Derived heatmap and optimal cell — recomputed on every recomposition
    // triggered by [responses] changes (snapshot reads propagate correctly).
    val heatmap = remember(responses) { viewModel.computeHeatmap() }
    val optimalCell = remember(responses) { viewModel.computeOptimalCell() }
    val maxCount = heatmap.values.maxOrNull()?.takeIf { it > 0 } ?: 1

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Track currently selected cell for "Set Final Event" — defaults to optimal.
    var selectedCell by remember(optimalCell) { mutableStateOf(optimalCell) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Availability Summary") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ── Stats bar ─────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
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
                        text = eventRequest.eventName.ifBlank { "Untitled Event" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${responses.size} / ${MOCK_PARTICIPANTS.size} responded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Low", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                // Gradient swatch
                Row(modifier = Modifier.weight(1f).height(10.dp)) {
                    for (i in 0..4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(heatColor(i.toFloat() / 4f))
                        )
                    }
                }
                Text("High", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Transparent)
                        .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp))
                )
                Text("Optimal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            // ── Heatmap grid ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                HeatmapGrid(
                    heatmap = heatmap,
                    maxCount = maxCount,
                    optimalCell = optimalCell,
                    selectedCell = selectedCell,
                    onCellTapped = { selectedCell = it }
                )
            }

            // ── Primary CTA ───────────────────────────────────────────────────
            Button(
                onClick = {
                    selectedCell?.let { viewModel.setFinalEvent(it) }
                    showBottomSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = selectedCell != null
            ) {
                Text("Set Final Event")
            }
        }
    }

    // ── ModalBottomSheet — Final Summary ──────────────────────────────────────
    // Non-destructive overlay: the heatmap remains visible behind the sheet,
    // preserving spatial context for the decision (Locus of Attention principle).
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            FinalSummarySheet(
                eventRequest = eventRequest,
                selectedCell = selectedCell,
                totalParticipants = responses.size,
                heatmap = heatmap,
                maxCount = maxCount,
                onDismiss = { showBottomSheet = false }
            )
        }
    }
}

/**
 * Heatmap grid rendering.
 * Each cell is colored by [heatColor] derived from the ratio count/maxCount.
 * Tapping a cell selects it as the proposed final slot (Noun selection).
 */
@Composable
private fun HeatmapGrid(
    heatmap: Map<Int, Int>,
    maxCount: Int,
    optimalCell: Int?,
    selectedCell: Int?,
    onCellTapped: (Int) -> Unit
) {
    val labelColWidth = 48.dp
    val cellHeight = 36.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = labelColWidth, end = 4.dp)
    ) {
        val cellWidth = maxWidth / GridConfig.COLS

        Column(modifier = Modifier.fillMaxWidth()) {

            // Day headers
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

            // Heat cells
            GridConfig.HOUR_LABELS.forEachIndexed { rowIdx, _ ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellHeight)
                ) {
                    repeat(GridConfig.COLS) { colIdx ->
                        val cellIndex = GridConfig.cellIndex(rowIdx, colIdx)
                        val count = heatmap[cellIndex] ?: 0
                        val ratio = count.toFloat() / maxCount
                        val isOptimal = cellIndex == optimalCell
                        val isSelected = cellIndex == selectedCell

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .background(if (count > 0) heatColor(ratio) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 2.dp else if (isOptimal) 1.5.dp else 0.5.dp,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.tertiary
                                        isOptimal -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    }
                                )
                                .clickable { if (count > 0) onCellTapped(cellIndex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (count > 0) {
                                Text(
                                    text = "$count",
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Hour label column
    Column(
        modifier = Modifier
            .width(labelColWidth)
            .padding(top = 20.dp)
    ) {
        GridConfig.HOUR_LABELS.forEach { label ->
            Box(
                modifier = Modifier
                    .height(cellHeight)
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

/**
 * Maps a normalised ratio [0, 1] to a heat color.
 * Uses Material Green palette: low consensus → light green, high → deep green.
 * Strict monotonic saturation increase ensures pre-attentive pop-out of
 * high-consensus slots without requiring legend consultation.
 */
private fun heatColor(ratio: Float): Color {
    val low = Color(0xFFA5D6A7)   // Green 200
    val high = Color(0xFF1B5E20)  // Green 900
    return lerp(low, high, ratio.coerceIn(0f, 1f))
}

/**
 * Bottom sheet content: final calendar invitation summary.
 */
@Composable
private fun FinalSummarySheet(
    eventRequest: com.example.cheese.data.EventRequest,
    selectedCell: Int?,
    totalParticipants: Int,
    heatmap: Map<Int, Int>,
    maxCount: Int,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Decode selected cell to human-readable time
    val (dayLabel, hourLabel) = if (selectedCell != null) {
        val row = selectedCell / GridConfig.COLS
        val col = selectedCell % GridConfig.COLS
        GridConfig.DAY_LABELS.getOrElse(col) { "?" } to GridConfig.HOUR_LABELS.getOrElse(row) { "?" }
    } else {
        "—" to "—"
    }

    val consensusCount = selectedCell?.let { heatmap[it] } ?: 0
    val consensusPct = if (totalParticipants > 0)
        (consensusCount * 100f / totalParticipants).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📅 Event Confirmed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        SummaryRow(label = "Event", value = eventRequest.eventName.ifBlank { "Untitled" })
        SummaryRow(
            label = "Window",
            value = if (eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L)
                "${dateFormatter.format(Date(eventRequest.startDateMillis))} → ${dateFormatter.format(Date(eventRequest.endDateMillis))}"
            else "Not specified"
        )
        SummaryRow(label = "Day", value = dayLabel)
        SummaryRow(label = "Time", value = hourLabel)
        SummaryRow(label = "Consensus", value = "$consensusCount / $totalParticipants participants ($consensusPct%)")

        Spacer(Modifier.height(8.dp))

        // Consensus indicator bar
        LinearProgressIndicator(
            progress = { consensusCount.toFloat() / maxCount.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = "Participant agreement: $consensusPct%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
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
