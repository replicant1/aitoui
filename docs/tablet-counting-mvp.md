# Tablet Counting (MVP) — Hook #1 Implementation Plan

## Goal & scope

Add a camera-based tablet count that feeds the existing **"Number of tablets"**
field on the In Hand screen. Count a single layer of loose tablets on a plain
surface, let the user correct the result, and return an integer into the field.
The user then taps **ADD** → **SAVE** exactly as today.

**In scope (MVP):**

- Camera entry point on the "Number of tablets" field.
- Freeze a still → generic (medication-agnostic) auto-count → tap-to-add/remove
  correction → confirm → number returns to the field.
- Reference-*aware* pipeline that *ignores* the reference for now.
- On-device only (the app has no internet permission).

**Out of scope (deferred):**

- Consuming the reference image to improve counting.
- Algorithmic de-clumping / split gestures.
- Blister-pack counting.
- Per-row "recount that sets the total" (hook #2).
- Persisting the captured/annotated image.
- Capturing a reference crop as a side effect (nice-to-have; optional).

## User flow

1. On **In Hand**, the user picks a medication in the dropdown (as today) →
   `selectedMedicationId` set.
2. The user taps a **camera icon** on the "Number of tablets" field.
3. Navigate to a new **Count Tablets** screen (passed the `medicationId`).
4. Camera permission gate (reuse the ScanScript pattern: *Grant permission* / a
   manual fallback that just backs out).
5. Live preview → user frames the spread-out tablets → taps **capture** → the
   frame freezes.
6. Auto-count runs; markers overlay each detected tablet; a live count shows
   (e.g. "24 tablets").
7. The user corrects: **tap empty space to add a marker**, **tap a marker to
   remove it**. Count = number of markers.
8. The user taps **Use count** → the screen returns the integer and pops back.
9. Back on In Hand, the number populates "Number of tablets"; the user taps
   **ADD** then **SAVE** (unchanged pipeline).

## Navigation & screens (mirrors ScanScript)

- New type-safe route `CountTabletsRoute(medicationId: Long)` in
  `navigation/Routes.kt`; wired in `AppNavHost.kt` with an
  `onCounted: (Int) -> Unit` callback that pops back (same shape as
  `ScanScriptRoute` → `onScanned`).
- New `CountTabletsRoot` / `CountTabletsScreen` + `CountTabletsViewModel` under a
  new `inhand/count/` (or `counting/`) package.
- The In Hand screen gets an `onCountTablets: () -> Unit` (or navigates directly)
  that launches the route with the currently selected medication id; the route's
  `onCounted` dispatches a new In Hand action.

## Count screen internals

State machine in `CountTabletsViewModel`:

`RequestingPermission → Previewing → Captured(bitmap) → Analyzing → Review(markers) → (returns count)`

- **Capture:** CameraX `ImageCapture` to a still bitmap (in-memory, ephemeral).
- **Overlay/correction:** a `Canvas`/`Box` over the frozen image drawing a marker
  per point; `pointerInput` for tap-to-add (hit-test empty space) and
  tap-to-remove (hit-test near a marker). Markers stored as a `List<Offset>` in
  normalized image coordinates so they survive rotation/scaling.
- **Result:** `markers.size` → returned via the nav callback.

## Counting engine (pure & swappable)

- An interface, e.g.
  `TabletCounter.count(bitmap, reference: ReferenceImage? = null): List<PointF>`
  — **reference param present but ignored in MVP** (reference-aware,
  reference-ignoring).
- MVP implementation: classical CV. Options — **OpenCV Android SDK**
  (threshold → contours/connected components → watershed for light declumping →
  centroids) or **ML Kit** subject/object detection. Lean OpenCV for control and
  no per-object model.
- Kept behind the interface so a learned model or reference-consuming version
  drops in later with no caller changes.
- Pure / JVM-testable on fixed sample images.

## Wiring into In Hand (the actual hook)

- **UI:** add a camera `IconButton` as the trailing icon of the "Number of
  tablets" `OutlinedTextField` (`InHandScreen.kt:170`), enabled only when a
  medication is selected.
- **New action:** `InHandAction.TabletsCounted(count: Int)` →
  `_state.update { it.copy(numberOfTablets = count.toString()) }`. (Or reuse
  `NumberOfTabletsChanged`.)
- **Everything downstream is unchanged:** `canAdd` / `Add` (appends a row) /
  `Save` (`replaceAll` + date stamp). **No data-model or persistence change.**
- Known semantic to note: hook #1 feeds the **additive ADD** path — "count this
  batch and add it." "Recount my whole holding to the true total" is hook #2
  (per-row `SetQuantity`), deliberately deferred.

## Dependencies

- **CameraX** (preview + still capture).
- **OpenCV Android SDK** (or ML Kit) for the count.
- No network; models/libs bundled.

## Testing

- **Unit:** `TabletCounter` against a handful of sample photos with known counts
  (assert within tolerance) — documents real accuracy.
- **Unit:** `CountTabletsViewModel` — add/remove marker math, count = marker
  count.
- **Unit:** `InHandViewModel` — `TabletsCounted(n)` sets `numberOfTablets` and
  flips `canAdd`.
- **Compose UI:** the correction overlay (tap adds/removes; count updates); the
  permission gate.

## MVP vs deferred

| Capability | MVP | Later |
|---|---|---|
| Camera icon on "Number of tablets" field | ✅ | |
| Freeze still → generic auto-count | ✅ | |
| Tap-to-add / tap-to-remove correction | ✅ | |
| Return int → ADD → SAVE (no data change) | ✅ | |
| Reference-aware interface (ignored) | ✅ | consume it |
| Colour/size prefilter from reference | | ▶️ |
| Algorithmic de-clumping / split gesture | | ▶️ |
| Per-row recount (set total, hook #2) | | ▶️ |
| Blister-pack counting | | ▶️ |
| Save annotated/reference image | | ▶️ |

## Risks / open questions

- **Accuracy** on shiny/coated tablets, shadows, glare — mitigated by UX guidance
  + correction; validate with real sample photos early.
- **CameraX + OpenCV** integration effort and APK size (OpenCV is sizeable) —
  evaluate ML Kit as a lighter alternative during a spike.
- **Fractional quantities:** In Hand stores `Double`; a count is always whole, so
  we set an integer string — fine, just noted.
- **Duplicate rows:** ADD appends, so counting a medication that already has a row
  creates a second row (existing behavior). Acceptable for MVP; hook #2 addresses
  it properly.

## Suggested order of work

1. Spike the counter (`TabletCounter`) on sample images; pick OpenCV vs ML Kit
   based on measured accuracy.
2. Build the Count Tablets screen (CameraX capture + correction overlay),
   returning a hard-coded/real count.
3. Wire the route + In Hand camera icon + `TabletsCounted` action.
4. Tests + on-device validation on the Pixel 3.
