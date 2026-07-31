# PxTx — Prescription Tracker

An Android application for managing prescription medications: tracking scripts, recording pharmacy
dispensations, monitoring tablets in hand, and projecting when supplies will run out.

**Package:** `com.example.aitoui`  
**Version:** 1.2.2 (versionCode 5)  
**Min SDK:** 24 (Android 7.0) · **Target SDK:** 36 · **Compile SDK:** 37  
**Database schema version:** 27  
**Toolchain:** Gradle 9.4.1 · AGP 9.2.1 · Kotlin 2.4.0 · Compose BOM 2026.06.00

---

## Table of Contents

1. [What the App Does](#what-the-app-does)
2. [Walkthrough](#walkthrough)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Architecture Overview](#architecture-overview)
6. [Screen Inventory](#screen-inventory)
7. [Database Schema](#database-schema)
8. [Recurring Patterns & Idioms](#recurring-patterns--idioms)
9. [Interaction Diagrams](#interaction-diagrams)
10. [Testing](#testing)
11. [Building & Running](#building--running)
12. [Related Docs](#related-docs)

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
| **Dispensable units** | Per-format records (dose / dose unit / tablets-per-pack / tablet photo) under each medication |
| **Scripts** | Prescription records with serial numbers, repeats, valid-to date, and dispensation history |
| **Script scanning** | Camera + ML Kit OCR reads a PBS PB038 (yellow repeat-authorisation) form to pre-fill the Add Script screen |
| **Dispensations** | Each pharmacy fill is recorded against a script; the remaining fill count is derived |
| **Daily schedule** | How many tablets of each dispensable unit to take per day (supports fractional quantities) |
| **In Hand** | Tablet counts per dispensable unit; updated manually, or via either camera counter (a menu on the field's camera icon) |
| **Camera tablet counter** | CameraX still + classical CV (distance-transform peaks) → auto-count, live sensitivity slider, optional crop, tap-to-correct |
| **Blister pack counter** | Camera + PCA geometry: auto-frame the packs, adjust the frames by hand, set the pocket grid, tap empty pockets to pop them |
| **Inventory** | Derived supply view per dispensable unit: in-hand days, script days, projected run-out date |
| **Run-out graph** | Visual projection of supply over time |
| **Attention messages** | Main-screen panel — "no scripts left", "running low (scripts still available)", "running low, need a new script", "restock from the chemist" (OTC) |
| **Backup / Restore** | Save/Load a `pxtx-<ddMMyyyy>-db<schema>.zip` (database + tablet photos) to/from Downloads |

---

## Walkthrough

These are real screenshots from a Pixel 3 running a real medication list — nine medications, ten
scripts, and real tablet photos. Brand names and active ingredients are pixelated throughout, as are
the personal details on the PB038 form below. Everything that makes the screens worth showing is
untouched: doses, dose units, pack sizes, quantities, frequencies, counts, durations and dates are
all exactly as the app produced them.

### The main screen

Everything starts here: a 2 × 4 grid of destinations, with the attention panel underneath. The panel
is the app's whole reason for existing — it is derived on every launch from the daily schedule, the
tablets in hand and the repeats left on each script, and it says the one thing worth knowing: what is
about to run out, and whether a chemist visit or a doctor visit is needed to fix it. When nothing is
low the panel disappears entirely.

<table align="center">
<tr>
<td align="center" valign="top"><img src="docs/screenshots/01-home.png" width="230" alt="Main screen with the 2x4 menu grid and three attention messages"><br><sub><b>Fig 1.</b> The main menu, with the attention panel listing what is about to run out.</sub></td>
</tr>
</table>

### Flow 1 — Building the catalogue

Before anything can be tracked it has to be described, and the app splits that into two levels. A
**medication** is the brand name plus its active ingredient, with a switch for whether it needs a
prescription at all — over-the-counter items still get tracked, they just never generate "go to the
doctor" warnings. A **dispensable unit** is one particular format of that medication: the dose, the
unit that dose is measured in, and how many tablets come in a pack. The split matters because a
prescription is written against a *format*, not a drug — 40mg × 60 and 80mg × 30 of the same statin
are two different things to count and re-order. Each unit can carry a photo of the actual tablet,
which is what makes the lists scannable at a glance and is worth the ten seconds it takes to snap.

<table align="center">
<tr>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/02-medications.png" width="215" alt="Medications list showing nine medications with active ingredients"><br><sub><b>Fig 2.</b> Every medication on record, each shown with its active ingredient.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/04-medication-add-filled.png" width="215" alt="Medication form with brand name, active ingredient and a requires-prescription switch"><br><sub><b>Fig 3.</b> Adding a medication: brand, ingredient, and whether it needs a prescription.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/05-unsaved-changes.png" width="215" alt="Unsaved changes dialog offering Cancel, Discard and Save"><br><sub><b>Fig 4.</b> Leaving a form with unsaved edits offers Save, Discard or Cancel.</sub></td>
</tr>
</table>

<table align="center">
<tr>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/06-dispensable-units.png" width="215" alt="Dispensable units list with tablet photos, doses and pack sizes"><br><sub><b>Fig 5.</b> One row per dose and pack size, each carrying a photo of the tablet.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/07-full-photo.jpg" width="215" alt="A tablet photo opened full-size"><br><sub><b>Fig 6.</b> Long-pressing a thumbnail opens that tablet photo full-size.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/10-dose-unit-menu.png" width="215" alt="Dispensable unit form with the dose unit dropdown open showing mg, g, IU, mL and micrograms"><br><sub><b>Fig 7.</b> The dose unit is an explicit choice, from milligrams to micrograms.</sub></td>
</tr>
</table>

Two details visible above: leaving a half-finished form always asks before discarding your typing,
and the dose unit is a real choice rather than an assumption — one of the medications above is dosed
in micrograms, and a unit measured in μg is deliberately *not* treated as a duplicate of the same
number in mg.

### Flow 2 — Recording a prescription

An Australian PBS repeat authorisation (form PB038) is the yellow slip the pharmacist staples to the
bag. It carries everything the app needs — serial number, eRx token, issue date, valid-to date, and
how many repeats you have left — so rather than retyping it, the **+** on the Scripts screen opens a
camera that reads the form with ML Kit and pre-fills the Add Script form from it. The parser is
label-anchored and deliberately best-effort: it finds printed labels like "valid to" and "eRx:" and
reads the value beside them, and if OCR misses the eRx token it falls back to decoding the form's
barcode. Whatever it produces lands in an ordinary editable form, which is the safety net — you check
it, fix anything that came out wrong, and save. Scripts are then listed with their fill progress, and
tapping the **+** on a card records a pharmacy fill: the dispensation is written, the pack's tablets
are added to your in-hand tally, and when the last repeat is used the script retires itself.

<table align="center">
<tr>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/pb038-form.jpg" width="290" alt="A PBS PB038 repeat authorisation form with personal, prescriber, pharmacy and medication details pixelated out"><br><sub><b>Fig 8.</b> A PBS repeat authorisation form — the input the scanner reads.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/13-scan-script.jpg" width="215" alt="The scan screen asking you to fit the whole PB038 form in the frame"><br><sub><b>Fig 9.</b> The scanner, waiting for the whole form to be framed.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/14-add-script-blank.png" width="215" alt="The Add Script review form with fields for brand, dose, serial number and eRx token"><br><sub><b>Fig 10.</b> The review form, where scanned values land to be checked before saving.</sub></td>
</tr>
</table>

<table align="center">
<tr>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/11-scripts.png" width="215" alt="Scripts list showing dispensed and repeats counts per script"><br><sub><b>Fig 11.</b> Scripts listed with their fill progress and remaining repeats.</sub></td>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/12-dispense-dialog.png" width="215" alt="Dialog confirming one dispensation, adding 60 tablets to the in-hand tally"><br><sub><b>Fig 12.</b> Recording a pharmacy fill, which also tops up the in-hand tally.</sub></td>
</tr>
</table>

The form above is the one that produced one of the scripts in this database — serial `PW2048021`,
five repeats, valid to 17/06/2027. The scanner screenshot shows the framing guidance as it appears in
use; the review form beside it is where the scanned values land.

### Flow 3 — Counting loose tablets

Knowing how long a supply lasts means knowing how much is actually in the bottle, and counting
sixty tablets by hand is the chore most likely to stop someone using the app at all. So the camera
icon on the In Hand field opens a counter: photograph the tablets spread on a contrasting surface and
the app counts them. The engine is plain Kotlin — a distance transform over the thresholded image,
whose local maxima are tablet centres, which separates tablets that touch instead of merging them
into one blob. Because no photo is perfect, two corrections sit on top of the result: a sensitivity
slider that re-selects from the already-computed peaks in real time (glare and fabric texture drop
out as you drag it), and an optional crop for when the surround is cluttered. The last word is
always yours — tap any missed tablet to add a marker, tap a marker to remove it — and the confirmed
number is handed back to the In Hand field.

<table align="center">
<tr>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/16-in-hand.png" width="215" alt="In Hand screen listing tablets currently in hand as of a gathered date"><br><sub><b>Fig 13.</b> In Hand — what is currently in the cupboard, and when it was last counted.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/17-count-menu.png" width="215" alt="Menu offering loose tablets or blister packs"><br><sub><b>Fig 14.</b> The camera icon offers either counter: loose tablets or blister packs.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/18-count-camera.jpg" width="215" alt="Camera screen telling you to spread the tablets in a single layer"><br><sub><b>Fig 15.</b> The loose-tablet camera, with its capture guidance.</sub></td>
</tr>
</table>

<table align="center">
<tr>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/19-count-detect.jpg" width="215" alt="127 tablets detected, each with a marker"><br><sub><b>Fig 16.</b> The first pass over the photo, one marker per detected tablet.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/20-count-sensitivity.jpg" width="215" alt="The same photo at a lower sensitivity, now 125 tablets"><br><sub><b>Fig 17.</b> The sensitivity slider dropping stray marks from glare and fabric texture.</sub></td>
<td align="center" width="33%" valign="top"><img src="docs/screenshots/21-count-crop.jpg" width="215" alt="Crop step with a draggable box over the tablets"><br><sub><b>Fig 18.</b> The optional crop, restricting detection to just the tablets.</sub></td>
</tr>
</table>

<table align="center">
<tr>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/22-count-edit.jpg" width="215" alt="Edit step inviting you to tap a missed tablet or a wrong marker"><br><sub><b>Fig 19.</b> The correction step: tap a missed tablet to add it, or a marker to remove it.</sub></td>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/23-in-hand-counted.png" width="215" alt="The count of 125 returned into the Number of tablets field"><br><sub><b>Fig 20.</b> The confirmed count handed back to the In Hand field.</sub></td>
</tr>
</table>

### Flow 4 — Counting blister packs

Blister packs need the opposite approach. Counting *tablets* through foil is unreliable, so the app
never tries: it does geometry only and lets you supply the one fact a camera cannot see. Photograph
the packs, and PCA segmentation finds each one and drops a numbered frame over it, which you can
drag, resize, rotate, add to or delete until the frames match reality. You then state the pocket
layout — 2 × 5 here — and the app lays a grid of circles over each pack. Every pocket starts **full**,
and you tap the empty ones to pop them, with a click and a haptic tick for each. Nothing is ever
classified automatically, so the count cannot be confidently wrong; the summary totals the packs and
hands the number back to In Hand exactly as the loose counter does.

<table align="center">
<tr>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/24-blister-frame.jpg" width="215" alt="Frame the packs step with three detected packs, each under a numbered frame"><br><sub><b>Fig 21.</b> Packs found and framed automatically, ready to be adjusted by hand.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/25-blister-format.jpg" width="215" alt="Pack format step setting 2 columns by 5 rows"><br><sub><b>Fig 22.</b> The pocket layout, stated once and applied to every pack in the shot.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/26-blister-pop.jpg" width="215" alt="Pop blisters step with two pockets popped, showing Full 8 and Empty 2"><br><sub><b>Fig 23.</b> Pockets start full; tapping an empty one pops it and updates the tally.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/27-blister-summary.png" width="215" alt="Summary totalling 28 tablets remaining across three packs"><br><sub><b>Fig 24.</b> The totals, added up across all three packs.</sub></td>
</tr>
</table>

The pocket layout is set once and applies to every pack in the shot, and the grid is seeded from each
pack's own principal axes — which is why the circles land on the pockets above without being touched.
When a pack sits at an awkward angle they can still be dragged into line and pinched to match the
pocket spacing, so the instruction stays on screen throughout.

### Flow 5 — Planning ahead

This is where the pieces pay off. The **daily schedule** records how many tablets of each dispensable
unit you take per day, fractions included, since a half tablet daily is a real prescription and a
supply that lasts twice as long. **Inventory** then does the arithmetic the app exists to do: for each
unit it decays your last in-hand count by the days elapsed since you took it, adds the tablets still
locked up in unfilled repeats, divides by the daily rate, and reports both a duration and a calendar
date. The **run-out graph** plots the same projection as a straight decline per unit, with a draggable
cursor that reads off what will be left at any future date — useful for the real question, which is
whether a single trip to the doctor can cover everything before a holiday.

<table align="center">
<tr>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/28-daily-schedule.png" width="215" alt="Daily schedule listing tablets taken per day per unit"><br><sub><b>Fig 25.</b> The daily schedule — how many tablets of each unit are taken per day.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/29-inventory.png" width="215" alt="Inventory showing weeks or months remaining and a run-out date per unit"><br><sub><b>Fig 26.</b> How long each supply lasts, and the date it is projected to run out.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/30-runout-graph.png" width="215" alt="Run-out graph with one declining line per dispensable unit"><br><sub><b>Fig 27.</b> The run-out graph, one declining line per dispensable unit.</sub></td>
<td align="center" width="25%" valign="top"><img src="docs/screenshots/31-runout-cursor.png" width="215" alt="The graph cursor dragged to February, with remaining amounts listed"><br><sub><b>Fig 28.</b> Dragging the cursor reads off what will be left at a chosen date.</sub></td>
</tr>
</table>

The inventory entries above and the attention messages on the main screen agree, as they should: the
medication at the top of the inventory has 1.3 weeks left and no repeats, so it needs a doctor rather
than a chemist.

### Flow 6 — Settings and backup

Two knobs and a safety net. The warning window decides how far ahead the attention panel looks —
fourteen days by default, which is roughly the gap between noticing and being able to act. **Save**
writes the whole database plus every tablet photo to a zip in your Downloads folder, named for the
date and schema version so several backups can sit side by side, and **Load** reads one back, running
any outstanding database migrations on the way in. That is also the migration path between phones.

<table align="center">
<tr>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/32-settings.png" width="215" alt="Settings screen with the warning window in days and the app and database versions"><br><sub><b>Fig 29.</b> Settings — how many days ahead the attention panel looks.</sub></td>
<td align="center" width="50%" valign="top"><img src="docs/screenshots/33-save-dialog.png" width="215" alt="Save backup dialog with the default file name pxtx-30072026-db27.zip"><br><sub><b>Fig 30.</b> Saving a backup of the database and every tablet photo.</sub></td>
</tr>
</table>

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
| Build | Gradle (Kotlin DSL), version catalog (`gradle/libs.versions.toml`), KSP |
| Unit tests | JUnit 5 (Jupiter) + Vintage engine, Turbine, AssertK, Mockito, `kotlinx-coroutines-test` |
| UI tests | Compose `ui-test-junit4` + Espresso, driven through per-screen Robot classes |
| Coverage | JaCoCo (`./gradlew jacocoTestReport`) |

---

## Project Structure

The project is a **single `:app` module**. Production source lives under
`app/src/main/java/com/example/aitoui/`; `app/src/test/…` (JVM unit tests) and
`app/src/androidTest/…` (Compose UI tests) mirror the same package layout — see [Testing](#testing).

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
├── backup/                   Save / Restore to Downloads as pxtx-<date>-db<schema>.zip
│   ├── BackupManager.kt      Zip write / peek / restore (WAL-checkpoint aware)
│   ├── BackupFileName.kt     Default name generation + validation
│   ├── BackupManifest.kt     @Serializable { schemaVersion, createdAtMillis }
│   └── DownloadsBackupStore.kt  MediaStore / legacy-permission abstraction
│
├── counting/                 On-device CV counting engine (pure Kotlin, no Android types, JVM-testable)
│   ├── TabletCounter.kt      CountImage / CountPoint / ReferenceImage + the
│   │                         TabletCounter interface: count(image, reference?) → List<CountPoint>
│   ├── PeakTabletCounter.kt  The counter in use: distance-transform peaks (a light watershed, so
│   │                         touching tablets separate). Split into analyse() → PeakField and
│   │                         PeakField.select(sensitivity) so the slider re-counts cheaply
│   ├── BlobTabletCounter.kt  Simpler reference implementation: Otsu → connected components →
│   │                         centroids. Superseded by PeakTabletCounter; kept for comparison
│   ├── Segmentation.kt       toGrayscale, otsuThreshold, foregroundMask
│   ├── PackSegmentation.kt   PCA-based blister-pack segmentation → PackRegion list
│   ├── PackGrid.kt           Grid math over PackRegion (cellCenter, tapToCell, GridAdjust nudging)
│   ├── MarkerEditing.kt      editMarkers() — tap-to-add / tap-to-remove marker hit-testing
│   ├── FrameBox.kt           Hand-editable oriented rectangle framing one pack (drag / resize /
│   │                         rotate hit-testing); converts to and from PackRegion
│   └── TabletCrop.kt         PixelRect + CountImage.cropped() — restrict detection to a framed region
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
    ├── AppTextField.kt         App-wide OutlinedTextField wrapper: FieldRequirement
    │                           (Required by default), "(optional)" labelling, error semantics
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
parameters. The back-stack result pattern (`SavedStateHandle`) returns a camera count back to In Hand —
and it lives entirely in `AppNavHost`, so neither ViewModel touches navigation:

```kotlin
const val TABLET_COUNT_RESULT = "tabletCount"

// In AppNavHost, the counter screens report their result upwards:
composable<CountTabletsRoute> {
    CountTabletsRoot(onCounted = { count ->
        navController.previousBackStackEntry?.savedStateHandle?.set(TABLET_COUNT_RESULT, count)
        navController.popBackStack()
    }, onBack = { navController.popBackStack() })
}

// …and the In Hand entry observes its own saved state, passing the value down as a parameter:
composable<InHandRoute> { entry ->
    val countedTablets by entry.savedStateHandle
        .getStateFlow<Int?>(TABLET_COUNT_RESULT, null)
        .collectAsStateWithLifecycle()
    InHandRoot(
        countedTablets = countedTablets,
        onCountedConsumed = { entry.savedStateHandle[TABLET_COUNT_RESULT] = null },
        …
    )
}
```

`InHandRoot` applies it in a `LaunchedEffect(countedTablets)`, announces it for accessibility, then
calls `onCountedConsumed()` so the value can't be re-applied on the next recomposition.

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
| Dispensable units list | `DispensableUnitsRoute` | All units, formatted `<dose><unit> × Qty <n>` (e.g. `50mg × Qty 60`) |
| Add/Edit dispensable unit | `DispensableUnitRoute` | Dose, dose unit, qty, tablet photo; fuzzy-dedup on save |
| Scripts list | `ScriptsRoute` | All scripts with fill progress; tap the "dispensed" area to record a fill (confirmed by dialog), delete a script, sort by issue date / brand name |
| Add/Edit script | `ScriptRoute(…args)` | Full script form; fuzzy medication/unit resolution on save |
| Scan script (OCR) | `ScanScriptRoute` | Live CameraX preview → freeze → OCR → pre-fill ScriptRoute |
| Daily schedule | `DailyScheduleRoute` | Per-dispensable-unit daily quantities; replace-all on save |
| In Hand | `InHandRoute` | Per-dispensable-unit counts; the camera icon opens a menu offering either counter |
| Count tablets | `CountTabletsRoute` | CameraX capture → detect (sensitivity slider, optional crop) → edit (tap to correct) → return int |
| Blister count | `BlisterCountRoute` | Capture → frame packs → set pocket grid → pop empties per pack → summary → return int |
| Inventory | `InventoryRoute` | Supply card per dispensable unit; sortable by name / time left |
| Run-out graph | `RunOutGraphRoute` | Visual supply projection over time |
| Settings | `SettingsRoute` | Warning-window days, app/DB version info |

---

## Database Schema

> Full schema detail: [`docs/DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md)  
> UML class diagram (all columns): [`docs/database-schema.png`](docs/database-schema.png)

The domain model forms a chain:

```mermaid
erDiagram
    medications ||--o{ dispensable_units : medicationId
    dispensable_units ||--o{ scripts : dispensableUnitId
    scripts ||--o{ dispensations : scriptId
    dispensable_units ||--o{ dispensations : dispensableUnitId
    dispensable_units ||--o{ daily_schedule : dispensableUnitId
    dispensable_units ||--o{ in_hand : dispensableUnitId
    in_hand_date {
        Int id "single row, PK fixed at 0"
        Long gatheredAtMillis "when In Hand was last saved"
    }
```

All foreign keys use `ON DELETE CASCADE`; `in_hand_date` stands alone. A script's "times filled" count
is **derived** by summing `dispensations.number` — it is never stored on the script itself.

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
- **Similar** (`SIMILAR = 0.45` on any one of the four field comparisons) → offer as a candidate. The
  threshold is deliberately low: the resolve dialog always confirms the pick, so a spare candidate
  costs a glance whereas a missed one costs a duplicate record.
- **Blocked** (`BLOCK = 0.90` on brand *and* active ingredient) → refuse creation of a near-duplicate.

Both the match and the block are computed in *either* orientation — entered brand vs. brand and active
vs. active, or brand vs. active and active vs. brand — so a medication entered with the two fields
transposed still resolves to (and is blocked by) the existing record.

For dispensable units there is no fuzzy scoring: all of the resolved medication's units are offered as
candidates, and creation is blocked only when dose, tablets-per-unit **and** dose unit all match
(after normalisation), so `50 mg × 60` and `50 IU × 60` are correctly treated as different formats.

### 8. Camera counter phase machine

Both `CountTabletsViewModel` and `BlisterCountViewModel` drive their UI from an explicit phase enum
(`CountPhase` / `BlisterPhase`). Camera permission is *not* a phase — each camera screen gates itself
on `Manifest.permission.CAMERA` in the composable (`PermissionGate` / `CameraCaptureScreen`) before any
phase is entered.

Loose tablets — `CountPhase`:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> DETECT: analyse(capture)
    DETECT --> DETECT: sensitivity slider re-selects peaks
    DETECT --> CROP: "Crop"
    CROP --> DETECT: apply / cancel crop (re-analyses)
    DETECT --> EDIT: "Next"
    EDIT --> EDIT: tap to add / remove a marker
    EDIT --> [*]: "Use N" → count returned
    DETECT --> [*]: "Retake" → back to capture
```

Blister packs — `BlisterPhase`:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> CAPTURE
    CAPTURE --> FRAME: segmentPacks() seeds one FrameBox per pack
    FRAME --> FRAME: drag / resize / rotate / add / delete frames
    FRAME --> FORMAT: confirmFrames()
    FORMAT --> FORMAT: set rows × columns
    FORMAT --> POP: confirmFormat()
    POP --> POP: tap empty pockets; nudge / stretch the grid; nextPack()
    POP --> SUMMARY: after the last pack
    SUMMARY --> [*]: total returned
```

Both ViewModels hold the captured image path (not the composable), so the frozen frame survives
rotation, and both delete that temp capture in `onCleared()`. Note these two deviate from the
`onAction(Action)` MVI convention used elsewhere: they expose named methods (`analyse`,
`setSensitivity`, `applyCrop`, `confirmFrames`, `popAt`, `stepBack`, …) because the camera screens
call them from gesture and lifecycle callbacks rather than from a single event sink.

### 9. Backup / Restore

A backup is a ZIP file — named `pxtx-<ddMMyyyy>-db<schema>.zip` by default (e.g.
`pxtx-30072026-db27.zip`, see `BackupFileName.default()`), editable by the user in the Save dialog —
containing:

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
O/0 confusion (common with digit OCR) is repaired with `normaliseDigits()`. If OCR misses the second
serial number, `PbsScriptParser.erxFromBarcodes()` recovers the eRx token from the form's Code 128 /
QR barcodes via ML Kit barcode scanning.

### 11. Required-by-default form fields (`AppTextField`)

Every form field goes through `AppTextField`, which carries the app's requirement convention: fields
are **required by default** and only the minority are marked, by appending "(optional)" to the visible
label text (not an asterisk or a colour, so TalkBack reads it for free and WCAG 1.4.1 / 3.3.2 hold).
Each form states the convention once via `requiredFieldsNote()` in its intro text.

```kotlin
enum class FieldRequirement { Required, Optional }

AppTextField(
    value = state.serialNo2,
    onValueChange = { onAction(AddScriptAction.SerialNo2Changed(it)) },
    label = stringResource(R.string.add_script_erx_token_label),
    requirement = FieldRequirement.Optional,   // one of the few exceptions, so it is marked
)
```

Requirement is only *communicated* here. It is *enforced* by each screen's `state.canSave`, which
disables Save until the required fields are filled; `AppTextField` also accepts an `errorText` that
flips the field to its error style and sets the error semantics for TalkBack when a screen wants to
call out an invalid field inline.

---

## Interaction Diagrams

### Use case 1 — Recording a dispensation from the Scripts screen

The user taps the "dispensed" area of a script card and confirms a pharmacy fill. The dispensation is
persisted and the in-hand quantity for that dispensable unit is incremented in one transaction. A
script allows `repeats + 1` fills, so on the final fill the script's lifecycle is over and the script
row is deleted (its dispensations cascade). The list then refreshes reactively from Room.

```mermaid
sequenceDiagram
    actor User
    participant ScriptsScreen
    participant ScriptsViewModel
    participant DispensationRepository
    participant InHandRepository
    participant ScriptRepository
    participant Room

    User->>ScriptsScreen: Taps the "dispensed" area of a script card
    ScriptsScreen->>ScriptsViewModel: onAction(DispensedTapped(scriptId))
    alt dispensed > repeats (nothing left to fill)
        ScriptsViewModel-->>ScriptsScreen: state.maxedOutScriptId = scriptId
        ScriptsScreen-->>User: "Already at maximum" dialog (DismissMaxedOut)
    else fills remain
        ScriptsViewModel-->>ScriptsScreen: state.pendingDispenseScriptId = scriptId
        ScriptsScreen-->>User: Confirmation dialog
        User->>ScriptsScreen: Confirms
        ScriptsScreen->>ScriptsViewModel: onAction(ConfirmDispense)
        ScriptsViewModel->>DispensationRepository: add(Dispensation(scriptId, dispensableUnitId, number = 1, now))
        DispensationRepository->>Room: INSERT INTO dispensations …
        ScriptsViewModel->>InHandRepository: addTablets(dispensableUnitId, 1 × tabletsPerUnit)
        InHandRepository->>Room: @Transaction UPDATE in_hand SET quantity = quantity + ? …<br/>(INSERT if no row yet)
        opt this was the final fill (dispensed == repeats)
            ScriptsViewModel->>ScriptRepository: deleteById(scriptId)
            ScriptRepository->>Room: DELETE FROM scripts … (dispensations cascade)
        end
        Room-->>ScriptsViewModel: scriptsWithDetails Flow re-emits
        ScriptsViewModel-->>ScriptsScreen: emits updated ScriptsState (dialog cleared)
        ScriptsScreen-->>User: Dispensed count increments (or the finished script disappears)
    end
```

---

### Use case 2 — Camera tablet count feeding the In Hand screen

The user photographs loose tablets; the app auto-counts them, lets the user tune the sensitivity and
correct individual markers, then returns the integer to the In Hand screen via the back-stack result.

```mermaid
sequenceDiagram
    actor User
    participant InHandScreen
    participant AppNavHost
    participant CountTabletsScreen
    participant CountTabletsViewModel
    participant PeakTabletCounter

    User->>InHandScreen: Picks a dispensable unit, taps the camera icon → "Count tablets"
    InHandScreen->>AppNavHost: onCountTablets()
    AppNavHost->>CountTabletsScreen: navigate(CountTabletsRoute)

    CountTabletsScreen-->>User: Camera permission gate, then live preview
    User->>CountTabletsScreen: Taps Capture
    CountTabletsScreen->>CountTabletsScreen: ImageStore saves the JPEG, then decodes a downscaled CountImage
    CountTabletsScreen->>CountTabletsViewModel: analyse(path, image)
    CountTabletsViewModel->>PeakTabletCounter: analyse(image) on Dispatchers.Default
    PeakTabletCounter->>PeakTabletCounter: foregroundMask → distance transform → local maxima
    PeakTabletCounter-->>CountTabletsViewModel: PeakField (peaks + median height)
    CountTabletsViewModel->>CountTabletsViewModel: PeakField.select(sensitivity) → List<CountPoint>
    CountTabletsViewModel-->>CountTabletsScreen: phase = DETECT, markers, count = 24

    User->>CountTabletsScreen: Drags the sensitivity slider
    CountTabletsScreen->>CountTabletsViewModel: setSensitivity(value)
    CountTabletsViewModel->>CountTabletsViewModel: re-select() only — no re-analyse
    CountTabletsViewModel-->>CountTabletsScreen: updated markers / count

    opt Cluttered surround
        User->>CountTabletsScreen: "Crop" → drags a box → "Apply"
        CountTabletsScreen->>CountTabletsViewModel: beginCrop() / applyCrop(rect)
        CountTabletsViewModel->>PeakTabletCounter: analyse(image.cropped(rect))
    end

    User->>CountTabletsScreen: Taps "Next"
    CountTabletsScreen->>CountTabletsViewModel: confirmDetection() → phase = EDIT
    User->>CountTabletsScreen: Taps a missed tablet
    CountTabletsScreen->>CountTabletsViewModel: onTapAt(x, y)
    CountTabletsViewModel->>CountTabletsViewModel: editMarkers(...) adds/removes one marker
    CountTabletsViewModel-->>CountTabletsScreen: count = 25

    User->>CountTabletsScreen: Taps "Use 25"
    CountTabletsScreen->>AppNavHost: onCounted(25)
    AppNavHost->>AppNavHost: previousBackStackEntry.savedStateHandle[TABLET_COUNT_RESULT] = 25<br/>popBackStack()
    AppNavHost->>InHandScreen: countedTablets = 25 (collected from the In Hand entry's saved state)
    InHandScreen->>InHandScreen: onAction(TabletsCounted(25)) → announceForAccessibility → onCountedConsumed()
    InHandScreen-->>User: "Number of tablets" field populated with 25
```

---

## Testing

Test sources mirror the production package layout, so a class's tests sit in the same package as the
class itself.

| Source set | What lives there | Stack |
|---|---|---|
| `app/src/test/…` | JVM unit tests: every ViewModel, the pure counting/CV engine, `PbsScriptParser`, `FuzzyMatcher`, `InventorySupply`, `AttentionMessages`, `RunOutGraph`, `BackupFileName`, the numeric sanitizers, state-derivation (`*StateTest`) | JUnit 5 (Jupiter), Turbine, AssertK, Mockito, `UnconfinedTestDispatcher` |
| `app/src/androidTest/…` | Compose UI tests, one `*ScreenTest` + `*Robot` pair per screen | `createAndroidComposeRule<ComponentActivity>`, `ui-test-junit4`, Espresso |

### Running

```zsh
# JVM unit tests (Gradle is configured with useJUnitPlatform())
./gradlew testDebugUnitTest

# …plus a JaCoCo coverage report → app/build/reports/jacoco/jacocoTestReport/html/index.html
./gradlew jacocoTestReport

# Compose UI tests — needs a connected device or running emulator
./gradlew connectedDebugAndroidTest
```

HTML results land in `app/build/reports/tests/testDebugUnitTest/index.html` (unit) and
`app/build/reports/androidTests/connected/debug/index.html` (instrumented).

### Conventions

- **ViewModel tests** set `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@BeforeEach`, reset it
  in `@AfterEach`, and assert on state with Turbine (`vm.state.test { awaitItem() … }`). Dependencies
  are hand-rolled fakes implementing the DAO/repository interface, or Mockito mocks where a single
  interaction is being verified — no DI framework is involved, since ViewModels take constructor
  parameters.
- **UI tests** use the Robot pattern: the robot calls `setContent { … }` on the *stateless* `FooScreen`
  composable with a hand-built `FooState`, exposes `assert*` / `perform*` methods, and looks nodes up
  by string resource or `testTag` — so the tests never depend on a real database or ViewModel.
- JUnit 4 tests still run alongside JUnit 5 via the Vintage engine (`junit-vintage-engine`).

---

## Building & Running

### Prerequisites

- Android Studio recent enough for AGP 9.2 (the project targets Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.4.0)
- JDK 17+ to run Gradle (AGP 9 requires it); the modules themselves compile at Java/Kotlin language
  level 11 (`sourceCompatibility`/`targetCompatibility = VERSION_11`)
- Android SDK — compile SDK 37, build-tools matching

### Clone and open

```zsh
git clone git@github.com:replicant1/PxTx.git
cd PxTx
# Open in Android Studio: File → Open → select the PxTx folder
```

### Build and install (command line)

```zsh
# Debug build
./gradlew installDebug

# Release build (unsigned; minification is off)
./gradlew assembleRelease
```

### Debug seed data

In a debug build, `DatabaseSeeder` auto-populates an empty database on first launch with a moderate
set of sample medications, dispensable units, scripts, dispensations, a daily schedule, and in-hand
quantities — so the app can be exercised immediately without manual data entry.

### Backup file format

Backups are written to the device's Downloads folder, named `pxtx-<ddMMyyyy>-db<schema>.zip` by
default (e.g. `pxtx-30072026-db27.zip`) — the Save dialog lets the user edit the name, and a missing
`.zip` extension is added for them. They can be transferred between devices: on load the app reads the
manifest's schema version first, **rejects** a backup newer than the running app, and otherwise lets
Room migrate an older database on the next open (see `MainViewModel`). Note that
`SharedPreferences` settings (the warning window) are **not** in the backup — only `aitoui.db` and the
tablet photos.

---

## Related Docs

| Document | Description |
|---|---|
| [`docs/DATABASE_SCHEMA.md`](docs/DATABASE_SCHEMA.md) | Full table definitions, column types, constraints, relationships, migration history (current: v27) |
| [`docs/database-schema.png`](docs/database-schema.png) | UML class diagram of the current schema — regenerate with `mmdc` from the Mermaid source in the schema doc after any schema change |
| [`docs/tablet-counting-mvp.md`](docs/tablet-counting-mvp.md) | Original design record for the loose-tablet camera counter: user flow, counting engine interface, wiring into In Hand. Its status banner lists where the shipped code diverged (`CountImage`/`CountPoint`, `PeakTabletCounter`, sensitivity slider, crop phase) |
| [`docs/blister-counting-mvp.md`](docs/blister-counting-mvp.md) | Original design record for the blister-pack camera counter: geometry-only approach, PCA segmentation, pop-the-empties UX. Its status banner notes the shipped phase machine, which adds the hand-adjustable framing step |
| [`docs/ACCESSIBILITY_AUDIT.md`](docs/ACCESSIBILITY_AUDIT.md) | Accessibility review findings and remediation status |
| [`docs/mockups/`](docs/mockups/) | Static HTML mockups used to agree a screen change before implementing it |
| [`docs/screenshots/`](docs/screenshots/) | Device screenshots used by the [Walkthrough](#walkthrough), plus the redacted PB038 form |

