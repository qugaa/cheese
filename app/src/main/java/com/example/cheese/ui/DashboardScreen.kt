package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cheese.data.EventState
import com.example.cheese.data.EventTemplate
import com.example.cheese.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.data.GridConfig

val eventComparator = java.util.Comparator<EventState> { a, b ->
    val aConfirmed = a.finalCellIndex != null
    val bConfirmed = b.finalCellIndex != null
    if (aConfirmed != bConfirmed) {
        return@Comparator if (aConfirmed) 1 else -1
    }

    if (!aConfirmed) {
        val startDiff = a.request.startDateMillis.compareTo(b.request.startDateMillis)
        if (startDiff != 0) return@Comparator startDiff
    } else {
        val aConfig = GridConfig(a.request.startDateMillis, a.request.endDateMillis, a.request.startHour, a.request.endHour)
        val aTime = aConfig.cellToTimestamp(a.finalCellIndex!!)
        
        val bConfig = GridConfig(b.request.startDateMillis, b.request.endDateMillis, b.request.startHour, b.request.endHour)
        val bTime = bConfig.cellToTimestamp(b.finalCellIndex!!)
        
        val timeDiff = aTime.compareTo(bTime)
        if (timeDiff != 0) return@Comparator timeDiff
    }

    a.request.eventName.compareTo(b.request.eventName, ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScheduleViewModel,
    onCreateNewEvent: () -> Unit,
    onQuickCreate: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onFriendClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    val events by viewModel.events.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val dashboardMessage by viewModel.dashboardMessage.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Events", "Friends")

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            onLogout()
        }
    }

    LaunchedEffect(dashboardMessage) {
        dashboardMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearDashboardMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(currentUser?.let { "Welcome, $it" } ?: "My Events") },
                actions = {
                    TextButton(onClick = { viewModel.logout() }) {
                        Text(
                            text = "Logout",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = {
                        viewModel.createNewEvent()
                        onCreateNewEvent()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create New Event")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (selectedTabIndex == 0) {
                // Events Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Text(
                            text = "Quick Create",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(templates, key = { it.id }) { template ->
                                TemplateCard(template) {
                                    viewModel.createFromTemplate(template)
                                    onQuickCreate()
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Upcoming Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }

                    if (events.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No events scheduled.\nTap a template or the + button.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val sortedEvents = events.sortedWith(eventComparator)
                        items(sortedEvents, key = { it.request.id }) { eventState ->
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
            } else {
                // Friends Tab
                var searchQuery by remember { mutableStateOf("") }
                val haptic = LocalHapticFeedback.current

                val filteredFriends = friends.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        placeholder = { Text("Search friends...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredFriends, key = { it.name }) { friend ->
                            ListItem(
                                headlineContent = { Text(friend.name, fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(CuratedParticipantColors[friend.colorIndex], CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = friend.name.firstOrNull()?.uppercase() ?: "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    onFriendClick(friend.name)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        // Add friend item if query is not blank and no exact match
                        if (searchQuery.isNotBlank() && friends.none { it.name.equals(searchQuery, ignoreCase = true) }) {
                            item {
                                ListItem(
                                    headlineContent = { Text("Add '$searchQuery' as friend", color = MaterialTheme.colorScheme.primary) },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        viewModel.addFriend(searchQuery.trim()) { success, msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                            if (success) {
                                                searchQuery = ""
                                            }
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(template: EventTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = template.emoji, fontSize = 24.sp)
            }
            Column {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
                Text(
                    text = template.dateOffset.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    eventState: EventState,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = onClick
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji container
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = eventState.request.eventEmoji,
                            fontSize = 24.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eventState.request.eventName.ifBlank { "Untitled Event" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val responded = eventState.responses.size
                        val total = eventState.request.invitees.size
                        val finalIndex = eventState.finalCellIndex
                        
                        if (finalIndex != null) {
                            val config = com.example.cheese.data.GridConfig(
                                eventState.request.startDateMillis,
                                eventState.request.endDateMillis,
                                eventState.request.startHour,
                                eventState.request.endHour
                            )
                            val dayStr = config.cellToDay(finalIndex)
                            val hourStr = config.cellToHour(finalIndex)
                            Text(
                                text = "$dayStr, $hourStr",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "$responded / $total responded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}
