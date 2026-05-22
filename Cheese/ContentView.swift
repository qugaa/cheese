import SwiftUI

// MARK: - Root Navigation (TabView)
// ===========================================================================
// ContentView implements a flat information architecture using TabView as the
// root navigation paradigm.
//
// HCI Rationale (Information Architecture):
// - A TabView provides persistent, always-visible navigation affordances at the
//   screen's bottom edge, compliant with Fitts' Law (large touch targets at a
//   screen edge have effectively infinite height).
// - The flat hierarchy ensures users are never more than one tap away from any
//   primary workflow, minimizing navigation-induced disorientation.
// - Each tab wraps its content in a NavigationStack for progressive disclosure
//   within that workflow, maintaining a clear spatial mental model.
//
// HCI Rationale (Information Scent):
// - Tab labels use precise, unambiguous verb/noun pairings ("Organize", "Respond",
//   "Results") paired with semantically relevant SF Symbols to maximize recognition
//   over recall (Nielsen's heuristic #6).
// ===========================================================================

struct ContentView: View {
    var body: some View {
        TabView {
            NavigationStack {
                OrganizerView()
            }
            .tabItem {
                Label("Organize", systemImage: "calendar.badge.plus")
            }

            NavigationStack {
                ParticipantView()
            }
            .tabItem {
                Label("Respond", systemImage: "person.crop.circle.badge.checkmark")
            }

            NavigationStack {
                ResultsView()
            }
            .tabItem {
                Label("Results", systemImage: "chart.bar.fill")
            }
        }
    }
}

#Preview {
    ContentView()
        .environment(ScheduleStore())
}
