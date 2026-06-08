package com.example.cheese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cheese.data.DateOffset
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCreateScreen(
    viewModel: ScheduleViewModel,
    onProceed: () -> Unit,
    onAdvancedSetup: () -> Unit,
    onBack: () -> Unit
) {
    val eventRequest by viewModel.eventRequest.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var newFriendName by remember { mutableStateOf("") }
    
    // Default smart date selection (based on the template if any)
    // We infer the current selection based on the start date
    var selectedOffset by remember { mutableStateOf(DateOffset.TODAY) }

    // If there is no specific date offset saved in the template (since we removed it), we can default to Custom or let the user choose.
    // Wait, EventTemplate still has `dateOffset: DateOffset`. So we can use it!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Create: ${eventRequest.eventEmoji} ${eventRequest.eventName}") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // ── Section 1: Who is joining? ──────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Who is joining?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select at least 2 people.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Selected Invitees Chips
                    if (eventRequest.invitees.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            eventRequest.invitees.forEach { invitee ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(invitee.name) },
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
                                            contentDescription = "Remove",
                                            modifier = Modifier.clickable {
                                                viewModel.removeInvitee(invitee.name)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Quick Add Friends
                    if (friends.isNotEmpty()) {
                        Text("Saved Friends", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(friends) { friend ->
                                val isAdded = eventRequest.invitees.any { it.name == friend.name }
                                FilterChip(
                                    selected = isAdded,
                                    onClick = {
                                        if (isAdded) {
                                            viewModel.removeInvitee(friend.name)
                                        } else {
                                            viewModel.addInvitee(friend.name)
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    label = { Text(friend.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(CuratedParticipantColors[friend.colorIndex], CircleShape)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Add new person
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFriendName,
                            onValueChange = { newFriendName = it },
                            placeholder = { Text("Type name...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newFriendName.isNotBlank()) {
                                    viewModel.addInvitee(newFriendName)
                                    // Automatically save them as a friend as requested!
                                    val newInvitee = viewModel.eventRequest.value.invitees.lastOrNull { it.name == newFriendName }
                                    if (newInvitee != null) {
                                        viewModel.addFriend(newInvitee.name, newInvitee.colorIndex)
                                    }
                                    newFriendName = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            // ── Section 2: When? ────────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "When?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val today = remember { LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    val tomorrow = remember { LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    val weekendStart = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.SUNDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        }
                    }
                    val weekendEnd = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        } else if (t.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                            t.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        }
                    }
                    val weekdaysStart = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.FRIDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY || t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        }
                    }
                    val weekdaysEnd = remember {
                        val t = LocalDate.now(ZoneOffset.UTC)
                        if (t.dayOfWeek == java.time.DayOfWeek.FRIDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY || t.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                            t.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        } else {
                            t.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        }
                    }

                    // Smart Date Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { 
                                selectedOffset = DateOffset.TODAY
                                viewModel.updateDateRange(today, today) 
                            }, 
                            label = { Text("Today") },
                        )
                        SuggestionChip(
                            onClick = { 
                                selectedOffset = DateOffset.TOMORROW
                                viewModel.updateDateRange(tomorrow, tomorrow) 
                            }, 
                            label = { Text("Tomorrow") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { 
                                selectedOffset = DateOffset.CUSTOM
                                viewModel.updateDateRange(weekdaysStart, weekdaysEnd) 
                            }, 
                            label = { Text("Weekdays") }
                        )
                        SuggestionChip(
                            onClick = { 
                                selectedOffset = DateOffset.WEEKEND
                                viewModel.updateDateRange(weekendStart, weekendEnd) 
                            }, 
                            label = { Text("Weekend") }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Section 3: Actions ──────────────────────────────────────────────
            OutlinedButton(
                onClick = onAdvancedSetup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Change specific time/dates")
            }

            Button(
                onClick = {
                    if (eventRequest.invitees.size < 2) {
                        scope.launch {
                            snackbarHostState.showSnackbar("You must select at least 2 people.")
                        }
                        return@Button
                    }
                    onProceed()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Request Availability")
            }
        }
    }
}
