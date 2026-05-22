import SwiftUI

// MARK: - View 3: Algorithmic Resolution & Finalization (Task 3)
// ===========================================================================
// Purpose: The system displays aggregated empirical data from all participant
// responses, enabling the organizer to make an informed final decision.
// This view closes the CSCW data-gathering loop by presenting deterministic
// output derived from the structured input protocol.
//
// State Machine:
//   [no event] → ContentUnavailableView (waiting for organizer)
//   [event sent] → results list with aggregated data + finalize action
//   [finalized] → terminal confirmation summary
//
// HCI Rationale (ProgressView):
// - Native ProgressView widgets provide a pre-attentive visual encoding of
//   percentage data. Users can compare availability across days at a glance
//   without reading exact numbers (parallel visual processing).
// - The linear ProgressView bar maps naturally to the 0–100% mental model.
//
// HCI Rationale (Optimal Day Highlight):
// - The algorithmically determined best day is promoted to a visually distinct
//   Section at the top of the results list. This reduces visual search time
//   and provides an immediate, actionable recommendation.
// - Typography hierarchy (title2.bold for the date, subheadline for the label)
//   creates a clear information scent guiding the eye to the most important data.
//
// HCI Rationale (Sheet for Finalization):
// - The final confirmation uses a standard sheet presentation, which:
//   (a) Creates a modal context appropriate for an irreversible action.
//   (b) Keeps the underlying results visible (partially), maintaining spatial
//       context and reducing disorientation.
//   (c) Can be dismissed by swiping down, supporting user autonomy.
//
// HCI Rationale (Noun-Verb on Finalization):
// - The "Set Final Event" button operates on the already-visible optimal day
//   (Noun = displayed data, Verb = confirmation button). The user inspects
//   the data before committing, eliminating blind actions.
// ===========================================================================

struct ResultsView: View {
    @Environment(ScheduleStore.self) private var store

    /// Controls the presentation of the finalization confirmation sheet.
    @State private var showFinalSheet = false

    var body: some View {
        Group {
            if !store.eventRequest.isSent {
                noEventState
            } else if store.isFinalized {
                finalizedState
            } else {
                resultsList
            }
        }
        .navigationTitle("Results")
    }

    // MARK: - Empty State

    /// Displayed when no event has been created yet.
    private var noEventState: some View {
        ContentUnavailableView(
            "No Event Yet",
            systemImage: "chart.bar",
            description: Text("Create and send an availability request from the Organize tab to see results here.")
        )
    }

    // MARK: - Results List (Active State)

    /// The primary results display showing aggregated availability data.
    private var resultsList: some View {
        Form {
            // Optimal Day Section — promoted to top for maximum information scent
            if let optimal = store.optimalDay {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Best Match")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        Text(
                            optimal.date.formatted(
                                .dateTime.weekday(.wide).month(.abbreviated).day()
                            )
                        )
                        .font(.title2.bold())

                        ProgressView(value: optimal.percentage) {
                            Text("\(Int(optimal.percentage * 100))% Available")
                                .font(.subheadline)
                                .monospacedDigit()
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("Recommended")
                } footer: {
                    Text("The day with the highest group availability based on \(store.responseCount) response(s).")
                }
            }

            // Per-Day Breakdown Section
            Section {
                ForEach(store.aggregatedAvailability) { day in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(
                                day.date.formatted(
                                    .dateTime.weekday(.abbreviated).month(.abbreviated).day()
                                )
                            )
                            .font(.body)

                            Spacer()

                            Text("\(day.availableCount)/\(day.totalParticipants)")
                                .font(.subheadline)
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }

                        ProgressView(value: day.percentage)
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("All Days")
            } footer: {
                Text("\(store.responseCount) participant(s) have responded so far.")
            }

            // Finalize Action Section
            Section {
                Button {
                    showFinalSheet = true
                } label: {
                    Text("Set Final Event")
                        .frame(maxWidth: .infinity)
                        .font(.headline)
                }
                .disabled(store.optimalDay == nil)
            }
        }
        .sheet(isPresented: $showFinalSheet) {
            if let optimal = store.optimalDay {
                FinalEventSheet(
                    eventName: store.eventRequest.eventName,
                    selectedDay: optimal,
                    responseCount: store.responseCount
                ) {
                    store.finalizeEvent(on: optimal.date)
                    showFinalSheet = false
                }
            }
        }
    }

    // MARK: - Finalized State

    /// Terminal state displayed after the event has been confirmed.
    ///
    /// HCI Rationale: Provides clear closure by summarizing the finalized event,
    /// confirming the action was successful, and offering a path to start fresh.
    private var finalizedState: some View {
        Form {
            Section {
                LabeledContent("Event", value: store.eventRequest.eventName)

                if let date = store.finalizedDate {
                    LabeledContent("Confirmed Date") {
                        Text(
                            date.formatted(
                                .dateTime.weekday(.wide).month(.abbreviated).day()
                            )
                        )
                        .fontWeight(.semibold)
                    }
                }

                LabeledContent("Participants") {
                    Text("\(store.responseCount)")
                        .monospacedDigit()
                }
            } header: {
                Text("Event Confirmed ✓")
            } footer: {
                Text("All participants have been notified of the final date.")
            }
        }
    }
}

// MARK: - Final Event Confirmation Sheet
// ===========================================================================
// A modal sheet summarizing the calendar invitation details before the
// organizer commits to the final date.
//
// HCI Rationale:
// - The sheet acts as a confirmation dialog for an important, semi-irreversible
//   action (setting the final event date).
// - It follows the Noun-Verb paradigm: all event details (Noun) are displayed
//   prominently, and the "Confirm Event" button (Verb) is placed at the bottom,
//   requiring the user to visually scan the summary before acting.
// - The "Cancel" option in the toolbar provides an escape hatch per Nielsen's
//   heuristic #3 (User control and freedom).
// ===========================================================================

struct FinalEventSheet: View {
    let eventName: String
    let selectedDay: DayAvailability
    let responseCount: Int

    /// Closure invoked when the user confirms the event.
    let onConfirm: () -> Void

    /// Controls the dismiss action for the sheet.
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Event Name", value: eventName)

                    LabeledContent("Date") {
                        Text(
                            selectedDay.date.formatted(
                                .dateTime.weekday(.wide).month(.abbreviated).day()
                            )
                        )
                        .fontWeight(.semibold)
                    }

                    LabeledContent("Group Availability") {
                        Text("\(Int(selectedDay.percentage * 100))%")
                            .monospacedDigit()
                            .fontWeight(.semibold)
                    }

                    LabeledContent("Respondents") {
                        Text("\(responseCount)")
                            .monospacedDigit()
                    }
                } header: {
                    Text("Event Summary")
                } footer: {
                    Text("Confirming will notify all participants and set this as the final event date.")
                }

                Section {
                    Button {
                        onConfirm()
                    } label: {
                        Text("Confirm Event")
                            .frame(maxWidth: .infinity)
                            .font(.headline)
                    }
                }
            }
            .navigationTitle("Confirm Event")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview("No Event") {
    NavigationStack {
        ResultsView()
    }
    .environment(ScheduleStore())
}

#Preview("With Responses") {
    let store = ScheduleStore()
    store.eventRequest.eventName = "Team Lunch"
    store.sendRequest()

    return NavigationStack {
        ResultsView()
    }
    .environment(store)
}

#Preview("Final Sheet") {
    FinalEventSheet(
        eventName: "Team Lunch",
        selectedDay: DayAvailability(
            date: Date(),
            availableCount: 3,
            totalParticipants: 4
        ),
        responseCount: 4,
        onConfirm: { }
    )
}
