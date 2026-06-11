package com.example.cheese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cheese.ui.DashboardScreen
import com.example.cheese.ui.EventDetailsScreen
import com.example.cheese.ui.LoginScreen
import com.example.cheese.ui.OrganizerScreen
import com.example.cheese.ui.ParticipantScreen
import com.example.cheese.ui.FriendDetailsScreen
import com.example.cheese.ui.ResolutionScreen
import com.example.cheese.ui.SplashScreen
import com.example.cheese.ui.QuickCreateScreen
import com.example.cheese.ui.theme.CheeseTheme
import com.example.cheese.viewmodel.ScheduleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheeseTheme {
                CheeseApp()
            }
        }
    }
}

/**
 * Root navigation graph.
 *
 * Navigation flow:
 *   splash ──[timeout]──► dashboard
 *   dashboard ──[FAB]──► organizer
 *   organizer ──[Request Availability]──► participant
 *   participant ──[Submit (repeat per user)]──► participant (recompose in-place)
 *   participant ──[All submitted]──► resolution
 */
@Composable
fun CheeseApp() {
    val navController = rememberNavController()
    // Single ViewModel scoped to Activity lifecycle
    val scheduleViewModel: ScheduleViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        // ── Splash ────────────────────────────────────────────────────────────
        composable("splash") {
            SplashScreen(
                onTimeout = {
                    if (scheduleViewModel.currentUser.value == null) {
                        navController.navigate("login") {
                            popUpTo("splash") { inclusive = true }
                        }
                    } else {
                        navController.navigate("dashboard") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── Login ────────────────────────────────────────────────────────────
        composable("login") {
            LoginScreen(
                viewModel = scheduleViewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard ─────────────────────────────────────────────────────────
        composable("dashboard") {
            DashboardScreen(
                viewModel = scheduleViewModel,
                onCreateNewEvent = {
                    navController.navigate("organizer")
                },
                onQuickCreate = {
                    navController.navigate("quick_create")
                },
                onOpenEvent = { eventId ->
                    val state = scheduleViewModel.events.value.find { it.request.id == eventId }
                    if (state != null) {
                        scheduleViewModel.selectEvent(eventId)
                        val currentUser = scheduleViewModel.currentUser.value
                        val isHost = state.request.invitees.firstOrNull()?.name == currentUser
                        val hasSubmitted = state.responses.containsKey(currentUser)

                        val isFinalized = state.finalCellIndex != null
                        val isComplete = state.responses.size >= state.request.invitees.size && state.request.invitees.isNotEmpty()
                        
                        if (isFinalized) {
                            navController.navigate("event_details")
                        } else if (!hasSubmitted) {
                            navController.navigate("participant")
                        } else if (isHost) {
                            navController.navigate("resolution")
                        } else {
                            // If not host and already submitted, they can just look at their response in ParticipantScreen
                            navController.navigate("participant")
                        }
                    }
                },
                onFriendClick = { friendName ->
                    navController.navigate("friend_details/$friendName")
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── Friend Details ────────────────────────────────────────────────────
        composable("friend_details/{friendName}") { backStackEntry ->
            val friendName = backStackEntry.arguments?.getString("friendName") ?: return@composable
            FriendDetailsScreen(
                friendName = friendName,
                viewModel = scheduleViewModel,
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId ->
                    val state = scheduleViewModel.events.value.find { it.request.id == eventId }
                    if (state != null) {
                        scheduleViewModel.selectEvent(eventId)
                        val currentUser = scheduleViewModel.currentUser.value
                        val isHost = state.request.invitees.firstOrNull()?.name == currentUser
                        val hasSubmitted = state.responses.containsKey(currentUser)
                        val isFinalized = state.finalCellIndex != null
                        
                        if (isFinalized) {
                            navController.navigate("event_details")
                        } else if (!hasSubmitted) {
                            navController.navigate("participant")
                        } else if (isHost) {
                            navController.navigate("resolution")
                        } else {
                            navController.navigate("participant")
                        }
                    }
                }
            )
        }

        // ── Quick Create Flow ─────────────────────────────────────────────────
        composable("quick_create") {
            QuickCreateScreen(
                viewModel = scheduleViewModel,
                onProceed = {
                    scheduleViewModel.finalizeEventRequest()
                    navController.navigate("participant")
                },
                onAdvancedSetup = {
                    navController.navigate("organizer")
                },
                onBack = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── View 1: Organizer ─────────────────────────────────────────────────
        composable("organizer") {
            OrganizerScreen(
                viewModel = scheduleViewModel,
                onRequestSent = {
                    scheduleViewModel.finalizeEventRequest()
                    navController.navigate("participant")
                },
                onBack = { navController.navigateUp() }
            )
        }

        // ── View 2: Participant ───────────────────────────────────────────────
        composable("participant") {
            ParticipantScreen(
                viewModel = scheduleViewModel,
                onSubmitted = { dashboardMsg ->
                    if (dashboardMsg != null) {
                        scheduleViewModel.setDashboardMessage(dashboardMsg)
                    }
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onEditEvent = {
                    val currentId = scheduleViewModel.currentEventId.value
                    if (currentId != null) {
                        scheduleViewModel.saveCurrentDraft()
                        // Editing is not implemented in ViewModel yet, removed `scheduleViewModel.editEvent(currentId)` 
                        // Let's just return to dashboard for now
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                },
                onBackToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── View 3: Resolution ────────────────────────────────────────────────
        composable("resolution") {
            ResolutionScreen(
                viewModel = scheduleViewModel,
                onEditEvent = {
                    val currentId = scheduleViewModel.currentEventId.value
                    if (currentId != null) {
                        navController.navigate("organizer") {
                            popUpTo("dashboard")
                        }
                    }
                },
                onConfirm = {
                    navController.navigate("event_details") {
                        popUpTo("dashboard")
                    }
                },
                onBack = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── View 4: Event Details ─────────────────────────────────────────────
        composable("event_details") {
            EventDetailsScreen(
                viewModel = scheduleViewModel,
                onEditChoice = {
                    navController.navigate("resolution")
                },
                onBack = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
    }
}
