import SwiftUI

// MARK: - View 2: Participant Input (Task 2)
// ===========================================================================
// Purpose: Invited users process binary availability decisions with minimal
// cognitive friction. This view transforms an inherently complex group
// negotiation into a simple per-day binary choice.
//
// State Machine:
//   [no event] → ContentUnavailableView (waiting for organizer)
//   [event sent, not responded] → availability form with toggles
//   [responded] → confirmation state with option to revise
//
// HCI Rationale (Toggle):
// - Each day is represented by a standard Toggle widget, providing a binary
//   affordance that maps directly to the binary data model (Available/Unavailable).
// - The semantic mapping between widget and data is 1:1, eliminating
//   Gulf of Execution errors (Norman's model).
// - Default state is OFF (unavailable) — a conservative default that forces
//   conscious, deliberate activation. This prevents false-positive availability
//   from passive acceptance, which is critical in scheduling contexts where
//   over-commitment leads to real-world coordination failures.
//
// HCI Rationale (Noun-Verb Paradigm):
// - The user first selects their data state (toggles = Noun) then executes
//   the action ("Submit Availability" = Verb). The submit button is always
//   visible at the bottom of the form, ensuring the action is discoverable
//   only after the data state is defined.
//
// HCI Rationale (Fitts' Law):
// - Toggle rows span the full width of the form, providing maximum touch
//   target area. The submit button also spans full width within its section.
//
// HCI Rationale (Locus of Attention):
// - The confirmation alert is non-intrusive and dismisses cleanly, returning
//   the user to a passive confirmation state rather than resetting the view.
// ===========================================================================

struct ParticipantView: View {
    @Environment(ScheduleStore.self) private var store

    /// Local state tracking the user's in-progress availability selections.
    /// This is kept separate from the store until the user explicitly submits,
    /// preventing premature state commits and supporting undo-by-abandonment.
    @State private var availability: [Date: Bool] = [:]

    /// Controls the visibility of the submission confirmation alert.
    @State private var showConfirmation = false

    var body: some View {
        Group {
            if !store.eventRequest.isSent {
                noEventState
            } else if store.currentUserHasResponded {
                submittedState
            } else {
                availabilityForm
            }
        }
        .navigationTitle("Your Availability")
    }

    // MARK: - Empty State (No Event Sent)

    /// Displayed when the organizer has not yet created an event request.
    ///
    /// HCI Rationale: ContentUnavailableView is a standard iOS 17+ pattern for
    /// communicating "nothing to show" states. It prevents user confusion by
    /// explicitly explaining *why* the screen is empty and *what action* is needed.
    private var noEventState: some View {
        ContentUnavailableView(
            "No Pending Requests",
            systemImage: "tray",
            description: Text("When an organizer sends an availability request, it will appear here.")
        )
    }

    // MARK: - Submitted State

    /// Displayed after the user has successfully submitted their availability.
    /// Provides a clear confirmation and an option to revise their response.
    ///
    /// HCI Rationale (Error Recovery):
    /// The "Revise Response" button supports Nielsen's heuristic #3 (User control
    /// and freedom) by allowing the user to correct mistakes without penalty.
    private var submittedState: some View {
        Form {
            Section {
                Label("Availability Submitted", systemImage: "checkmark.circle.fill")
                    .font(.headline)
                    .foregroundStyle(.green)
            } header: {
                Text("Status")
            } footer: {
                Text("Your response for \"\(store.eventRequest.eventName)\" has been recorded. Check the Results tab to see group availability.")
            }

            if let response = store.currentUserResponse {
                Section {
                    ForEach(store.requestedDays, id: \.self) { day in
                        LabeledContent(
                            day.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
                        ) {
                            Text(response.availability[day] == true ? "Available" : "Unavailable")
                                .foregroundStyle(
                                    response.availability[day] == true ? .green : .secondary
                                )
                        }
                    }
                } header: {
                    Text("Your Response")
                }
            }

            Section {
                Button {
                    // Re-enter editing: pre-populate with previous response
                    if let response = store.currentUserResponse {
                        availability = response.availability
                    }
                    // Remove the current response to re-show the form
                    store.participantResponses.removeAll { $0.name == store.currentParticipantName }
                } label: {
                    Text("Revise Response")
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    // MARK: - Availability Form (Input State)

    /// The primary input form where the user toggles availability for each day.
    private var availabilityForm: some View {
        Form {
            Section {
                LabeledContent("Event", value: store.eventRequest.eventName)

                LabeledContent("Date Range") {
                    Text(
                        "\(store.eventRequest.startDate.formatted(.dateTime.month(.abbreviated).day())) – \(store.eventRequest.endDate.formatted(.dateTime.month(.abbreviated).day()))"
                    )
                }
            } header: {
                Text("Request Details")
            }

            Section {
                ForEach(store.requestedDays, id: \.self) { day in
                    Toggle(
                        day.formatted(.dateTime.weekday(.wide).month(.abbreviated).day()),
                        isOn: binding(for: day)
                    )
                }
            } header: {
                Text("Mark Your Available Days")
            } footer: {
                Text("Each day defaults to unavailable. Toggle on the days you are free.")
            }

            Section {
                Button {
                    store.submitCurrentUserAvailability(availability)
                    showConfirmation = true
                } label: {
                    Text("Submit Availability")
                        .frame(maxWidth: .infinity)
                        .font(.headline)
                }
            }
        }
        .onAppear {
            initializeAvailability()
        }
        .alert("Availability Submitted", isPresented: $showConfirmation) {
            Button("OK", role: .cancel) { }
        } message: {
            Text("Your availability for \"\(store.eventRequest.eventName)\" has been recorded successfully.")
        }
    }

    // MARK: - Helpers

    /// Creates a two-way binding for a specific day's toggle state.
    ///
    /// This bridges the local `availability` dictionary to individual Toggle widgets,
    /// defaulting to `false` (unavailable) if no entry exists for the day.
    private func binding(for day: Date) -> Binding<Bool> {
        Binding(
            get: { availability[day] ?? false },
            set: { availability[day] = $0 }
        )
    }

    /// Initializes the local availability dictionary with conservative defaults.
    ///
    /// Only sets values for days that don't already have an entry, preserving
    /// any pre-populated state (e.g., when revising a previous response).
    private func initializeAvailability() {
        for day in store.requestedDays where availability[day] == nil {
            availability[day] = false
        }
    }
}

#Preview("No Event") {
    NavigationStack {
        ParticipantView()
    }
    .environment(ScheduleStore())
}

#Preview("With Event") {
    let store = ScheduleStore()
    store.eventRequest.eventName = "Team Lunch"
    store.sendRequest()

    return NavigationStack {
        ParticipantView()
    }
    .environment(store)
}
