package com.example.cheese.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


/**
 * VIEW 1 — Organizer Initiation
 *
 * HCI rationale:
 * - OutlinedTextField provides affordance cues (label, border) reducing learnability
 *   friction per Norman's theory of affordances.
 * - DatePickerDialog is a Modal widget; modality is acceptable here because date
 *   selection is an episodic, bounded task that naturally scopes attention.
 * - The "Request Availability" CTA is bottom-anchored and fillMaxWidth to maximise
 *   Fitts' Law target area — the single primary action of this screen.
 * - Snackbar feedback ("Group Request Sent") is non-modal and self-dismissing,
 *   preserving the user's locus of attention without demanding acknowledgement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(
    viewModel: ScheduleViewModel,
    onRequestSent: () -> Unit
) {
    val eventRequest by viewModel.eventRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Date picker dialog visibility flags
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Material 3 DatePickerState instances
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cheese — Schedule Event") },
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
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Event Name ────────────────────────────────────────────────────
            // OutlinedTextField: visible border acts as a persistent affordance
            // (Gibson), signalling editability without requiring prior learning.
            OutlinedTextField(
                value = eventRequest.eventName,
                onValueChange = { viewModel.updateEventName(it) },
                label = { Text("Event Name") },
                placeholder = { Text("e.g. Team Retrospective") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Date Pickers ──────────────────────────────────────────────────
            Text(
                text = "Availability Window",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Start date read-only field — clicking opens the DatePickerDialog.
            OutlinedTextField(
                value = if (eventRequest.startDateMillis > 0L)
                    dateFormatter.format(Date(eventRequest.startDateMillis)) else "",
                onValueChange = {},
                label = { Text("From") },
                placeholder = { Text("Select start date") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showStartPicker = true }) {
                        Text("Pick")
                    }
                }
            )

            // End date read-only field.
            OutlinedTextField(
                value = if (eventRequest.endDateMillis > 0L)
                    dateFormatter.format(Date(eventRequest.endDateMillis)) else "",
                onValueChange = {},
                label = { Text("To") },
                placeholder = { Text("Select end date") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showEndPicker = true }) {
                        Text("Pick")
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            // ── Primary CTA ───────────────────────────────────────────────────
            // fillMaxWidth: maximises Fitts' Law target width; no Spacer between
            // the button and the bottom edge so the travel distance from the
            // primary content area is minimised.
            Button(
                onClick = {
                    viewModel.updateStartDate(startPickerState.selectedDateMillis ?: eventRequest.startDateMillis)
                    viewModel.updateEndDate(endPickerState.selectedDateMillis ?: eventRequest.endDateMillis)
                    // Snackbar: transient, non-blocking confirmation — preserves
                    // locus of attention (Wickens' Multiple Resource Theory).
                    scope.launch {
                        snackbarHostState.showSnackbar("Group Request Sent")
                    }
                    onRequestSent()

                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = eventRequest.eventName.isNotBlank()
            ) {
                Text("Request Availability")
            }
        }
    }

    // ── Start Date Picker Dialog ───────────────────────────────────────────────
    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startPickerState.selectedDateMillis?.let { viewModel.updateStartDate(it) }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startPickerState)
        }
    }

    // ── End Date Picker Dialog ─────────────────────────────────────────────────
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endPickerState.selectedDateMillis?.let { viewModel.updateEndDate(it) }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endPickerState)
        }
    }
}
