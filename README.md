# Cheese

**Asynchronous Time-Management & Availability-Synchronization Application**

A high-fidelity native iOS prototype built with SwiftUI, designed and evaluated strictly on HCI methodologies and Cognitive Ergonomics principles.

## Concept

Cheese replaces chaotic, synchronous group chat negotiations with a structured, deterministic data-gathering protocol. It minimizes cognitive overload in Computer-Supported Cooperative Work (CSCW) by decomposing group scheduling into three discrete tasks:

1. **Organize** — The host defines temporal constraints (event name, date range)
2. **Respond** — Participants submit binary availability per day
3. **Results** — The system aggregates responses and recommends the optimal date

## Architecture

| Layer | Implementation |
|-------|---------------|
| **UI Framework** | SwiftUI (iOS 17+) |
| **State Management** | `@Observable` (`ScheduleStore`) |
| **Navigation** | `TabView` (flat IA) + `NavigationStack` (progressive disclosure) |
| **Backend** | None — mock local state simulates multi-user data |
| **Design System** | Apple HIG standard widgets only |

## Project Structure

```
Cheese/
├── CheeseApp.swift              # @main entry point, environment injection
├── ContentView.swift             # Root TabView navigation
├── Models/
│   └── ScheduleStore.swift       # Data models + @Observable state store
└── Views/
    ├── OrganizerView.swift       # Task 1: Event creation form + dashboard
    ├── ParticipantView.swift     # Task 2: Binary availability toggles
    └── ResultsView.swift         # Task 3: Aggregated results + finalization
```

## Xcode Setup

1. Open Xcode → **File → New → Project**
2. Select **iOS → App** template
3. Set Product Name to `Cheese`, Interface to **SwiftUI**, Language to **Swift**
4. Set minimum deployment target to **iOS 17.0**
5. Delete the auto-generated `ContentView.swift` and `CheeseApp.swift`
6. Drag the `Cheese/` folder from this repository into the Xcode project navigator
7. Ensure all `.swift` files are added to the `Cheese` target
8. Build and run (`⌘R`)

## HCI Principles Applied

- **Fitts' Law**: All interactive elements span maximum width; tabs at screen edge
- **Noun-Verb Paradigm**: Data state (noun) must be defined before actions (verb) are enabled
- **Locus of Attention**: State preserved across interruptions; non-intrusive Alert/sheet feedback
- **Hick's Law**: Binary toggles minimize choice complexity per decision point
- **Error Prevention**: DatePicker constraints, disabled buttons on invalid state, conservative defaults
- **User Autonomy**: Revise/reset actions available at every terminal state (Nielsen #3)

## Requirements

- Xcode 15+
- iOS 17.0+
- Swift 5.9+
