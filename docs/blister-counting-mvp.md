# Blister-Pack Counting (MVP) — Implementation Plan

## Goal & scope

Add a camera-based way to count the tablets still held in **blister packs** and
feed the total into the existing **"Number of tablets"** field on the In Hand
screen — the same hand-off as the loose-tablet counter. The user photographs one
or more packs, confirms each pack's grid layout, **pops the empty pockets**, and
the remaining count returns to the field. They then tap **ADD** → **SAVE** as
today.

The defining decision: the app does **geometry only** — segment each pack, square
it up, lay a grid the user confirms — and **never classifies a pocket as full or
empty**. Every pocket **defaults to full**; the user taps the gone ones. This is
grounded in measurement, not preference (see *What we validated*).

**In scope (MVP):**

- Camera entry to a **Count from packs** screen (from the "Number of tablets"
  field).
- Multi-pack in one photo; each pack at any angle; **foil-up, dome-up, or a mix**
  — the geometry is side-agnostic.
- Per-pack **layout confirmation** (columns × rows, e.g. 2×5).
- **Assume-full default + pop-the-empties** correction, with a pop **sound** and
  a **haptic** tick.
- Sum full pockets across all packs → integer returns to the field.
- Pure-Kotlin, on-device (no OpenCV / ML Kit / network), reusing the
  loose-tablet counter's camera + result plumbing.

**Out of scope (deferred):**

- Any automatic full/empty classification (proven unreliable — see appendix).
- Perspective/keystone correction for packs shot at a steep tilt.
- Automatic grid detection (columns × rows inferred from the image).
- Auto-detecting which side (foil/dome) a pack shows, for a hint.
- Pulling the known layout from the medication's dispensable unit to skip
  confirmation.
- Non-rectangular / irregular packs.

## Why this shape (design decisions)

- **Geometry only, no classification.** Segmentation + orientation + grid are
  reliable in pure Kotlin; full/empty is not (the signals overlap across packs and
  lighting). So the machine does the reliable part and hands the judgement to the
  person.
- **Assume every pocket is full.** Scanning happens right after dispensing, when
  packs are full or nearly so, so the default is correct the vast majority of the
  time and a fresh pack needs **zero taps**.
- **Foil-up preferred, any side accepted.** On the foil side an empty pocket is an
  obvious torn hole — easiest for a *person* to read. But because the app never
  reads pockets, dome-up and mixed photos work identically; only the human's
  reading difficulty differs.
- **Packs must not touch or overlap.** Segmentation separates packs by each being
  its own bright island on the dark surface; overlap merges them into one blob.
  The capture instruction states this.

## User flow

1. On **In Hand**, pick a medication (as today) → `selectedMedicationId` set.
2. Tap the camera icon on the "Number of tablets" field → a small chooser:
   **Loose tablets** (existing) / **Blister packs** (new).
3. Navigate to **Count from packs** (passed the `medicationId`). Camera-permission
   gate reuses the loose-tablet pattern.
4. Live preview → instruction: *lay packs on a plain surface, not touching, foil-
   or dome-up* → **capture** → the frame freezes and is decoded upright.
5. The app **segments** the packs and, for **each pack**, shows it squared with a
   grid overlay → the user nudges **columns × rows** until it snaps → **Confirm**.
6. **Pop screen** (per pack): every pocket starts **full**. Tapping a pocket
   **pops** it (→ empty) with a pop sound + haptic; the **Full** count ticks down.
   Tap again to un-pop. **Pinch to zoom** for dense packs.
7. **Summary:** full pockets summed across all packs → **Use N**.
8. Back on In Hand, `N` populates "Number of tablets"; the user taps **ADD** then
   **SAVE** (unchanged pipeline).

## Navigation & screens (mirrors the loose-tablet counter)

- New type-safe route `BlisterCountRoute(medicationId: Long)` in
  `navigation/Routes.kt`; wired in `AppNavHost.kt` with an `onCounted: (Int) -> Unit`
  that writes the existing savedStateHandle result key (reuse `TABLET_COUNT_RESULT`
  — both features return an int to the same field) and pops back.
