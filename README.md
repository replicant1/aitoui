# PxTx — Prescription Tracker

An Android application for managing prescription medications: tracking scripts, recording pharmacy
dispensations, monitoring tablets in hand, and projecting when supplies will run out.

**Package:** `com.example.aitoui`  
**Version:** 1.2.2 (versionCode 5)  
**Min SDK:** 24 (Android 7.0) · **Target SDK:** 36 · **Compile SDK:** 37  
**Database schema version:** 27

---

## Table of Contents

1. [What the App Does](#what-the-app-does)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Architecture Overview](#architecture-overview)
5. [Screen Inventory](#screen-inventory)
6. [Database Schema](#database-schema)
7. [Recurring Patterns & Idioms](#recurring-patterns--idioms)
8. [Interaction Diagrams](#interaction-diagrams)
9. [Building & Running](#building--running)
10. [Related Docs](#related-docs)

---

## What the App Does

PxTx models the lifecycle of a prescription medication from the doctor's surgery to the last tablet:

```
Doctor issues script
  → User scans or manually records the script (serial numbers, dose, repeats, valid-to date)
  → Pharmacist fills a repeat → user records a dispensation
  → User counts tablets in hand (manually or via camera)
  → App projects how long the supply will last against a daily schedule
  → Attention messages warn when a refill or new prescription is needed
```

### Key features

| Feature | Description |
|---|---|
| **Medication catalogue** | Brand name + active ingredient records; flags for over-the-counter vs. prescription |
| **Dispensable units** | Per-format records (dose / tablets-per-pack / tablet photo) under each medication |
| **Scripts** | Prescription records with serial numbers, repeats, valid-to date, and dispensation history |
| **Script scanning** | Camera + ML Kit OCR reads a PBS PB038 (yellow repeat-authorisation) form to pre-fill the Add Script screen |
| **Dispensations** | Each pharmacy fill is recorded against a script; the remaining fill count is derived |
| **Daily schedule** | How many tablets of each dispensable unit to take per day (supports fractional quantities) |
| **In Hand** | Tablet counts per dispensable unit; updated manually or via the camera counter |
| **Camera tablet counter** | CameraX still + classical CV (blob/peak segmentation) → auto-count with tap-to-correct |
| **Blister pack counter** | Camera + PCA geometry: segment packs, confirm grid layout, tap empty pockets to pop them |
| **Inventory** | Derived supply view per dispensable unit: in-hand days, script days, projected run-out date |
| **Run-out graph** | Visual projection of supply over time |
| **Attention messages** | Main-screen panel — "no scripts left", "running low", "get more from chemist" |
| **Backup / Restore** | Save/Load a `pxtx.zip` (database + tablet photos) to/from Downloads |

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | `androidx.navigation.compose` with type-safe `@Serializable` routes |
| Database | Room (KSP), hand-written migrations |
| Async | Kotlin Coroutines + `StateFlow` |
| Camera | CameraX (preview, `ImageCapture`) |
| OCR / barcode | ML Kit Text Recognition + Barcode Scanning |
| Image loading | Coil (`coil-compose`) |
| Serialization | `kotlinx.serialization.json` (navigation args + backup manifest) |
| DI | No framework — manual wiring via `Application` class + `APPLICATION_KEY` factory pattern |
| Build | Gradle (Kotlin DSL), version catalog (`libs.versions.toml`), KSP |

---

## Project Structure

The project is a **single `:app` module**. All source lives under
`app/src/main/java/com/example/aitoui/`.

```
com.example.aitoui/
├── AitouiApp.kt              Application — owns the singleton data layer
├── MainActivity.kt           Single activity; hosts the NavHost
├── MainScreen.kt             Main menu (2 × 4 grid) + attention messages + backup dialogs
├── MainViewModel.kt          Backup logic, attention-message derivation
│
├── alerts/                   Supply-warning rule engine
│   └── AttentionMessages.kt  attentionMessages() + medicationSupplies()
│
├── backup/                   Save / Restore to Downloads as pxtx.zip
│   ├── BackupManager.kt      Zip write / peek / restore (WAL-checkpoint aware)
│   ├── BackupFileName.kt     Default name generation + validation
│   ├── BackupManifest.kt     @Serializable { schemaVersion, createdAtMillis }
│   └── DownloadsBackupStore.kt  MediaStore / legacy-permission abstraction
│
├── counting/                 On-device CV counting engine (pure Kotlin, JVM-testable)
│   ├── TabletCounter.kt      Interface: count(bitmap, reference?) → List<PointF>
│   ├── BlobTabletCounter.kt  Otsu threshold → connected-component blobs → centroids
│   ├── PeakTabletCounter.kt  Alternate approach: local-maximum peaks
│   ├── Segmentation.kt       toGrayscale, otsuThreshold, foregroundMask
│   ├── PackSegmentation.kt   PCA-based blister-pack segmentation → PackRegion list
│   ├── PackGrid.kt           Grid math over PackRegion (cellCenter, tapToCell)
│   ├── MarkerEditing.kt      Tap-to-add / tap-to-remove marker hit-testing
│   ├── FrameBox.kt           Screen ↔ image coordinate mapping
│   └── TabletCrop.kt         Reference-image crop helper
│
├── dailyschedule/
│   ├── DailyScheduleScreen.kt
│   └── DailyScheduleViewModel.kt
│
├── data/                     Data layer — entities, DAOs, repositories, domain models
│   ├── AppDatabase.kt        Room @Database (version = DATABASE_SCHEMA_VERSION)
│   ├── Migrations.kt         ALL_MIGRATIONS (v21 → v27, hand-written)
│   ├── DoseUnit.kt           Enum with storedAbbreviation + string-resource IDs
│   ├── FuzzyMatcher.kt       Medication / dispensable-unit dedup (Levenshtein-based)
│   ├── TextSimilarity.kt     normalize() + ratio() helpers
│   ├── DatabaseSeeder.kt     Debug-only seed on first launch
│   ├── DatabaseDumper.kt     ASCII table dump to logcat
│   ├── SettingsRepository.kt SharedPreferences (warning window days)
│   ├── Medication*.kt        Entity, DAO, Repository, domain model
│   ├── DispensableUnit*.kt   Entity, DAO, Repository, domain model, Details projection
│   ├── Script*.kt            Entity, DAO, Repository, domain model, Details projection
│   ├── Dispensation*.kt      Entity, DAO, Repository, domain model
│   ├── DailySchedule*.kt     Entity, DAO, Repository, domain model, Details projection
│   ├── InHand*.kt            Entity, DAO, Repository, domain model, Details projection
│   └── InHandDate*.kt        Entity, DAO, single-row (id=0) gathered-date table
│
├── dispensableunit/
│   ├── DispensableUnitsScreen.kt  List screen
│   ├── DispensableUnitsViewModel.kt
│   ├── DispensableUnitScreen.kt   Add / Edit screen
│   ├── DispensableUnitViewModel.kt
│   └── DoseUnitFormatting.kt      Compose helpers: DoseUnit.abbreviation(), .displayName()
│
├── image/
│   ├── CameraCaptureScreen.kt  Reusable CameraX preview + capture composable
│   ├── FullImageViewer.kt      Full-screen Coil image viewer
│   └── ImageStore.kt           Internal-storage JPEG management (thumb + full-res)
│
├── inhand/
│   ├── InHandScreen.kt
│   ├── InHandViewModel.kt
│   ├── blister/                Blister-pack counter
│   │   ├── BlisterCountScreen.kt
│   │   ├── BlisterCountViewModel.kt
│   │   └── PopFeedback.kt      SoundPool pop + haptic
│   └── count/                  Loose-tablet camera counter
│       ├── CountTabletsScreen.kt
│       └── CountTabletsViewModel.kt
│
├── inventory/
│   ├── InventoryScreen.kt
│   ├── InventorySupply.kt      computeSupply() — derives days-of-supply per dispensable unit
│   └── InventoryViewModel.kt
│
├── medication/
│   ├── MedicationsScreen.kt    List screen
│   ├── MedicationsViewModel.kt
│   ├── MedicationScreen.kt     Add / Edit screen
│   └── MedicationViewModel.kt
│
├── navigation/
│   ├── Routes.kt               All @Serializable route objects / data classes
│   └── AppNavHost.kt           NavHost wiring; all cross-screen callbacks
│
├── runout/
│   ├── RunOutGraph.kt          Pure graph model
│   ├── RunOutGraphScreen.kt
│   └── RunOutGraphViewModel.kt
│
├── scan/
│   ├── ScanScriptScreen.kt     Camera + ML Kit OCR / barcode pipeline
│   ├── ScanScriptViewModel.kt
│   ├── PbsScriptParser.kt      Label-anchored PB038 text extraction (pure, JVM-testable)
│   └── OcrModels.kt            OcrLine, ParsedScript data classes
│
├── script/
│   ├── ScriptsScreen.kt        List screen + record-dispensation action
│   ├── ScriptsViewModel.kt
│   ├── AddScriptScreen.kt      Add / Edit screen (manual or pre-filled from scan)
│   ├── AddScriptViewModel.kt
│   └── ResolutionPrompts.kt    Medication / dispensable-unit resolution dialog text
│
├── settings/
│   ├── SettingsScreen.kt
│   └── SettingsViewModel.kt
│
└── ui/                         Shared UI components
    ├── AppTextField.kt         Themed OutlinedTextField wrapper
    ├── Heading.kt              heading() Modifier — semantic heading for accessibility
    ├── NumericInputSanitizer.kt  digitsOnly() and decimalInput() String extensions
    ├── SelectableRow.kt        Tappable list row with leading radio/checkbox
    ├── UnsavedChangesGuard.kt  AlertDialog — Save / Discard / Cancel on back navigation
    └── theme/                  Color, Shape, Type, Theme composables
```

---

## Architecture Overview

### Layers

```
┌─────────────────────────────────────────────────────────┐
│  Presentation (MVI)                                     │
│  ViewModel  ←→  State (StateFlow) / Action (sealed)     │
│  Screen composable (Root + Content split)               │
├─────────────────────────────────────────────────────────┤
│  Data layer                                             │
│  Repository  ←→  DAO  ←→  Room entity                   │
│  SettingsRepository  ←→  SharedPreferences              │
├─────────────────────────────────────────────────────────┤
│  Application (AitouiApp)                                │
│  Singleton owner: AppDatabase + all Repositories        │
└─────────────────────────────────────────────────────────┘
```

### Dependency injection

There is **no DI framework** (no Koin, no Hilt). `AitouiApp` constructs and holds the singleton
`AppDatabase` and all repositories as `lazy` properties — effectively acting as the DI container
itself. ViewModels obtain their dependencies via the `APPLICATION_KEY` factory pattern:

```kotlin
companion object {
    val Factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as AitouiApp
            MyViewModel(app.myRepository)
        }
    }
}
```

This gives the three things a DI framework is actually solving:

| DI concern | How this app handles it |
|---|---|
| Single instance of expensive objects (DB, repos) | `lazy` properties on `AitouiApp` |
| Inject only what each consumer needs | Constructor parameters declared on each ViewModel |
| Testability — swap real for fake | Pass fakes directly in the ViewModel constructor |

**Why no framework at this size?** The dependency graph is shallow (ViewModel → Repository → DAO,
three levels), there is only one Gradle module so `AitouiApp` is always in scope, there are no
feature-scoped singletons, and the total factory boilerplate (~50–60 lines across ~15 ViewModels)
is no more than the equivalent Koin module definitions would be.

**When this would change:** Adding a second Gradle module, introducing a "user session" scope, or
accumulating enough transitive dependencies that factory constructors become hard to maintain would
all be natural triggers for introducing Koin (or Hilt). Migrating at that point is
straightforward — each `viewModelFactory { initializer { … } }` block maps directly to a Koin
`viewModel { … }` definition.

### Navigation

All routes are `@Serializable` Kotlin objects / data classes in `Routes.kt`. `AppNavHost.kt` wires
them together, forwarding cross-screen callbacks (e.g. `onScanned`, `onCounted`) as lambda
parameters. The back-stack result pattern (`SavedStateHandle`) is used to return a camera count back
to In Hand:

```kotlin
const val TABLET_COUNT_RESULT = "tabletCount"
// CountTabletsViewModel writes: navController.previousBackStackEntry
//     ?.savedStateHandle?.set(TABLET_COUNT_RESULT, count)
// InHandViewModel reads it in init {} via savedStateHandle.getStateFlow(TABLET_COUNT_RESULT, null)
```

### Reactive data flow

Repositories expose `Flow<List<T>>` / `StateFlow` properties (Room DAOs return `Flow`). ViewModels
`combine` multiple flows and push a single immutable `State` object out via `MutableStateFlow`. The
Compose screen observes it with `collectAsStateWithLifecycle()`.

---

## Screen Inventory

| Screen | Route | Description |
|---|---|---|
| Main menu | `MainRoute` | 2 × 4 grid; attention messages; backup Save/Load |
| Medications list | `MedicationsRoute` | All medications with active ingredient |
| Add/Edit medication | `MedicationRoute` | Brand name, active ingredient, Rx flag |
| Dispensable units list | `DispensableUnitsRoute` | All units; formatted as `dose unit × qty` |
| Add/Edit dispensable unit | `DispensableUnitRoute` | Dose, unit, qty, tablet photo; fuzzy-dedup on save |
| Scripts list | `ScriptsRoute` | All scripts with fill progress; record-dispensation button |
| Add/Edit script | `ScriptRoute(…args)` | Full script form; fuzzy medication/unit resolution on save |
| Scan script (OCR) | `ScanScriptRoute` | Live CameraX preview → freeze → OCR → pre-fill ScriptRoute |
| Daily schedule | `DailyScheduleRoute` | Per-dispensable-unit daily quantities; replace-all on save |
| In Hand | `InHandRoute` | Per-dispensable-unit counts; camera icon launches counter |
| Count tablets | `CountTabletsRoute` | CameraX capture → auto-count → tap-to-correct → return int |
| Blister count | `BlisterCountRoute` | Segment packs → confirm grid → pop empties → return int |
| Inventory | `InventoryRoute` | Supply card per dispensable unit; sortable by name / time left |
| Run-out graph | `RunOutGraphRoute` | Visual supply projection over time |
| Settings | `SettingsRoute` | Warning-window days, app/DB version info |

---

## Database Schema

> Full schema detail: [`docs/DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md)  
> Entity-relationship diagram: [`docs/database-schema.png`](docs/database-schema.png)

The domain model forms a chain:

```
medications ──< dispensable_units ──< scripts ──< dispensations
                       │
                       ├──< daily_schedule
                       └──< in_hand
                                        in_hand_date  (single-row)
```

All foreign keys use `ON DELETE CASCADE`. A dispensation's "times filled" count is **derived** by
summing `dispensations.number` — it is never stored on the script itself.

### Schema version history (hand-written migrations)

| Migration | Change |
|---|---|
| 21 → 22 | Added `scripts.instructions` |
| 22 → 23 | Internal restructure |
| 23 → 24 | Added `medications.requiresPrescription` |
| 24 → 25 | Re-keyed `in_hand` from `medicationId` to `dispensableUnitId` |
| 25 → 26 | Re-keyed `daily_schedule` from `medicationId` to `dispensableUnitId` |
| 26 → 27 | Added `dispensable_units.doseUnit` (TEXT, DEFAULT 'mg') |

`fallbackToDestructiveMigration(dropAllTables = true)` acts as a crash-prevention safety net only
for version jumps not covered by any migration; in normal use it is never triggered.

---

## Recurring Patterns & Idioms

### 1. MVI presentation layer

Every feature follows the same skeleton:

```kotlin
// Immutable state snapshot — the only thing the composable reads
data class FooState(
    val items: List<FooItem> = emptyList(),
    val isLoading: Boolean = false,
)

// All user intents as a sealed hierarchy — the only thing the composable writes
sealed interface FooAction {
    data class ItemSelected(val id: Long) : FooAction
    data object SaveTapped : FooAction
}

class FooViewModel(private val repo: FooRepository) : ViewModel() {
    private val _state = MutableStateFlow(FooState())
    val state: StateFlow<FooState> = _state.asStateFlow()

    fun onAction(action: FooAction) { /* when (action) { … } */ }
}
```

The screen is split into two composables:
- **Root** (named `FooRoot`) — collects state with `collectAsStateWithLifecycle()`, obtains the
  ViewModel via `viewModel(factory = FooViewModel.Factory)`, and wires `onAction`.
- **Content** (named `FooScreen`) — stateless; takes `state` and `onAction` as parameters; has an
  `@Preview`.

### 2. ViewModel factory via `APPLICATION_KEY`

No Koin, no Hilt. Each ViewModel has a `companion object { val Factory = viewModelFactory { … } }`
that casts `this[APPLICATION_KEY]` to `AitouiApp` and extracts the required repositories.

### 3. Type-safe navigation routes

All routes are `@Serializable` Kotlin objects or data classes:

```kotlin
@Serializable
object InventoryRoute          // no arguments

@Serializable
data class ScriptRoute(        // optional pre-filled arguments
    val brandName: String? = null,
    val serialNo: String? = null,
    // …
)
```

Navigation from a screen is passed as a plain lambda (`onNavigateToInventory: () -> Unit`), so
composables never depend on `NavController` directly.

### 4. Unsaved-changes guard

Any add/edit screen that can be navigated away from intercepts both the top-bar back arrow and the
Android system back with a `BackHandler`. When unsaved changes exist, `UnsavedChangesDialog` is
shown offering **Save / Discard / Cancel**. If the form is incomplete (cannot save), only **Discard /
Cancel** are offered.

### 5. Numeric input sanitizers

`NumericInputSanitizer.kt` provides two `String` extension functions used on `onValueChange`:

```kotlin
// Integer-only fields (tablets per pack, repeats, …)
internal fun String.digitsOnly(): String = filter { it.isDigit() }

// Decimal fields (dose per tablet: e.g. 0.5 mg)
internal fun String.decimalInput(): String { /* normalises comma→dot, single separator, leads with 0 */ }
```

### 6. `DoseUnit` enum with constructor properties

The dose unit is stored in the database as a short ASCII token (`"mg"`, `"g"`, `"IU"`, …). The enum
carries both the token and the string resource IDs, eliminating `when` mappings:

```kotlin
enum class DoseUnit(
    val storedAbbreviation: String,        // stored in DB; used for comparisons
    @StringRes val abbreviationResId: Int, // for Compose UI
    @StringRes val displayNameResId: Int,
) {
    MILLIGRAMS("mg", R.string.dose_unit_milligrams_abbreviation, R.string.dose_unit_milligrams_display_name),
    // …
}

// Look up from DB string:
fun doseUnitFromAbbreviation(abbr: String): DoseUnit? =
    DoseUnit.entries.firstOrNull { it.storedAbbreviation == abbr }
```

### 7. Fuzzy medication / dispensable-unit deduplication

`FuzzyMatcher` (backed by `TextSimilarity.ratio()` — a Levenshtein-based similarity score)
classifies entered values against existing records when the user saves a new script:

- **Exact** match → offer the existing record for selection (skip creation).
- **Similar** (score ≥ 0.45 on either field) → offer as a candidate.
- **Blocked** (score ≥ 0.90 on both fields) → refuse creation of a near-duplicate.

For dispensable units the block requires matching dose, tablets-per-unit **and** dose unit, so
`50 mg × 60` and `50 IU × 60` are correctly treated as different formats.

### 8. Camera counter phase machine

Both `CountTabletsViewModel` and `BlisterCountViewModel` use an explicit phase enum to drive the UI:

```
RequestingPermission → Previewing → Captured(path) → Analyzing
    → Review(markers) → (returns count)          (loose tablets)

RequestingPermission → Previewing → Captured(path) → Segmenting
    → ConfirmLayout(packIndex) → Popping(packIndex) → Summary  (blister packs)
```

The captured image path is stored in the ViewModel (not the composable) so the frozen frame
survives rotation.

### 9. Backup / Restore

A backup is a ZIP file (`pxtx.zip`) containing:

```
manifest.json              { "schemaVersion": 27, "createdAtMillis": … }
database/aitoui.db
images/unit_images/        thumbnail JPEGs
images/unit_images_full/   full-res JPEGs
```

Before writing, `AitouiApp.checkpointDatabase()` merges the WAL into the main file so the copy is
complete. Before restoring, `AitouiApp.closeDatabase()` releases Room's handle; the app process is
then restarted via `Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` + `Runtime.exit(0)`.

### 10. OCR script parsing (`PbsScriptParser`)

The parser is label-anchored: it finds known printed labels on a PB038 form (e.g. "valid to",
"repeats", "eRx:") and reads the adjacent value. It is pure Kotlin, JVM-testable (takes a
`List<OcrLine>`), and deliberately best-effort — the Add Script review screen is the safety net.
O/0 confusion (common with digit OCR) is repaired with `normaliseDigits()`.

---

## Interaction Diagrams

### Use case 1 — Recording a dispensation from the Scripts screen

The user selects a script and records a pharmacy fill. The dispensation is persisted, and the
in-hand count for that dispensable unit is incremented atomically.

```mermaid
sequenceDiagram
    actor User
    participant ScriptsScreen
    participant ScriptsViewModel
    participant ScriptRepository
    participant DispensationRepository
    participant InHandRepository
    participant Room

    User->>ScriptsScreen: Taps "Record fill" on a script row
    ScriptsScreen->>ScriptsViewModel: onAction(RecordDispensation(scriptId, dispensableUnitId, tabletsPerUnit))
    ScriptsViewModel->>ScriptRepository: getScript(scriptId)
    ScriptRepository->>Room: SELECT … FROM scripts WHERE id = ?
    Room-->>ScriptRepository: ScriptEntity
    ScriptsViewModel->>DispensationRepository: insert(DispensationEntity(scriptId, dispensableUnitId, number=1, now))
    DispensationRepository->>Room: INSERT INTO dispensations …
    Room-->>DispensationRepository: new dispensation id
    ScriptsViewModel->>InHandRepository: addTablets(dispensableUnitId, tabletsPerUnit)
    InHandRepository->>Room: UPDATE in_hand SET quantity = quantity + ? WHERE dispensableUnitId = ?
    Room-->>InHandRepository: rows updated
    ScriptsViewModel->>ScriptsViewModel: _state.update { it.copy(confirmation = "Fill recorded") }
    ScriptsViewModel-->>ScriptsScreen: emits updated ScriptsState (via StateFlow)
    ScriptsScreen-->>User: Shows confirmation snackbar; script fill count increments
```

---

### Use case 2 — Camera tablet count feeding the In Hand screen

The user photographs loose tablets; the app auto-counts them, lets the user correct the result,
then returns the integer to the In Hand screen.

```mermaid
sequenceDiagram
    actor User
    participant InHandScreen
    participant InHandViewModel
    participant NavController
    participant CountTabletsScreen
    participant CountTabletsViewModel
    participant BlobTabletCounter

    User->>InHandScreen: Selects a medication, taps camera icon on "Number of tablets"
    InHandScreen->>NavController: navigate(CountTabletsRoute)
    NavController->>CountTabletsScreen: opens screen

    CountTabletsScreen->>CountTabletsViewModel: (init) phase = RequestingPermission
    CountTabletsViewModel-->>CountTabletsScreen: state.phase = Previewing (after permission granted)
    CountTabletsScreen-->>User: Shows live camera preview

    User->>CountTabletsScreen: Taps Capture button
    CountTabletsScreen->>CountTabletsViewModel: onAction(Capture)
    CountTabletsViewModel->>CountTabletsViewModel: saves image to internal storage (ImageStore)
    CountTabletsViewModel->>BlobTabletCounter: count(bitmap)
    BlobTabletCounter->>BlobTabletCounter: toGrayscale → otsuThreshold → connectedComponents → centroids
    BlobTabletCounter-->>CountTabletsViewModel: List<PointF> (one point per detected tablet)
    CountTabletsViewModel-->>CountTabletsScreen: state.phase = Review(markers)

    CountTabletsScreen-->>User: Shows frozen image with marker overlay and count (e.g. "24 tablets")

    User->>CountTabletsScreen: Taps on missed tablet (empty space)
    CountTabletsScreen->>CountTabletsViewModel: onAction(AddMarker(offset))
    CountTabletsViewModel->>CountTabletsViewModel: markers += offset; count = markers.size
    CountTabletsViewModel-->>CountTabletsScreen: updated state (count = 25)

    User->>CountTabletsScreen: Taps "Use 25"
    CountTabletsScreen->>CountTabletsViewModel: onAction(UseCount)
    CountTabletsViewModel->>NavController: previousBackStackEntry?.savedStateHandle[TABLET_COUNT_RESULT] = 25
    CountTabletsViewModel->>NavController: popBackStack()

    NavController->>InHandScreen: back-stack restored
    InHandViewModel->>InHandViewModel: savedStateHandle.getStateFlow(TABLET_COUNT_RESULT) emits 25
    InHandViewModel-->>InHandScreen: state.numberOfTablets = "25"
    InHandScreen-->>User: "Number of tablets" field populated with 25
```

---

## Building & Running

### Prerequisites

- Android Studio (Meerkat or later recommended)
- JDK 11+
- Android SDK — compile SDK 37, build-tools matching

### Clone and open

```zsh
git clone git@github-rodney:replicant1/aitoui.git
cd aitoui
# Open in Android Studio: File → Open → select the aitoui folder
```

### Build and install (command line)

```zsh
# Debug build
./gradlew installDebug

# Release build (unsigned)
./gradlew assembleRelease
```

### Debug seed data

In a debug build, `DatabaseSeeder` auto-populates an empty database on first launch with a moderate
set of sample medications, dispensable units, scripts, dispensations, a daily schedule, and in-hand
quantities — so the app can be exercised immediately without manual data entry.

### Backup file format

Backups are written to the device's Downloads folder as `pxtx_v<schema>_<date>.zip`. They can be
transferred between devices; the app validates the schema version on load and runs any outstanding
Room migrations automatically.

---

## Related Docs

| Document | Description |
|---|---|
| [`docs/DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md) | Full table definitions, column types, constraints, relationships, migration history |
| [`docs/database-schema.png`](docs/database-schema.png) | UML class diagram (rendered from the Mermaid source in the schema doc) |
| [`docs/tablet-counting-mvp.md`](docs/tablet-counting-mvp.md) | MVP design for the loose-tablet camera counter: user flow, counting engine interface, wiring into In Hand |
| [`docs/blister-counting-mvp.md`](docs/blister-counting-mvp.md) | MVP design for the blister-pack camera counter: geometry-only approach, phase machine, PCA segmentation, pop-the-empties UX |
| [`docs/ACCESSIBILITY_AUDIT.md`](docs/ACCESSIBILITY_AUDIT.md) | Accessibility review findings and remediation status |

