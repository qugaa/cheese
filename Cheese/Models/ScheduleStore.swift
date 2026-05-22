import Foundation
import SwiftUI

// MARK: - Data Models
// ===========================================================================
// These value types form the core data model for the Cheese application.
// They are intentionally simple structs to maintain a single source of truth
// within the ScheduleStore, adhering to unidirectional data flow principles.
// ===========================================================================

/// Represents the temporal boundaries defined by the organizer.
///
/// HCI Rationale: By constraining the date range upfront, we reduce the
/// combinatorial explosion of possibilities that participants must evaluate,
/// directly lowering cognitive load (Hick's Law mitigation).
struct EventRequest {
    var eventName: String = ""
    var startDate: Date = Date()
    var endDate: Date = Calendar.current.date(byAdding: .day, value: 6, to: Date()) ?? Date()
    var isSent: Bool = false
}

/// Represents a single participant's binary availability across the requested days.
///
/// HCI Rationale: Availability is strictly binary (Available / Unavailable)
/// to eliminate ambiguous intermediate states. This forces a conscious,
/// deterministic decision from the user on each day, reducing decision fatigue
/// compared to Likert-scale or free-text alternatives.
struct ParticipantResponse: Identifiable {
    let id = UUID()
    let name: String
    /// Maps a calendar day (normalized to midnight) to a boolean availability flag.
    /// `true` = Available, `false` = Unavailable.
    var availability: [Date: Bool]
}

/// Aggregated availability data for a single day, computed across all participants.
///
/// Used exclusively in View 3 (Results) to present empirical group data.
struct DayAvailability: Identifiable {
    let id = UUID()
    let date: Date
    let availableCount: Int
    let totalParticipants: Int

    /// The percentage of participants available on this day (0.0–1.0).
    var percentage: Double {
        guard totalParticipants > 0 else { return 0 }
        return Double(availableCount) / Double(totalParticipants)
    }
}

// MARK: - Central State Store
// ===========================================================================
// ScheduleStore is the single, observable source of truth for all application
// state. It uses @Observable (iOS 17+) for fine-grained, automatic SwiftUI
// invalidation without manual publishers.
//
// Architecture: There is NO remote backend. This store simulates multi-user
// data by generating mock participant responses alongside the real user's input.
// ===========================================================================

@Observable
final class ScheduleStore {

    // MARK: - Organizer State

    /// The event request defined by the organizer in View 1.
    var eventRequest = EventRequest()

    // MARK: - Participant State

    /// All participant responses, including mock participants and the current user.
    var participantResponses: [ParticipantResponse] = []

    // MARK: - Finalization State

    /// Whether the organizer has confirmed the final event date.
    var isFinalized: Bool = false

    /// The date selected as the final event date, if finalized.
    var finalizedDate: Date?

    // MARK: - Constants

    /// The display name for the prototype's active user (acting as a participant).
    let currentParticipantName = "You"

    /// Simulated remote participants to demonstrate CSCW data aggregation.
    private let mockParticipantNames = ["Alice", "Bob", "Charlie"]

    // MARK: - Computed Properties

    /// Generates a deterministic array of calendar days spanning the event request range.
    ///
    /// Each date is normalized to midnight (start of day) to ensure consistent
    /// dictionary key matching across the application.
    var requestedDays: [Date] {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: eventRequest.startDate)
        let end = calendar.startOfDay(for: eventRequest.endDate)

        guard start <= end else { return [] }

        var days: [Date] = []
        var current = start
        while current <= end {
            days.append(current)
            guard let next = calendar.date(byAdding: .day, value: 1, to: current) else { break }
            current = next
        }
        return days
    }

    /// Returns the current user's submitted response, if one exists.
    var currentUserResponse: ParticipantResponse? {
        participantResponses.first { $0.name == currentParticipantName }
    }

    /// Whether the current user has already submitted their availability.
    var currentUserHasResponded: Bool {
        currentUserResponse != nil
    }

    /// The total number of participants who have submitted responses.
    var responseCount: Int {
        participantResponses.count
    }

    /// Aggregated availability per day, computed from all submitted responses.
    ///
    /// This is the core algorithmic output: for each day in the range, it counts
    /// how many participants marked themselves as available.
    var aggregatedAvailability: [DayAvailability] {
        let days = requestedDays
        let responses = participantResponses

        return days.map { day in
            let available = responses.filter { $0.availability[day] == true }.count
            return DayAvailability(
                date: day,
                availableCount: available,
                totalParticipants: responses.count
            )
        }
    }

    /// The day with the highest match percentage across all participants.
    ///
    /// In case of a tie, the earliest day wins (stable sort by date).
    var optimalDay: DayAvailability? {
        aggregatedAvailability
            .sorted { $0.date < $1.date }
            .max { $0.percentage < $1.percentage }
    }

    // MARK: - Actions (State Transitions)

    /// Transitions the event from draft to sent state.
    ///
    /// Side effect: Generates mock participant responses to simulate a multi-user
    /// environment. In a production system, this would dispatch a network request.
    func sendRequest() {
        eventRequest.isSent = true
        generateMockResponses()
    }

    /// Records the current user's availability and adds it to the response pool.
    ///
    /// If the user has already responded, the previous response is replaced,
    /// preserving idempotent behavior.
    ///
    /// - Parameter availability: A dictionary mapping each requested day to a boolean.
    func submitCurrentUserAvailability(_ availability: [Date: Bool]) {
        // Remove any prior submission to allow re-entry
        participantResponses.removeAll { $0.name == currentParticipantName }

        let response = ParticipantResponse(
            name: currentParticipantName,
            availability: availability
        )
        participantResponses.append(response)
    }

    /// Finalizes the event on the given date.
    ///
    /// This is a terminal state transition; after finalization, the event is locked.
    ///
    /// - Parameter date: The chosen event date.
    func finalizeEvent(on date: Date) {
        finalizedDate = date
        isFinalized = true
    }

    /// Resets the entire application state for a new event cycle.
    ///
    /// HCI Rationale: Provides a clear "escape hatch" so the user is never
    /// trapped in a terminal state, supporting user autonomy (Nielsen's heuristic #3).
    func reset() {
        eventRequest = EventRequest()
        participantResponses = []
        isFinalized = false
        finalizedDate = nil
    }

    // MARK: - Mock Data Generation (Prototype Only)

    /// Populates simulated participant responses with probabilistic availability.
    ///
    /// Each mock participant has approximately 60% chance of being available on
    /// any given day, creating realistic variance for the results view.
    private func generateMockResponses() {
        let days = requestedDays

        participantResponses = mockParticipantNames.map { name in
            var availability: [Date: Bool] = [:]
            for day in days {
                // ~60% probability of availability for realistic distribution
                availability[day] = Double.random(in: 0...1) < 0.6
            }
            return ParticipantResponse(name: name, availability: availability)
        }
    }
}
