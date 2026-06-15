package com.example.cheese.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.GridConfig
import com.example.cheese.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    viewModel: ScheduleViewModel,
    onEditChoice: () -> Unit,
    onBack: () -> Unit
) {
    val currentEventId by viewModel.currentEventId.collectAsState()
    val events by viewModel.events.collectAsState()
    val eventState = events.find { it.request.id == currentEventId }
    val eventRequest = eventState?.request ?: viewModel.eventRequest.collectAsState().value
    val responses = eventState?.responses ?: emptyMap()
    val finalCellIndex = eventState?.finalCellIndex
    val finalCellEndIndex = eventState?.finalCellEndIndex
    val dateOnly = eventRequest.dateOnlyMode
    
    val gridConfig = remember(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour, dateOnly) {
        if (dateOnly) {
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, 0, 1)
        } else {
            GridConfig(eventRequest.startDateMillis, eventRequest.endDateMillis, eventRequest.startHour, eventRequest.endHour)
        }
    }
    val heatmap = remember(responses) { viewModel.computeHeatmap(gridConfig) }
    val totalParticipants = eventRequest.invitees.size

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    fun timeKey(index: Int): Int =
        (index % gridConfig.cols) * gridConfig.rows + (index / gridConfig.cols)

    val startCell = finalCellIndex
    val endCell = finalCellEndIndex ?: finalCellIndex

    val dayLabel = remember(startCell, endCell, gridConfig, dateOnly) {
        if (startCell == null) "—"
        else if (endCell == null || startCell == endCell) gridConfig.cellToDay(startCell)
        else {
            val sCol = startCell % gridConfig.cols
            val eCol = endCell % gridConfig.cols
            val minCol = minOf(sCol, eCol)
            val maxCol = maxOf(sCol, eCol)
            if (minCol == maxCol) gridConfig.cellToDay(startCell)
            else "${gridConfig.dayLabels.getOrElse(minCol) { "?" }} → ${gridConfig.dayLabels.getOrElse(maxCol) { "?" }}"
        }
    }

    val hourLabel = remember(startCell, endCell, gridConfig, dateOnly) {
        if (startCell == null) "—"
        else if (dateOnly) "All day"
        else if (endCell == null || startCell == endCell) gridConfig.cellToHour(startCell)
        else {
            val sRow = startCell / gridConfig.cols
            val eRow = endCell / gridConfig.cols
            val minRow = minOf(sRow, eRow)
            val maxRow = maxOf(sRow, eRow)
            "${gridConfig.hourLabels.getOrElse(minRow) { "?" }} → ${gridConfig.hourLabels.getOrElse(maxRow) { "?" }}"
        }
    }

    val consensusCount = remember(startCell, endCell, responses, gridConfig, eventRequest, dateOnly) {
        if (startCell == null) 0
        else {
            val endVal = endCell ?: startCell
            val minCell = minOf(startCell, endVal)
            val maxCell = maxOf(startCell, endVal)
            val timestamps = if (dateOnly) {
                (minCell..maxCell).map { gridConfig.cellToTimestamp(it) }
            } else {
                val minKey = minOf(timeKey(startCell), timeKey(endVal))
                val maxKey = maxOf(timeKey(startCell), timeKey(endVal))
                (0 until gridConfig.totalCells).filter { cell ->
                    timeKey(cell) in minKey..maxKey
                }.map { gridConfig.cellToTimestamp(it) }
            }
            eventRequest.invitees.count { invitee ->
                responses[invitee.name]?.let { r ->
                    timestamps.isNotEmpty() && timestamps.all { ts -> r.availability.contains(ts) }
                } == true
            }
        }
    }
    val consensusPct = if (totalParticipants > 0) (consensusCount * 100f / totalParticipants).toInt() else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditChoice) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Choice")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${eventRequest.eventEmoji} ${eventRequest.eventName.ifBlank { "Untitled" }}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SummaryRow(
                        label = "Window",
                        value = if (eventRequest.startDateMillis > 0L && eventRequest.endDateMillis > 0L)
                            "${dateFormatter.format(Date(eventRequest.startDateMillis))} → ${dateFormatter.format(Date(eventRequest.endDateMillis))}"
                        else "Not specified"
                    )
                    SummaryRow(label = "Day", value = dayLabel)
                    SummaryRow(label = "Time", value = if (eventRequest.dateOnlyMode) "All day" else hourLabel)
                    SummaryRow(
                        label = "Consensus",
                        value = "$consensusCount / $totalParticipants participants ($consensusPct%)"
                    )
                }
            }

            Text(
                text = "Participant Availability",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val selectedTimestamps = remember(startCell, endCell, gridConfig, dateOnly) {
                        if (startCell == null) emptyList()
                        else {
                            val endVal = endCell ?: startCell
                            val minCell = minOf(startCell, endVal)
                            val maxCell = maxOf(startCell, endVal)
                            val cellRange = if (dateOnly) {
                                minCell..maxCell
                            } else {
                                val minKey = minOf(timeKey(startCell), timeKey(endVal))
                                val maxKey = maxOf(timeKey(startCell), timeKey(endVal))
                                (0 until gridConfig.totalCells).filter { cell ->
                                    timeKey(cell) in minKey..maxKey
                                }
                            }
                            cellRange.map { gridConfig.cellToTimestamp(it) }
                        }
                    }

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
                }
            }
            
            Button(
                onClick = {
                    currentEventId?.let { id ->
                        viewModel.deleteEvent(id)
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Delete Event", color = MaterialTheme.colorScheme.onError)
            }

            Spacer(Modifier.height(80.dp)) // padding for FAB
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