- New `BlisterCountRoot` / `BlisterCountScreen` + `BlisterCountViewModel` under a
  new `inhand/blister/` package, reusing helpers from `inhand/count/`:
  camera preview + `ImageCapture`, `ImageStore.decodeUpright` /
  `newCaptureFile`, the **capture-path-in-VM** pattern (review survives rotation),
  pinch-to-zoom with inverse tap mapping, `safeDrawingPadding`, and the debug
  "Use test image" loader for on-device iteration.
- The In Hand camera `IconButton` gains the chooser (or a second icon). The
  `onCounted` path dispatches the existing `InHandAction.TabletsCounted(count)`.

## Screen internals & state

`BlisterCountViewModel` phase machine:

`RequestingPermission → Previewing → Captured(path) → Segmenting → ConfirmLayout(packIndex) → Popping(packIndex) → Summary → (returns total)`

State (all view-relevant, testable):

- `capturePath: String?` — retained frame (rotation-safe), as in the loose counter.
- `packs: List<PackState>` where `PackState`:
  - `geometry: PackRegion` — centroid, unit axes `u`/`v` (long/short), extents.
  - `cols: Int`, `rows: Int` — user-confirmed (default 2×5).
  - `popped: BooleanArray` (size `cols*rows`, all `false` = all full).
  - `fullCount get() = cols*rows - popped.count { it }`.
- `phase`, `currentPackIndex`.
- `total get() = packs.sumOf { it.fullCount }`.

Tapping on the displayed image → map screen→image px (reuse the pinch/zoom inverse
transform) → `PackGrid.tapToCell` → toggle that cell's `popped`, firing pop
feedback only on the full→empty transition.

## Geometry engine (pure & JVM-testable)

Two new dependency-free files in `com.example.aitoui.counting`, built on the
existing `Segmentation.kt` (`toGrayscale`, `otsuThreshold`, `foregroundMask`):

- **`PackSegmentation.kt`** — `segmentPacks(image: CountImage): List<PackRegion>`
  1. `foregroundMask` (bright pack vs dark surface).
  2. 8-connected components (same flood-fill pattern as `BlobTabletCounter`),
     keeping blobs above an area fraction (packs are large; drops speckle).
  3. Per blob: centroid + covariance → **PCA** principal axis (`u` = long side,
     `v` = short side); project pixels to get extents `uMin/uMax/vMin/vMax`.
  → `PackRegion(cx, cy, ux, uy, vx, vy, uMin, uMax, vMin, vMax)`.

- **`PackGrid.kt`** — pure grid math over a `PackRegion` + `(cols, rows)` +
  inset margins:
  - `cellCenter(region, cols, rows, r, c): CountPoint` (image px).
  - `tapToCell(region, cols, rows, x, y): CellRef?` (inverse of the above; null
    outside the pocket area).
  - `pocketCount(cols, rows)`.

Both are the exact stages the offline prototype already validated (markers landed
on pockets); this promotes that throwaway code into tested production functions.

## Pop feedback (sound + haptic)

- **Sound:** `SoundPool` with a short, dry pop clip (low latency, good for rapid
  repeats — unlike `MediaPlayer`). Play only on full→empty; un-pop is silent (or a
  soft reverse). Respect the ringer/silent mode.
- **Haptic:** a short `VibrationEffect` (`EFFECT_TICK`, or a brief one-shot) via
  `Vibrator`/`VibratorManager`, or `View.performHapticFeedback`. Carries the
  feedback when the phone is muted; respects system haptic settings.
- Encapsulate in `inhand/blister/PopFeedback.kt` so the composable just calls
  `pop()` / `unpop()`.

## Wiring into In Hand (the hook)

- Reuse `InHandAction.TabletsCounted(count)` → sets `numberOfTablets`. No new
  action or data-model change; blister count is just another source of the int.
- Everything downstream — `canAdd` / `Add` / `Save` — unchanged. Feeds the same
  additive **ADD** path as the loose counter (hook #2 "recount to true total"
  remains deferred).

## Dependencies

- **CameraX** — already in the project and used by the loose-tablet counter.
- Pure Kotlin for all image work (no OpenCV / ML Kit). No network.
- `SoundPool` / `Vibrator` — platform APIs, no new libs. One small audio asset.

## Testing

