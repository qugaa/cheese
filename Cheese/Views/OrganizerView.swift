import SwiftUI

// MARK: - View 1: Organizer Initiation (Task 1)
// ===========================================================================
// Purpose: The host defines temporal constraints to limit computational
// variables for the group scheduling problem.
//
// State Machine:
//   [idle] → user fills form → taps "Request Availability" → [sent]
//   [sent] → passive dashboard showing event status
//   [finalized] → terminal summary with option to start a new event
//
// HCI Rationale (Form):
// - A native Form groups related inputs into semantically labeled Sections,
//   leveraging pre-attentive processing (Gestalt proximity) so users can
//   instantly parse the interface structure.
// - Each Section has a `header` for scanning and a `footer` for contextual
//   micro-copy, reducing the need for external documentation.
//
// HCI Rationale (DatePicker):
// - Standard DatePicker widgets provide a familiar, system-consistent temporal
//   input that eliminates formatting errors and enforces valid ranges.
// - The `in: Date()...` constraint makes past dates unreachable, preventing
//   input errors at the widget level (error prevention > error recovery).
//
// HCI Rationale (Button):
// - The primary action button spans full width within its Section, maximizing
//   the touch target area per Fitts' Law.
// - It is disabled when the event name is empty, enforcing the Noun-Verb
//   paradigm: the user must first provide valid data (Noun) before the
//   action (Verb) becomes available.
//
// HCI Rationale (Alert):
// - A standard Alert provides non-intrusive confirmation feedback without
//   permanently hijacking the user's locus of attention. The user acknowledges
//   and immediately returns to a passive dashboard state.
// ===========================================================================

struct OrganizerView: View {
    @Environment(ScheduleStore.self) private var store
    @State private var showConfirmation = false

    var body: some View {
        Group {
            if store.isFinalized {
                finalizedDashboard
            } else if store.eventRequest.isSent {
                pendingDashboard
            } else {
                eventForm
            }
        }
        .navigationTitle("Organize")
    }

    // MARK: - Event Creation Form (Idle State)

    /// The primary input form displayed before the event request is sent.
    private var eventForm: some View {
        // @Bindable is required to create two-way bindings from @Observable objects.
        @Bindable var store = store

        return Form {
            Section {
                TextField("Event Name", text: $store.eventRequest.eventName)
            } header: {
                Text("Event Details")
            } footer: {
                Text("Enter a descriptive name for your event.")
            }

            Section {
                DatePicker(
                    "Start Date",
                    selection: $store.eventRequest.startDate,
                    in: Date()...,
                    displayedComponents: .date
                )

                DatePicker(
                    "End Date",
                    selection: $store.eventRequest.endDate,
                    in: store.eventRequest.startDate...,
                    displayedComponents: .date
                )
            } header: {
                Text("Date Range")
            } footer: {
                Text("Participants will mark their availability for each day in this range.")
            }

            Section {
                Button {
                    store.sendRequest()
                    showConfirmation = true
                } label: {
                    Text("Request Availability")
                        .frame(maxWidth: .infinity)
                        .font(.headline)
                }
                .disabled(
                    store.eventRequest.eventName
                        .trimmingCharacters(in: .whitespaces)
                        .isEmpty
                )
            }
        }
        .alert("Request Sent", isPresented: $showConfirmation) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(
                "Your group availability request for \"\(store.eventRequest.eventName)\" has been sent to all participants."
            )
        }
    }

    // MARK: - Pending Dashboard (Sent State)

    /// Passive dashboard displayed after the event request has been sent.
    /// Shows event status and response progress without requiring user action.
    ///
    /// HCI Rationale (Locus of Attention):
    /// This view preserves the user's context by displaying the event they created
    /// without requiring re-navigation. It provides ambient status information
    /// (response count) that the organizer can glance at without deep interaction.
    private var pendingDashboard: some View {
        Form {
            Section {
                LabeledContent("Event Name", value: store.eventRequest.eventName)

                LabeledContent("Start Date") {
                    Text(
                        store.eventRequest.startDate,
                        format: .dateTime.weekday(.wide).month(.abbreviated).day()
                    )
                }

                LabeledContent("End Date") {
                    Text(
                        store.eventRequest.endDate,
                        format: .dateTime.weekday(.wide).month(.abbreviated).day()
                    )
                }
            } header: {
                Text("Event Summary")
            }

            Section {
                LabeledContent("Responses Received") {
                    Text("\(store.responseCount)")
                        .monospacedDigit()
                }
            } header: {
                Text("Status")
            } footer: {
                Text("Switch to the Respond tab to submit your own availability, or check the Results tab to view aggregated data.")
            }
        }
    }

    // MARK: - Finalized Dashboard (Terminal State)

    /// Displayed after the organizer has confirmed the final event date.
    /// Provides a clear summary and an option to start a new scheduling cycle.
    ///
    /// HCI Rationale (User Autonomy):
    /// The "New Event" button ensures the user is never trapped in a terminal
    /// state, supporting Nielsen's heuristic #3 (User control and freedom).
    private var finalizedDashboard: some View {
        Form {
            Section {
                LabeledContent("Event", value: store.eventRequest.eventName)

                if let date = store.finalizedDate {
                    LabeledContent("Confirmed Date") {
                        Text(
                            date,
                            format: .dateTime.weekday(.wide).month(.abbreviated).day()
                        )
                    }
                }
            } header: {
                Text("Event Confirmed ✓")
            } footer: {
                Text("This event has been finalized and all participants have been notified.")
            }

            Section {
                Button {
                    store.reset()
                } label: {
                    Text("Create New Event")
                        .frame(maxWidth: .infinity)
                        .font(.headline)
                }
            }
        }
    }
}

#Preview("Idle") {
    NavigationStack {
        OrganizerView()
    }
    .environment(ScheduleStore())
}

#Preview("Sent") {
    let store = ScheduleStore()
    store.eventRequest.eventName = "Team Lunch"
    store.sendRequest()

    return NavigationStack {
        OrganizerView()
    }
    .environment(store)
}
