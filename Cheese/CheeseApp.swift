import SwiftUI

// MARK: - Application Entry Point
// ===========================================================================
// The @main entry point initializes the single ScheduleStore instance and
// injects it into the SwiftUI environment. All child views access this shared
// state via @Environment(ScheduleStore.self).
//
// HCI Rationale: A single source of truth guarantees that interruptions to the
// user's locus of attention (e.g., switching tabs, backgrounding the app)
// never result in lost state. The user always returns exactly where they left off.
// ===========================================================================

@main
struct CheeseApp: App {
    /// The single, application-wide state container.
    /// Held by @State to ensure its lifetime matches the application's lifetime.
    @State private var store = ScheduleStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
        }
    }
}
