# Accessibility Audit

A screen-by-screen accessibility review of the PxTx (`com.example.aitoui`) app, focused on
**TalkBack / screen-reader** support and WCAG-for-mobile basics. Each finding is a checkbox so this
doubles as a **living remediation checklist** — tick items as they're fixed.

- **Scope:** all 15 screen composables under `app/src/main/java`.
- **Audited:** 2026-07-16.
- **Method:** static review of each screen against the 10-point checklist below (no live TalkBack pass yet —
  see *Follow-up*).

## Checklist categories

1. **Content descriptions** — every `Icon`/`Image`/`IconButton` has a meaningful `contentDescription`, or
   `null` when purely decorative; interactive controls are named.
2. **Text-field labels** — every `TextField`/`OutlinedTextField` has a visible label.
3. **Heading semantics** — screen title and section headers use `Modifier.heading()`.
4. **State & selection** — toggles/selected/expanded items expose their state to screen readers.
5. **Touch-target size** — tappable elements are ≥ 48dp.
6. **Colour & contrast** — information isn't conveyed by colour alone; text meets contrast minimums.
7. **Custom-drawn content & gesture-only actions** — `Canvas`/graph/overlay content has a text alternative
   or semantics; gesture-only actions have an accessible fallback.
8. **Focus & reading order** — logical order; related row/card elements grouped (`mergeDescendants`).
9. **Dynamic type** — text uses `sp`; no fixed heights/widths that clip scaled text.
10. **Live announcements** — important dynamic changes (counts, errors, results, progress) use a `liveRegion`.

Severity: **High** (blocks a screen-reader user from a core task), **Med** (significant friction/ambiguity),
**Low** (polish).

## Already done (context)

