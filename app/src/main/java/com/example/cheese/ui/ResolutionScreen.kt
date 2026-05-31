package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
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
import com.example.cheese.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightSageGreen = Color(0xFFD5E8D4)
private val MediumMintGreen = Color(0xFFA5D6A7)
private val VibrantEmeraldGreen = Color(0xFF4CAF50)
private val DeepForestGreen = Color(0xFF1B5E20)

private fun heatColor(ratio: Float): Color {
    return when {
        ratio <= 0f -> Color.Transparent
        ratio <= 0.25f -> LightSageGreen
        ratio <= 0.50f -> MediumMintGreen
        ratio <= 0.75f -> VibrantEmeraldGreen
        else -> DeepForestGreen
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

    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis) {
        GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis)
    }

    val heatmap = remember(responses) { viewModel.computeHeatmap(gridConfig) }
    val optimalCell = remember(responses) { viewModel.computeOptimalCell(gridConfig) }
    val totalParticipants = eventRequest.invitees.size

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCell by remember(optimalCell) { mutableStateOf(optimalCell) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Final Consensus — ${eventRequest.eventName}")
                },
                navigationIcon = {
                    IconButton(onClick = onEditEvent) {
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

            // Gradient legend
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
                    DeepForestGreen
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
                Text("Optimal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            // Heatmap grid with horizontal + vertical scrolling
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                HeatmapGrid(
                    gridConfig = gridConfig,
                    heatmap = heatmap,
                    totalParticipants = totalParticipants,
                    optimalCell = optimalCell,
                    selectedCell = selectedCell,
                    onCellTapped = { selectedCell = it }
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        selectedCell?.let { viewModel.setFinalEvent(it) }
                        showBottomSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = selectedCell != null,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Set Final Event")
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
                selectedCell = selectedCell,
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
    optimalCell: Int?,
    selectedCell: Int?,
    onCellTapped: (Int) -> Unit
) {
    val labelColWidth = 48.dp
    val cellHeight = 40.dp

    val infiniteTransition = rememberInfiniteTransition(label = "optimalPulse")
    val pulseWidth by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorderWidth"
    )

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
                            text = label,
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
                            val isOptimal = cellIndex == optimalCell
                            val isSelected = cellIndex == selectedCell

                            val borderWidth = when {
                                isOptimal -> pulseWidth.dp
                                isSelected -> 2.dp
                                else -> 0.5.dp
                            }
                            val tertiaryColor = MaterialTheme.colorScheme.tertiary
                            val outlineColor = MaterialTheme.colorScheme.outline
                            val borderColor = when {
                                isOptimal -> tertiaryColor
                                isSelected -> tertiaryColor.copy(alpha = 0.7f)
                                else -> outlineColor.copy(alpha = 0.2f)
                            }

                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .fillMaxHeight()
                                    .padding(1.5.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (count > 0) heatColor(ratio)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .then(
                                        if (isOptimal || isSelected) {
                                            Modifier.border(
                                                width = borderWidth,
                                                color = borderColor,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable { if (count > 0) onCellTapped(cellIndex) },
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

@Composable
private fun FinalSummarySheet(
    gridConfig: GridConfig,
    eventRequest: EventRequest,
    selectedCell: Int?,
    responses: Map<String, ParticipantResponse>,
    totalParticipants: Int,
    heatmap: Map<Int, Int>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val dayLabel = selectedCell?.let { gridConfig.cellToDay(it) } ?: "—"
    val hourLabel = selectedCell?.let { gridConfig.cellToHour(it) } ?: "—"

    val consensusCount = selectedCell?.let { heatmap[it] } ?: 0
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
        SummaryRow(label = "Time", value = hourLabel)
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

        val gridConfig = GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)
        val selectedTimestamp = selectedCell?.let { gridConfig.cellToTimestamp(it) }

        eventRequest.invitees.forEach { invitee ->
            val response = responses[invitee.name]
            val isAvailable = selectedTimestamp != null && response?.availability?.contains(selectedTimestamp) == true

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.width(90.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                LinearProgressIndicator(
                    progress = { if (isAvailable) 1f else 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = if (isAvailable) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = if (isAvailable) "Available" else "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAvailable) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
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
