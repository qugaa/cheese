package com.example.cheese.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cheese.viewmodel.ScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailsScreen(
    friendName: String,
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()

    // Filter events involving this friend
    val sharedEvents = events.filter { event ->
        event.request.invitees.any { it.name == friendName }
    }

    val now = System.currentTimeMillis()
    val upcomingEvents = sharedEvents.filter { it.request.endDateMillis >= now }.sortedWith(eventComparator)
    val pastEvents = sharedEvents.filter { it.request.endDateMillis < now }.sortedWith(eventComparator)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(friendName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Upcoming Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            
            if (upcomingEvents.isEmpty()) {
                item {
                    Text(
                        text = "No upcoming events with $friendName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(upcomingEvents, key = { it.request.id }) { eventState ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        EventCard(
                            eventState = eventState,
                            onDelete = { viewModel.deleteEvent(eventState.request.id) },
                            onClick = {
                                viewModel.selectEvent(eventState.request.id)
                                onOpenEvent(eventState.request.id)
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Past Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }

            if (pastEvents.isEmpty()) {
                item {
                    Text(
                        text = "No past events with $friendName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(pastEvents, key = { it.request.id }) { eventState ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        EventCard(
                            eventState = eventState,
                            onDelete = { viewModel.deleteEvent(eventState.request.id) },
                            onClick = {
                                viewModel.selectEvent(eventState.request.id)
                                onOpenEvent(eventState.request.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