Prior accessibility work, merged: run-out graph line styling for colour-blindness (#29), list-row selection
state exposed to screen readers (#57), and headings on screen titles/section headers (#59). The shared
helpers are `ui/Heading.kt` (`Modifier.heading()`) and `ui/SelectableRow.kt` (`Role` + `selected`).

## Cross-cutting

- **Gesture-only + Canvas-invisible interactions (the biggest gap).** The camera/graph screens
  (`RunOutGraph`, `CameraCapture`, `CountTablets`, `BlisterCount`) build their core interactions from raw
  `pointerInput` gestures (tap-to-focus, pinch-to-zoom, tap-to-add/remove marker, tap-to-pop, drag-the-crop,
  scrub-the-time-cursor) drawn on a `Canvas` with **no semantics**. A TalkBack user cannot perceive the drawn
  state or perform the action at all. These are the highest-value fixes and share one solution shape: expose
  drawn items as semantics nodes with **custom actions**, and/or add non-gesture control fallbacks.
- **Hardcoded strings app-wide.** Every user-facing string is an inline literal, not a `stringResource`.
  This doesn't break TalkBack (the literals are read aloud) but blocks localization/translated output. Noted
  once here; not repeated per screen.
- **Unlabelled shutter buttons.** ~~All four camera screens use an `IconButton` whose child is a decorative
  `Box`, with no `contentDescription` → TalkBack says only "button".~~ **Fixed 2026-07-16** — labelled
  "Take photo" (capture/count/blister) and "Capture form" (scan).
- **Un-announced live counts.** The counters (`CountTablets` "N tablets", `BlisterCount` "Full X · Empty Y",
  graph "Remaining at cursor") update silently. Add `liveRegion = LiveRegionMode.Polite`.

## Summary

| # | Screen | High | Med | Low |
|---|---|:--:|:--:|:--:|
| 1 | MainScreen | 0 | 0 | 2 |
| 2 | MedicationsScreen | 0 | 1 | 1 |
| 3 | MedicationScreen | 0 | 0 | 0 |
| 4 | ScriptsScreen | 1 | 1 | 3 |
| 5 | DispensableUnitsScreen | 0 | 1 | 2 |
| 6 | DispensableUnitScreen | 0 | 0 | 0 |
| 7 | DailyScheduleScreen | 0 | 0 | 2 |
| 8 | InHandScreen | 0 | 0 | 2 |
| 9 | InventoryScreen | 0 | 2 | 1 |
| 10 | RunOutGraphScreen | 2 | 1 | 2 |
| 11 | ScanScriptScreen | 1 | 2 | 2 |
| 12 | AddScriptScreen | 0 | 1 | 1 |
| 13 | CameraCaptureScreen | 2 | 4 | 1 |
| 14 | CountTabletsScreen | 3 | 2 | 1 |
| 15 | BlisterCountScreen | 4 | 1 | 1 |
| | **Total** | **13** | **16** | **20** |

---

## Per-screen findings

### 1. MainScreen — `MainScreen.kt`
Largely accessible: title is a heading, buttons labelled, decorative icons correctly nulled.
- [ ] **[Low]** Live announcements: the "Working…" busy dialog (`MainScreen.kt:202`) is a bare
  `CircularProgressIndicator` with no text/`stateDescription` → add a `contentDescription`/`liveRegion`
  ("Working, please wait").
- [ ] **[Low]** Reading order: the 2×4 grid zips two column lists into rows (`MainScreen.kt:178`), read
  zig-zag by TalkBack. Matches the visual rows, so acceptable — optionally group with headers if the columns
  are meaningful.

### 2. MedicationsScreen — `medication/MedicationsScreen.kt`
Good baseline; the per-row delete control is ambiguous.
- [x] **[Med]** Content descriptions: every row's delete button reads the identical "Delete medication"
  (`MedicationsScreen.kt:161`) → `"Delete ${medication.brandName}"`.
  **Done 2026-07-16** — the delete icon now reads "Delete {brandName}" per row.
- [ ] **[Low]** Reading order: `MedicationRow` (`:139`) exposes brand + active ingredient as two stops →
  `Modifier.semantics(mergeDescendants = true)` on the info column (keep delete separate).

### 3. MedicationScreen — `medication/MedicationScreen.kt`
**No significant issues.** Title is a heading; back and Save are labelled; both fields have labels; disabled
Save is exposed via `enabled`.

### 4. ScriptsScreen — `script/ScriptsScreen.kt`
The tappable "Dispensed" cell is not discoverable as an action; delete buttons ambiguous.
- [x] **[High]** State/content: the "Dispensed" cell is a bare `Modifier.clickable` (`ScriptsScreen.kt:291`,
  `ScriptCardCell` ~`:338`) with no `Role.Button`, `onClickLabel`, or description — reads as two fragments
  ("Dispensed", "2"), visually identical to the non-interactive "Repeats" cell → add
  `role = Role.Button` + `onClickLabel = "Dispense one unit"`, merge the cell, and give it a visual
  affordance distinguishing it from Repeats.
  **Done 2026-07-16** — a "+" `IconButton` (labelled "Dispense one unit") in the Dispensed cell's top-right
  corner, mirroring the card's delete cross, is now the dispense control; caption+value read as one node.
  Both the screen-reader and visual-affordance parts are addressed.
- [x] **[Med]** Content descriptions: delete button reads identical "Delete script" (`:277`) →
  `"Delete script for ${script.brandName}"`.
  **Done 2026-07-16** — reads `"Delete ${script.brandName}"` per card, matching the Medications delete label.
- [x] **[Low]** Reading order: `ScriptCard` (`:242`) emits many stops with delete interleaved → merge the
  header column (`:255`) so brand + dosage read together.
  **Done 2026-07-16** — the header `Column` is now `semantics(mergeDescendants = true)`, so brand + dosage
  read as one stop; the delete cross stays a separate sibling node.
- [x] **[Low]** Contrast: dosage uses `ScriptCardInk.copy(alpha = 0.6f)` on the yellow band (`:271`); verify
  it meets AA 4.5:1.
  **Done 2026-07-16 (no change needed)** — verified: black @ 0.6 alpha composites to `#66602F` on the band
  `#FFF176`, a contrast ratio of **5.52:1** — passes AA 4.5:1 (and it's `bodyLarge`). Left as is.
- [x] **[Low]** State: sort `DropdownMenuItem`s (`:120`) show the current choice only via a check icon → set
  `semantics { selected = (order == state.sortOrder) }` on the current item.
  **Done 2026-07-16** — the current item now carries `selected` state so TalkBack reads e.g.
  "Brand name, selected". The flag is placed on the item's label `Text` (a descendant of the item's
  clickable node) rather than on the `DropdownMenuItem` `modifier`: the `modifier` lands on a wrapper
  *above* the click target, and semantics merge only from descendants upward, so a wrapper-placed flag
  would never reach the spoken node. The check icon is now decorative (`contentDescription = null`) so the
  selection isn't announced twice. (`selected`, like `stateDescription`, is TalkBack-audible only and does
  not surface in `uiautomator dump` — verified across three placements plus AddScript's radio cards.)

### 5. DispensableUnitsScreen — `dispensableunit/DispensableUnitsScreen.kt`
Icons/photos labelled and title is a heading, but a core photo gesture is screen-reader-invisible. (The
recently-tuned 56dp thumbnail row height is **not** a clipping risk — the text column is `weight(1f)` with no
fixed height, so the row grows with scaled text.)
- [x] **[Med]** Gesture-only: the photo's `onLongClick = onViewFullImage` (`:290`) is long-press only, which
  TalkBack can't perform — yet the hint says "Long-press a photo to view it full-size" (`:116`) → expose a
  `CustomAccessibilityAction("View full-size photo", …)`.
  **Done 2026-07-16** — the photo now carries a `CustomAccessibilityAction("View full-size photo")` in
  TalkBack's actions menu; the sighted long-press is unchanged.
- [x] **[Low]** Content descriptions: the photo's primary tap opens the retake/remove dialog but its
  description is only "Tablet photo for {brand}" (`:285`) → add an `onClick` semantics label ("Manage photo").
  **Done 2026-07-16** — `combinedClickable(onClickLabel = "Manage photo", …)`, so TalkBack announces
  "double-tap to Manage photo" on the "Tablet photo for {brand}" node.
- [x] **[Low]** Reading order: merge the three-`Text` column (`:234`) so brand/ingredient/dose read as one.
  **Done 2026-07-16** — the text `Column` is now `semantics(mergeDescendants = true)`.

### 6. DispensableUnitScreen — `dispensableunit/DispensableUnitScreen.kt`
**No significant issues.** Title heading; back labelled; Save exposes disabled state; all three fields have
labels; empty-state uses `supportingText`.

### 7. DailyScheduleScreen — `dailyschedule/DailyScheduleScreen.kt`
Strong state semantics — rows use `selectableRow` (Role + selected, so selection isn't colour-alone) and the
total is a heading.
- [ ] **[Low]** Live announcements: "Tablets taken daily ({N}):" (`:183`) recomputes on Add/Delete without a
  `liveRegion` → add `liveRegion = LiveRegionMode.Polite`.
- [ ] **[Low]** Content descriptions: row thumbnail is `contentDescription = null` (`:214`) — acceptable as
  decorative since the brand is in the text.

### 8. InHandScreen — `inhand/InHandScreen.kt`
Well-labelled — the camera `IconButton` has a description and is disabled until a medication is chosen; rows
use `selectableRow`.
- [x] **[Low]** Live announcements: the camera-count result lands in "Number of tablets" via
  `LaunchedEffect(countedTablets)` (`:77`) with no announcement → consider a `liveRegion` on the field.
  **Done 2026-07-16** — announced via `View.announceForAccessibility("Counted N tablet(s)")` from the
  `LaunchedEffect` when the count arrives, rather than a `liveRegion` on the field. A field live region is an
  anti-pattern for an editable field: it would also fire on every manual keystroke and when the field resets
  after Add (announcing empty). The one-shot announcement fires only for the discrete camera-count event.
- [x] **[Low]** Live announcements: "Tablets in hand as of {date}:" (`:235`) updates after Save/Add/Delete
  without a `liveRegion`.
  **Done 2026-07-16** — the heading Text is now a `LiveRegionMode.Polite` region, so TalkBack announces the
  updated title after Save records the gathered date.

### 9. InventoryScreen — `inventory/InventoryScreen.kt`
Labels/headings solid; rows aren't grouped and the thumbnail target is undersized.
- [ ] **[Med]** Reading order: `MedicationRow` has no `mergeDescendants`, so TalkBack hits 5+ stops and the
  runway value reads in isolation ("—"/"5 months", `:176`) → wrap the row in
  `semantics(mergeDescendants = true)` (or `clearAndSetSemantics` with a composed sentence).
- [ ] **[Med]** Touch-target: the tappable thumbnail is `size(44.dp)` + `.clickable` (`:154`), below 48dp →
  use `minimumInteractiveComponentSize()` or a ≥48dp target around the 44dp visual.
- [ ] **[Low]** Content/state: the clickable thumbnail has no `Role.Button` and its description doesn't say
  it's actionable (`:150`) → add `role = Role.Button` and reword ("View tablet photo for …").

### 10. RunOutGraphScreen — `runout/RunOutGraphScreen.kt`
Colour-blind handling is genuinely strong (colour + line width + dash, Okabe–Ito palette, legend swatches
replicate the dash — **category 6 passes**), but the `Canvas` carries no semantics and the time-cursor scrub
is unreachable.
- [x] **[High]** Custom-drawn: `RunOutChart`'s `Canvas` has no `semantics`/`contentDescription` (`:233`) —
  lines, axes, "Tablets"/"Time" titles and month labels are all invisible drawn text → add a
  `contentDescription` summarizing the chart (domain days, unit count) so it announces as a described figure.
  **Done 2026-07-16** — chart now announces a summary (unit count + domain days + axes + legend pointer).
- [x] **[High]** Gesture-only / live: the time cursor is driven only by
  `detectHorizontalDragGestures`/`detectTapGestures` (`:236`) with no accessibility action, so TalkBack can't
  move it and the "Remaining at cursor" legend is pinned to day 0 → expose the cursor as a semantics slider
  (`ProgressBarRangeInfo` + `setProgress`) and mark the legend a `liveRegion`.
  **Done 2026-07-16** — the chart is now an adjustable node: `progressBarRangeInfo` over `0..domainDays`
  (stepped 1 day for ≤60-day domains, 1 week for longer) + `setProgress` wired to the cursor. Its
  `stateDescription` — re-announced on each swipe-adjust — reads the cursor position ("N days from now")
  plus each unit's remaining supply at that point, giving the screen-reader user the same figures the
  sighted legend shows (so the legend needn't be a separate live region).
- [ ] **[Med]** Dynamic type: the legend's days column is fixed `width(96.dp)` with `maxLines = 1` +
  `Ellipsis` (`:394`) — truncates at large font scales → `widthIn(min = 96.dp)` or allow wrap.
- [ ] **[Low]** Reading order: each legend `Row` (swatch + label + days) isn't merged (`:367`) →
  `semantics(mergeDescendants = true)`.
- [ ] **[Low]** Heading: the "Remaining at cursor" legend header (`:360`) isn't a heading → `.heading()`.

### 11. ScanScriptScreen — `scan/ScanScriptScreen.kt`
Camera controls are the weak spot: shutter unlabelled, flash hides its state, overlaid text has no scrim.
- [x] **[High]** Content descriptions: the shutter `IconButton` is a decorative `Box` with no
  `contentDescription` (`:199`) → label "Capture / scan form" (and disable/announce when `state.busy`).
  **Done 2026-07-16** — labelled "Capture form" (busy-disable still open).
- [ ] **[Med]** State: the flash toggle's description is a constant "Flash" across off/auto/on (`:227`) →
  reflect the mode ("Flash: auto").
- [ ] **[Med]** Contrast: white instruction text and the "Enter manually" label sit over the live preview
  with no scrim (`:192`, `:220`) → add a semi-opaque background behind the text. (The permission screen's
  white-on-black `:250` is fine.)
- [ ] **[Low]** Live announcements: the busy `CircularProgressIndicator` (`:273`) isn't announced → mark it a
  `liveRegion` / "Scanning…".
- [ ] **[Low]** Reading order: the Close/"Cancel" `IconButton` is declared last though shown top-start
  (`:266`) → declare earlier or set `traversalIndex`.

### 12. AddScriptScreen — `script/AddScriptScreen.kt`
Field labels (all ten), radio-card selection state, `selectableGroup`, and dialog roles are correct; the gap
is the date fields' invisible click overlay.
- [ ] **[Med]** State/content: "Date of issue" and "Valid to" open pickers via a transparent
  `Box().matchParentSize().clickable {}` overlay with no `contentDescription`/`Role.Button` (`:177`, `:203`)
  → give the overlay `role = Role.Button` + a description ("Date of issue, select a date").
- [ ] **[Low]** Reading order: the sparkle "new" badge `Icon` is a sibling of the `selectable` row, not
  inside it (`:428`, `:586`), so it reads as a separate node → move it inside the row (or fold into the row's
  semantics).

### 13. CameraCaptureScreen — `image/CameraCaptureScreen.kt`
Gesture-driven capture/crop with no accessible path through either phase; several unlabelled controls.
- [deferred] **[High]** Gesture-only: tap-to-focus (`:192`), pinch-to-zoom (`:204`), crop drag (`:256`) and the four
  corner resize handles (`:267`) are raw gestures with no semantics — a TalkBack user can't focus, zoom, or
  crop → add `customActions` ("Focus centre", "Zoom in/out", "Grow/shrink crop", "Move crop") or non-gesture
  stepper/slider fallbacks.
  **Deferred 2026-07-16 (inherently visual, won't fix)** — focusing, zooming and dragging a crop rectangle are
  adjustments to a live photo the user can't see, and unlike the CountTablets markers there is no non-visual
  fallback: the feature *is* "photograph the tablet" for a dispensable unit's reference image. A screen-reader
  user would not be capturing tablet photos, so a `customActions`/stepper scaffold would add complexity for a
  path that has no real destination. Same rationale as the deferred CountTablets marker editing. The screen's
  smaller labelling/state Med items below remain actionable.
- [x] **[High]** Content descriptions: the shutter `IconButton` (`:304`) has no `contentDescription`
  (decorative child `Box` `:332`) → label "Take photo". **Done 2026-07-16.**
- [ ] **[Med]** State: the flash toggle announces only "Flash" regardless of OFF/AUTO/ON (`:378`) → reflect
  the mode.
- [ ] **[Med]** Custom-drawn: the crop rectangle/position/size is `Canvas`-only (`:238`) and never exposed →
  expose crop bounds via semantics.
- [ ] **[Med]** Touch-target: the 30dp corner handles (`:266`) are below 48dp → enlarge the touch area.
- [ ] **[Med]** Heading: the phase instruction lines ("Frame the tablet…" `:298`, "Drag or resize…" `:335`)
  are the only titles but aren't headings → `.heading()`.
- [ ] **[Low]** Contrast: white instruction text over the live preview (`:298`) → add a scrim.

### 14. CountTabletsScreen — `inhand/count/CountTabletsScreen.kt`
The whole tap-to-correct review is gesture-only and the running count never announces.
- [~] **[High]** Gesture-only: tap-to-add/remove markers (`:341`) and pinch-to-zoom/pan (`:329`) have no
  semantics — a TalkBack user can't add, remove, or perceive markers → expose each marker as a semantics node
  with a "Remove tablet" action, add an "Add tablet" affordance, or provide +/- count controls.
  **Deferred 2026-07-16 (won't fix):** unlike the blister grid, markers are at *arbitrary* positions a
  screen-reader user can't judge, and capturing the photo is itself visual — so this is inherently a sighted
  tool. A TalkBack user's accessible path is to type the number straight into the In Hand "Number of tablets"
  field. We announce the detected count (below) but don't build blind marker editing.
- [x] **[High]** Live announcements: the header flips "Counting…" ↔ "N tablets" (`:305`) and every tap
  changes the count, unannounced → `liveRegion = LiveRegionMode.Polite`. **Done 2026-07-16.**
- [x] **[High]** Content descriptions: the shutter `IconButton` (`:254`) has no `contentDescription`
  (decorative child `Box` `:287`) → label "Take photo". **Done 2026-07-16.**
- [ ] **[Med]** Custom-drawn: markers are `Canvas`-only (`:367`); count/positions invisible → surface a
  semantics description.
- [ ] **[Med]** Heading: the "N tablets" headline (`:305`) isn't a heading → `.heading()`.
- [ ] **[Low]** Colour/contrast: white text over the live preview (`:246`); markers distinguished by
  `colorScheme.primary` alone (`:374`) → scrim + shape/number cue (moot until markers are exposed).

### 15. BlisterCountScreen — `inhand/blister/BlisterCountScreen.kt`
Multi-phase pack workflow whose pop interaction and blister state are gesture/Canvas-only and unannounced.
- [x] **[High]** Gesture-only: tap-to-pop (`:532`) and pinch-to-zoom/pan (`:519`) in `PackImageView` have no
  accessible alternative → expose each blister cell as a semantics node with toggle state + "Pop/Unpop"
  action, or an accessible per-cell grid fallback.
  **Done 2026-07-16** — a touch-free `AccessibleBlisterGrid` overlays one semantics node per blister with a
  "Pop"/"Restore" `onClick`; sighted tap/zoom fall through unchanged. (Pinch-zoom stays a sighted nicety.)
- [x] **[High]** State: full-vs-popped state is `Canvas`-only (ring vs filled hole, `:558`) with no
  `stateDescription` → attach per-cell `stateDescription` ("full"/"empty"). **Done 2026-07-16** — each grid
  node sets `stateDescription`.
- [x] **[High]** Live announcements: the "Full X · Empty Y" tally (`:402`) updates per pop and the
  "Finding packs…" `LoadingOverlay` (`:164`) aren't live regions → `liveRegion = LiveRegionMode.Polite`.
  (The `PopFeedback` haptic at `:384` helps tactile users but isn't speech.) **Done 2026-07-16** — tally and
  loading label are now polite live regions.
- [x] **[High]** Content descriptions: the shutter `IconButton` (`:296`) has no `contentDescription`
  (decorative child `Box` `:325`) → label "Take photo". **Done 2026-07-16.**
- [ ] **[Med]** Heading: `Header` titles ("Confirm layout", "Pop blisters", "Total on hand", `:454`) are
  plain `Text`, never headings → `.heading()` inside `Header`.
- [ ] **[Low]** Contrast: white text over the live preview (`:289`); popped vs full also differ by shape, so
  the colour cue is acceptable → add a scrim behind preview text.

---

## Remediation priority

**Tier 1 — unblock screen-reader users (High). ✅ Complete (2026-07-16).** All 13 High items are resolved:
11 fixed and 2 deferred as inherently visual (with rationale in-line). Fixed: `RunOutGraph` (chart
description + adjustable cursor slider), `CountTablets` (count live region), `BlisterCount` (pop action +
per-cell state + tally live region), every camera shutter label, and the `Scripts` "Dispensed" cell action.
Deferred (no non-visual destination): `CameraCapture` focus/zoom/crop gestures and `CountTablets` marker
add/remove — both are adjustments to an image the user can't see, with the accessible path lying elsewhere
(typing into the In Hand "Number of tablets" field). Remaining a11y work is Tier 2/3 only.

**Tier 2 — friction/ambiguity (Med).** Row grouping (`Inventory`, `Medications`, `Scripts`), the date-field
overlay in `AddScript`, flash-state descriptions, undersized touch targets (`Inventory` 44dp, `CameraCapture`
30dp), preview-text contrast scrims, and the graph legend's dynamic-type width.

**Tier 3 — polish (Low).** Merge-descendants on remaining rows, missing `.heading()` on a few titles,
live-region announcements for schedule/in-hand totals, sort-menu selected semantics.

## Follow-up

- This is a **static** review. A live **TalkBack** pass on-device would confirm reading order, catch focus
  traps, and validate the custom-action fixes.
- Consider Google's **Accessibility Scanner** app for automated touch-target/contrast/label checks.
- The **hardcoded-strings** point is a separate localization effort, not tracked here.
