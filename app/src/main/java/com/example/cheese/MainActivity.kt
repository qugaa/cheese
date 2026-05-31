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
import com.example.cheese.ui.OrganizerScreen
import com.example.cheese.ui.ParticipantScreen
import com.example.cheese.ui.ResolutionScreen
import com.example.cheese.ui.SplashScreen
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
                    navController.navigate("dashboard") {
                        popUpTo("splash") { inclusive = true }
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
                onOpenEvent = { eventId ->
                    val state = scheduleViewModel.events.value.find { it.request.id == eventId }
                    if (state != null) {
                        val isFinalized = state.finalCellIndex != null
                        val isComplete = state.responses.size >= state.request.invitees.size && state.request.invitees.isNotEmpty()
                        
                        if (isFinalized || isComplete) {
                            navController.navigate("resolution")
                        } else {
                            navController.navigate("participant")
                        }
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
                }
            )
        }

        // ── View 2: Participant ───────────────────────────────────────────────
        composable("participant") {
            ParticipantScreen(
                viewModel = scheduleViewModel,
                onSubmitted = {
                    if (scheduleViewModel.getResponses().size >= scheduleViewModel.totalParticipants()) {
                        navController.navigate("resolution") {
                            popUpTo("participant") { inclusive = true }
                        }
                    }
                },
                onEditEvent = {
                    val currentId = scheduleViewModel.currentEventId.value
                    if (currentId != null) {
                        scheduleViewModel.editEvent(currentId)
                        navController.navigate("organizer") {
                            popUpTo("dashboard")
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
                        scheduleViewModel.editEvent(currentId)
                        navController.navigate("organizer") {
                            popUpTo("dashboard")
                        }
                    }
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
