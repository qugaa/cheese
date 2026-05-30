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
import com.example.cheese.data.MOCK_PARTICIPANTS
import com.example.cheese.ui.OrganizerScreen
import com.example.cheese.ui.ParticipantScreen
import com.example.cheese.ui.ResolutionScreen
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
 * Architecture rationale:
 * - A single [ScheduleViewModel] is scoped to the NavHost's parent lifecycle
 *   (the Activity), so all three screens share one state container without
 *   passing data through navigation arguments.  This preserves atomicity of the
 *   Noun-Verb paradigm: the "noun" (event / selected cell) is never serialised,
 *   so there is zero risk of stale or mismatched state after back-stack pops.
 *
 * Navigation flow:
 *   organizer ──[Request Availability]──► participant
 *   participant ──[Submit (repeat per user)]──► participant  (recompose in-place)
 *   participant ──[All submitted]──► resolution
 */
@Composable
fun CheeseApp() {
    val navController = rememberNavController()
    // viewModel() at this scope attaches the VM to the Activity lifecycle,
    // making it the single source of truth for all three destinations.
    val scheduleViewModel: ScheduleViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "organizer"
    ) {
        composable("organizer") {
            OrganizerScreen(
                viewModel = scheduleViewModel,
                onRequestSent = {
                    navController.navigate("participant")
                }
            )
        }

        composable("participant") {
            ParticipantScreen(
                viewModel = scheduleViewModel,
                onSubmitted = {
                    // After submission the ViewModel has already incremented the
                    // participant index (or stayed at last index).  Check whether
                    // all mock participants have now responded.
                    if (scheduleViewModel.submittedCount >= MOCK_PARTICIPANTS.size) {
                        // All data collected — navigate to resolution.
                        navController.navigate("resolution") {
                            // Pop participant off the back stack so pressing Back
                            // on the resolution screen returns to the organizer,
                            // not an empty participant screen.
                            popUpTo("participant") { inclusive = true }
                        }
                    }
                    // If not all have submitted, stay on this destination —
                    // the Composable recomposes with the new participant name
                    // from the updated StateFlow, requiring zero extra navigation.
                }
            )
        }

        composable("resolution") {
            ResolutionScreen(
                viewModel = scheduleViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
