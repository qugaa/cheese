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
import androidx.compose.material.icons.filled.Check
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.cheese.ui.theme.CuratedParticipantColors
import com.example.cheese.data.GridConfig
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.imePadding()) },
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

                // Firestore user search (debounced) + just-added tracking for the + → ✓ flow
                var dbResults by remember { mutableStateOf<List<String>>(emptyList()) }
                var searchCompleted by remember { mutableStateOf(false) }
                var justAdded by remember { mutableStateOf<Set<String>>(emptySet()) }

                LaunchedEffect(searchQuery) {
                    searchCompleted = false
                    if (searchQuery.isBlank()) {
                        dbResults = emptyList()
                        return@LaunchedEffect
                    }
                    delay(300) // debounce while the user is typing
                    viewModel.searchUsers(searchQuery) { results ->
                        dbResults = results
                        searchCompleted = true
                    }
                }

                val filteredFriends = friends.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
                val friendNames = friends.map { it.name }.toSet()
                // Users found in the DB who are not yet friends (and not the current user).
                // Keep just-added names visible briefly so the ✓ can be shown before the row disappears.
                val nonFriendResults = dbResults.filter { name ->
                    name != currentUser && (name !in friendNames || name in justAdded)
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

                        // Users in the DB who are not yet friends
                        if (searchQuery.isNotBlank() && nonFriendResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "People on Cheese",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(nonFriendResults, key = { "db_$it" }) { name ->
                                val added = name in justAdded
                                ListItem(
                                    headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name.firstOrNull()?.uppercase() ?: "?",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        if (added) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Added",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            IconButton(onClick = {
                                                viewModel.addFriend(name) { success, msg ->
                                                    if (success) {
                                                        justAdded = justAdded + name
                                                        coroutineScope.launch {
                                                            // Show the checkmark for a moment, then let the row disappear
                                                            delay(1200)
                                                            justAdded = justAdded - name
                                                        }
                                                    } else {
                                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                                    }
                                                }
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add $name as friend",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        // No user found anywhere
                        if (searchQuery.isNotBlank() && searchCompleted &&
                            filteredFriends.isEmpty() && nonFriendResults.isEmpty()
                        ) {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            "No user found",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 100.dp.toPx() }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Using IntrinsicSize.Min ensures the background box height matches the Card height
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
    ) {
        // Background - Delete Button
        val color = MaterialTheme.colorScheme.errorContainer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, RoundedCornerShape(12.dp))
                .clickable {
                    onDelete()
                }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        // Foreground - Event Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -maxRevealPx / 2) {
                                    offsetX.animateTo(-maxRevealPx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-maxRevealPx * 1.5f, 0f))
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = {
                if (offsetX.value < -10f) {
                    coroutineScope.launch { offsetX.animateTo(0f) }
                } else {
                    onClick()
                }
            }
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
                            text = if (eventState.request.dateOnlyMode) "$dayStr (all day)" else "$dayStr, $hourStr",
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
}
