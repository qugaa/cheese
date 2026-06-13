package com.example.cheese.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    var successOverlayMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dashboardMessage) {
        dashboardMessage?.let { msg ->
            if (msg == "Event Confirmed!" || msg == "Event Created!" || msg == "Availabilities Sent!") {
                successOverlayMessage = msg
                viewModel.clearDashboardMessage()
            } else {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearDashboardMessage()
            }
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
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.createNewEvent()
                        onCreateNewEvent()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create New Event") },
                    text = { Text("CREATE EVENT", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                val finalizedEvents = remember(events) {
                    events.filter { it.finalCellIndex != null }.sortedWith(eventComparator)
                }
                val unconfirmedEvents = remember(events) {
                    events.filter { it.finalCellIndex == null }
                }
                val actionNeededEvents = remember(unconfirmedEvents, currentUser) {
                    unconfirmedEvents.filter { eventState ->
                        val hostName = eventState.request.invitees.firstOrNull()?.name
                        val isHost = hostName == currentUser
                        val hostHasSubmitted = hostName != null && eventState.responses.containsKey(hostName)
                        val hasSubmitted = eventState.responses.containsKey(currentUser)
                        val allResponded = eventState.responses.size >= eventState.request.invitees.size && eventState.request.invitees.isNotEmpty()
                        
                        if (isHost) {
                            !hasSubmitted || allResponded
                        } else {
                            hostHasSubmitted && !hasSubmitted
                        }
                    }.sortedByDescending { it.request.createdAt }
                }
                val waitingOnOthersEvents = remember(unconfirmedEvents, currentUser) {
                    unconfirmedEvents.filter { eventState ->
                        val hostName = eventState.request.invitees.firstOrNull()?.name
                        val isHost = hostName == currentUser
                        val hostHasSubmitted = hostName != null && eventState.responses.containsKey(hostName)
                        val hasSubmitted = eventState.responses.containsKey(currentUser)
                        val allResponded = eventState.responses.size >= eventState.request.invitees.size && eventState.request.invitees.isNotEmpty()
                        
                        if (isHost) {
                            hasSubmitted && !allResponded
                        } else {
                            !hostHasSubmitted || hasSubmitted
                        }
                    }.sortedByDescending { it.request.createdAt }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        if (templates.isNotEmpty()) {
                            var templateToDelete by remember { mutableStateOf<EventTemplate?>(null) }
                            
                            if (templateToDelete != null) {
                                AlertDialog(
                                    onDismissRequest = { templateToDelete = null },
                                    title = { Text("Delete Template") },
                                    text = { Text("Are you sure you want to delete '${templateToDelete?.name}'?") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                templateToDelete?.let { viewModel.deleteTemplate(it) }
                                                templateToDelete = null
                                            }
                                        ) {
                                            Text("Delete")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { templateToDelete = null }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            
                            Text(
                                text = "Saved Templates",
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
                                    TemplateCard(
                                        template = template,
                                        onClick = {
                                            viewModel.createFromTemplate(template)
                                            onCreateNewEvent()
                                        },
                                        onDeleteClick = {
                                            templateToDelete = template
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(16.dp))
                        }
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
                        if (finalizedEvents.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Confirmed Events",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(finalizedEvents, key = { it.request.id }) { eventState ->
                                        FinalizedEventCard(
                                            eventState = eventState,
                                            onClick = {
                                                viewModel.selectEvent(eventState.request.id)
                                                onOpenEvent(eventState.request.id)
                                            }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        // ACTION NEEDED Section
                        if (actionNeededEvents.isNotEmpty()) {
                            item {
                                Text(
                                    text = "ACTION NEEDED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(actionNeededEvents, key = { it.request.id }) { eventState ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    EventCard(
                                        eventState = eventState,
                                        currentUser = currentUser,
                                        isActionNeeded = true,
                                        onDelete = { viewModel.deleteEvent(eventState.request.id) },
                                        onClick = {
                                            viewModel.selectEvent(eventState.request.id)
                                            onOpenEvent(eventState.request.id)
                                        }
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // WAITING ON OTHERS Section
                        if (waitingOnOthersEvents.isNotEmpty()) {
                            item {
                                Text(
                                    text = "WAITING ON OTHERS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(waitingOnOthersEvents, key = { it.request.id }) { eventState ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    EventCard(
                                        eventState = eventState,
                                        currentUser = currentUser,
                                        isActionNeeded = false,
                                        onDelete = { viewModel.deleteEvent(eventState.request.id) },
                                        onClick = {
                                            viewModel.selectEvent(eventState.request.id)
                                            onOpenEvent(eventState.request.id)
                                        }
                                    )
                                }
                            }
                        }

                        // If both unconfirmed lists are empty (but there are finalized events)
                        if (actionNeededEvents.isEmpty() && waitingOnOthersEvents.isEmpty()) {
                            item {
                                Text(
                                    text = "Pending Events",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 20.dp, bottom = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No pending events.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

    val overlayMessage = successOverlayMessage
    if (overlayMessage != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { successOverlayMessage = null }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(280.dp)
                    .padding(16.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = overlayMessage,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20),
                        textAlign = TextAlign.Center
                    )
                    
                    val subtext = when (overlayMessage) {
                        "Event Confirmed!" -> "The final date and time have been finalized."
                        "Event Created!" -> "waiting for response"
                        "Availabilities Sent!" -> "Thank you for submitting your availability."
                        else -> ""
                    }
                    
                    if (subtext.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subtext,
                            style = if (overlayMessage == "Event Created!") {
                                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        LaunchedEffect(overlayMessage) {
            delay(2500)
            successOverlayMessage = null
        }
    }
}

@Composable
private fun TemplateCard(template: EventTemplate, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                        text = "${template.invitees.size} invited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }
            }
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete Template",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private val ConfirmedEventPastelGradients = listOf(
    listOf(Color(0xFFE0F7FA), Color(0xFFE0F2F1)), // Minty blue
    listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)), // Peach
    listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7)), // Lavender
    listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)), // Sage Green
    listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC)), // Cool Slate
    listOf(Color(0xFFFFFDE7), Color(0xFFFFF9C4)), // Pastel Yellow
    listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0)), // Soft Pink
    listOf(Color(0xFFEBF3FC), Color(0xFFD6E4FA)), // Soft Sky Blue
)

@Composable
private fun FinalizedEventCard(
    eventState: EventState,
    onClick: () -> Unit
) {
    val gradientIndex = remember(eventState.request.id) {
        val hash = eventState.request.id.hashCode()
        kotlin.math.abs(hash) % ConfirmedEventPastelGradients.size
    }
    
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = ConfirmedEventPastelGradients[gradientIndex]
                        )
                    )
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Emoji container
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = eventState.request.eventEmoji,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Details
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = eventState.request.eventName.ifBlank { "Untitled Event" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        val finalIndex = eventState.finalCellIndex
                        val finalEndIndex = eventState.finalCellEndIndex
                        if (finalIndex != null) {
                            val isDateOnly = eventState.request.dateOnlyMode
                            val config = if (isDateOnly) {
                                com.example.cheese.data.GridConfig(
                                    eventState.request.startDateMillis,
                                    eventState.request.endDateMillis,
                                    0,
                                    1
                                )
                            } else {
                                com.example.cheese.data.GridConfig(
                                    eventState.request.startDateMillis,
                                    eventState.request.endDateMillis,
                                    eventState.request.startHour,
                                    eventState.request.endHour
                                )
                            }

                            val dayStr = if (finalEndIndex == null || finalIndex == finalEndIndex) {
                                config.cellToDay(finalIndex)
                            } else {
                                val sCol = finalIndex % config.cols
                                val eCol = finalEndIndex % config.cols
                                val minCol = minOf(sCol, eCol)
                                val maxCol = maxOf(sCol, eCol)
                                if (minCol == maxCol) config.cellToDay(finalIndex)
                                else "${config.dayLabels.getOrElse(minCol) { "?" }} → ${config.dayLabels.getOrElse(maxCol) { "?" }}"
                            }

                            val hourStr = if (isDateOnly) {
                                "all day"
                            } else if (finalEndIndex == null || finalIndex == finalEndIndex) {
                                config.cellToHour(finalIndex)
                            } else {
                                val sRow = finalIndex / config.cols
                                val eRow = finalEndIndex / config.cols
                                val minRow = minOf(sRow, eRow)
                                val maxRow = maxOf(sRow, eRow)
                                "${config.hourLabels.getOrElse(minRow) { "?" }} → ${config.hourLabels.getOrElse(maxRow) { "?" }}"
                            }

                            Text(
                                text = if (isDateOnly) "$dayStr ($hourStr)" else "$dayStr, $hourStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4A4974),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Small circular checkmark badge in the top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(18.dp)
                .background(
                    color = Color.White.copy(alpha = 0.8f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Confirmed",
                tint = Color(0xFF4A4974),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    eventState: EventState,
    currentUser: String? = null,
    isActionNeeded: Boolean = false,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 100.dp.toPx() }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = if (isActionNeeded) {
                BorderStroke(1.dp, Color(0xFFFFCDD2))
            } else {
                BorderStroke(1.dp, Color(0xFFE5E7EB))
            },
            onClick = {
                if (offsetX.value < -10f) {
                    coroutineScope.launch { offsetX.animateTo(0f) }
                } else {
                    onClick()
                }
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
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
                        text = eventState.request.eventName.uppercase().ifBlank { "UNTITLED EVENT" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val isHost = eventState.request.invitees.firstOrNull()?.name == currentUser
                    val hasSubmitted = eventState.responses.containsKey(currentUser)
                    val isOrganizerFinalization = isActionNeeded && isHost && hasSubmitted

                    if (isOrganizerFinalization) {
                        Text(
                            text = "Everyone has voted!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pick final slot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    } else {
                        val responded = eventState.responses.size
                        val total = eventState.request.invitees.size
                        Text(
                            text = "$responded / $total RESPONDED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isActionNeeded) {
                            val guidanceText = when {
                                isHost && !hasSubmitted -> "Set your availability options."
                                else -> "Choose your availability."
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = guidanceText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Action / Status tag on the right
                val isHost = eventState.request.invitees.firstOrNull()?.name == currentUser
                val hasSubmitted = eventState.responses.containsKey(currentUser)
                val allResponded = eventState.responses.size >= eventState.request.invitees.size && eventState.request.invitees.isNotEmpty()

                Spacer(modifier = Modifier.width(8.dp))

                // Info Icon Button
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Event Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (isActionNeeded) {
                    val buttonText = if (isHost && hasSubmitted) "FINALIZE" else "RESPOND"
                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E3A8A), // Dark Navy Blue
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    val pillText = if (isHost) "ORGANIZING" else "VOTED"
                    val pillBgColor = if (isHost) Color(0xFFDCE6FF) else Color(0xFFDCFCE7)
                    val pillTextColor = if (isHost) Color(0xFF2563EB) else Color(0xFF166534)
                    
                    Box(
                        modifier = Modifier
                            .background(pillBgColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = pillTextColor
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "Participants (${eventState.request.invitees.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    eventState.request.invitees.forEach { invitee ->
                        val isOrganizer = invitee.isHost || (invitee.name == eventState.request.invitees.firstOrNull()?.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Avatar circle
                                val avatarColor = CuratedParticipantColors.getOrElse(invitee.colorIndex) { Color.Gray }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(avatarColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = invitee.name.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Name
                                val displayName = if (invitee.name == currentUser) "${invitee.name} (You)" else invitee.name
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Status badge
                            val badgeBg: Color
                            val badgeText: Color
                            val badgeLabel: String
                            val badgeIcon: androidx.compose.ui.graphics.vector.ImageVector?

                            when {
                                isOrganizer -> {
                                    badgeBg = Color(0xFFFEF08A)
                                    badgeText = Color(0xFF854D0E)
                                    badgeLabel = "Organizer"
                                    badgeIcon = null
                                }
                                !eventState.responses.containsKey(invitee.name) -> {
                                    badgeBg = Color(0xFFECEFF1)
                                    badgeText = Color(0xFF455A64)
                                    badgeLabel = "Waiting"
                                    badgeIcon = Icons.Default.Notifications
                                }
                                eventState.responses[invitee.name]?.availability.isNullOrEmpty() -> {
                                    badgeBg = Color(0xFFFEE2E2)
                                    badgeText = Color(0xFF991B1B)
                                    badgeLabel = "Not Available"
                                    badgeIcon = Icons.Default.Close
                                }
                                else -> {
                                    badgeBg = Color(0xFFDCFCE7)
                                    badgeText = Color(0xFF166534)
                                    badgeLabel = "Voted"
                                    badgeIcon = Icons.Default.Check
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (badgeIcon != null) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = badgeLabel,
                                        tint = badgeText,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = badgeText
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
