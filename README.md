# Cheese

**Asynchronous Time-Management & Availability-Synchronization Application**

A high-fidelity native Android prototype built with Jetpack Compose and Material 3, designed and evaluated strictly on HCI methodologies and Cognitive Ergonomics principles.

---

## Concept

Cheese replaces chaotic, synchronous group-chat negotiations with a structured, deterministic data-gathering protocol. It minimises cognitive overload in Computer-Supported Cooperative Work (CSCW) by decomposing group scheduling into three discrete tasks:

1. **Organize** — The host defines the event name and temporal date-range constraints
2. **Respond** — Each participant paints their availability onto a weekly time-grid using a continuous drag gesture
3. **Results** — The system aggregates responses into a color-coded heatmap and recommends the optimal time slot

---

## Architecture

| Layer | Implementation |
|---|---|
| **UI Framework** | Jetpack Compose (Material 3) |
| **State Management** | `ScheduleViewModel` (Activity-scoped `ViewModel`) with `MutableStateFlow` / `StateFlow` |
| **Navigation** | `NavHost` + `NavController` — three composable destinations |
| **Data Models** | `EventRequest`, `ParticipantResponse`, `GridConfig` (see `data/Models.kt`) |
| **Theme** | `CheeseTheme` — Material 3 dynamic color; falls back gracefully on API < 31 |
| **Min SDK** | API 34 (Android 14) |
| **Backend** | None — mock multi-user data via `MOCK_PARTICIPANTS` list |

---

## Project Structure

```
app/src/main/java/com/example/cheese/
├── MainActivity.kt                   # Single Activity; hosts CheeseApp NavHost
├── data/
│   └── Models.kt                     # EventRequest, ParticipantResponse, GridConfig
├── ui/
│   ├── OrganizerScreen.kt            # Screen 1: Event creation form
│   ├── ParticipantScreen.kt          # Screen 2: Drag-to-paint availability grid
│   ├── ResolutionScreen.kt           # Screen 3: Heatmap aggregation & finalization
│   └── theme/
│       ├── CheeseTheme.kt            # Material 3 dynamic color wrapper
│       ├── Color.kt                  # Fallback color tokens
│       ├── Theme.kt                  # Light/dark color schemes
│       └── Type.kt                  # Typography scale
└── viewmodel/
    └── ScheduleViewModel.kt          # Single source of truth; shared across all screens
```

---

## Navigation Flow

```
organizer ──[Request Availability]──► participant
participant ──[Submit (repeated per user)]──► participant   (recomposes in-place)
participant ──[All users submitted]──► resolution
resolution ──[Back]──► organizer
```

- The `participant` destination is popped from the back-stack when the resolution screen is entered, so pressing Back on the results screen returns to the organizer — not a stale participant screen.
- A single `ScheduleViewModel` is scoped to the `Activity` lifecycle, shared across all three destinations with zero serialisation overhead.

---

## Screen Descriptions

### Screen 1 — Organizer Initiation (`OrganizerScreen.kt`)
- **Event Name** input via `OutlinedTextField`
- **Availability Window** selection via two Material 3 `DatePickerDialog` instances (start and end dates)
- **Request Availability** CTA button — disabled until a non-blank event name is entered
- Non-modal `Snackbar` confirmation: `"Group Request Sent"`

### Screen 2 — Participant Availability Input (`ParticipantScreen.kt`)
- **Drag-to-paint grid** — a `14 × 7` matrix (08:00–21:00 × Mon–Sun)
- Continuous `detectDragGestures` pointer stream; every move event resolves a `(row, col)` pair and marks that cell as available
- Selected cells rendered in Material Green 800 (`#2E7D32`) for immediate visual feedback
- Cell sizing derived from `BoxWithConstraints` — no hard-coded dp values, ensuring correct touch-target sizes across all densities
- Simulates four participants sequentially: **Alice → Bob → Carol → Dave**
- **Submit Availability** CTA — disabled until at least one cell is painted

### Screen 3 — Algorithmic Resolution & Finalization (`ResolutionScreen.kt`)
- **Color-coded heatmap** — each cell's background interpolates from Green 200 (low consensus) to Green 900 (high consensus) using a continuous `lerp`
- **Optimal cell** highlighted with an accent `tertiary` border — the slot with the maximum participant count (ties broken by earliest index)
- **Organizer override** — tapping any populated heatmap cell selects it as the proposed final slot
- **Set Final Event** — opens a `ModalBottomSheet` with the final calendar summary: event name, date window, chosen day/time, and a participant consensus progress bar

---

## HCI Principles Applied

| Principle | Application |
|---|---|
| **Fitts' Law** | All primary CTA buttons span full screen width; date-picker trigger is a `TextButton` inside the field trailing area |
| **Hick's Law** | Fixed 14×7 grid eliminates open-ended time selection; date-range bounding shrinks the participant decision space |
| **Noun-Verb Paradigm** | Cell selection (Noun) gates the Submit/Finalize action (Verb); buttons are disabled until a valid selection exists |
| **Locus of Attention** | Non-blocking `Snackbar` and `ModalBottomSheet` overlays preserve screen context; heatmap remains visible behind the bottom sheet |
| **Pre-attentive Processing** | Monotonic green saturation gradient on the heatmap enables instant identification of high-consensus slots without reading numbers |
| **Error Prevention** | Submit disabled on empty draft; Finalize disabled on no selected cell; date picker uses native calendar validation |
| **Feedback Principle** | Immediate cell-color change on drag paint; Snackbar confirmations on submission and request dispatch |

---

## Build & Run

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK API 34+

### Steps

```bash
# Clone the repository
git clone https://github.com/qugaa/cheese.git
cd cheese

# Open in Android Studio or build via Gradle wrapper
./gradlew assembleDebug

# Install on a connected device or running emulator
./gradlew installDebug
```

Or open the project root in **Android Studio** and press **Run (⇧F10 / Shift+F10)**.

---

## Ignore Configuration

| File | Purpose |
|---|---|
| `.gitignore` | Excludes Android build outputs, local configurations, and `.clinerules/` from version control |
| `.clineignore` | Excludes Android build directories, Gradle caches, `.idea/`, and `.clinerules/` from AI code-context indexing |

> `.clinerules/` is excluded from **both** files to ensure local AI prompt instructions are never tracked or surfaced in code reviews.