- **Unit — `PackSegmentation`:** synthetic images of 2–3 bright rectangles (varied
  angles) on a dark ground → correct pack count, centroids, and orientation.
- **Unit — `PackGrid`:** `cellCenter` positions for a known region; `tapToCell`
  round-trips (tap at a computed cell center returns that cell); taps outside the
  pocket area return null.
- **Unit — `BlisterCountViewModel`:** pop/un-pop toggles the right cell;
  `fullCount` and `total` math; default is all-full.
- **Unit — `InHandViewModel`:** `TabletsCounted(n)` sets `numberOfTablets`, flips
  `canAdd` (already covered).
- **Device:** the sample photos (`3-blister-packs*`, `2-blister-packs*`, foil &
  dome) via the debug loader — verify segmentation + grid alignment, the pop
  sound/haptic, pinch-zoom tap accuracy, and `Use N` → field. Tune margins on a
  flat capture; note tilt degrades alignment.

## MVP vs deferred

| Capability | MVP | Later |
|---|---|---|
| Camera entry → Count from packs | ✅ | |
| Multi-pack, any angle, foil/dome/mixed | ✅ | |
| Segment → square → grid (pure Kotlin) | ✅ | |
| User-confirmed columns × rows | ✅ | |
| Assume-full + pop-the-empties | ✅ | |
| Pop sound + haptic | ✅ | |
| Sum across packs → ADD → SAVE | ✅ | |
| Automatic full/empty classification | | ✗ proven unreliable |
| Auto grid detection (infer cols × rows) | | ▶️ |
| Perspective correction for tilted packs | | ▶️ |
| Side auto-detect → reading hint | | ▶️ |
| Layout from the dispensable unit | | ▶️ |

## Risks / open questions

- **Perspective / tilt.** The uniform grid assumes a roughly flat pack; steep
  angles drift the grid (seen in the prototype). Mitigation: capture guidance;
  the user can nudge cols/rows and pinch-zoom; corners/homography is a later step.
- **Label-margin packs.** Pockets may not fill the full pack extent (foil packs
  have printed strips). Global inset margins + user confirm covers the common
  case; per-product margins are a later refinement.
- **Forgotten pops → over-count.** Mild and rare right after dispensing; the
  caved-in holes make omissions visually obvious. Optional "mark the rest empty"
  for the uncommon mostly-used pack.
- **Glary foil.** A strong dark reflection could nip a pack's outline during
  segmentation — per-pack robustness, unrelated to side/mix.
- **Entry point.** Loose-tablet vs blister chooser on one camera icon vs two
  icons — decide during build.

## Suggested order of work

1. **Pure geometry first:** `PackSegmentation` + `PackGrid` + unit tests
   (promote the validated prototype logic).
2. `BlisterCountViewModel` + state (multi-pack, phases, popped grids) + tests.
3. Screens: reuse capture/permission; add **confirm-layout** and **pop** screens
   + **summary**; reuse pinch-zoom + inverse tap mapping.
4. `PopFeedback` (SoundPool + haptic).
5. Route + In Hand chooser + `Use N` hand-off.
6. On-device validation with the sample photos; tune margins/area thresholds.
7. Tests green, cleanup, PR.

## Appendix — what we validated (offline prototypes)

Two throwaway JVM prototypes over the real sample photos settled the design:

- **Geometry works.** Segment → PCA-rectify → 2×5 grid placed markers on the
  pockets reliably on flat packs (both sides); alignment degrades under tilt.
- **Classification does not.** Forcing full/empty per pocket failed: on dome-up,
  a global threshold gave 10/10/8 and per-pack Otsu gave 10/4/5 against a
  ground truth of **10/3/8** — the features (brightness, variance, edge) overlap
  across packs (an embossed full pocket looks like a crumpled empty elsewhere).
- **Foil-up auto-default doesn't pay.** A conservative 3-way (full / empty /
  defer) rule on the foil photos **deferred 70%** (9 of 30 pockets auto-resolved;
  one pack deferred entirely) because printing destroys the texture cue and only
  a weak dark-cavity cue survives.

Hence: geometry by machine, full/empty by the person, default-full to make that
trivial. A UX walkthrough of the flow accompanies this plan
(published Artifact; "pop the empties").
