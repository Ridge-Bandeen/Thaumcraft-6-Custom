# Thaumcraft 1.21.1 Port — Outstanding Work

Living TODO for the 1.12.2/Forge → 1.21.1/NeoForge port. Generated from a full
sweep of the true original's `BlocksTC.java`/`ItemsTC.java` (via
`git show 31baee9:...`, the repo's pristine-original first commit) against
this port's current `BlocksTC.java`/`ItemsTC.java`, cross-checked field by
field. Update this file as items close or new gaps surface — the goal is that
a future session (or sweep) can start here instead of re-deriving the whole
list from scratch.

Full narrative history/rationale for everything below lives in the project
memory file (`thaumcraft-1211-port.md`); this file is the compact, actionable
index.

## Confirmed real gaps (actionable)

Each of these is genuinely real, registered content in the true original with
no equivalent anywhere in the current port (verified by direct source read,
not just a name diff).

*(Empty as of 2026-09-14 — a fresh full sweep found only two real gaps
(`banners`, `creativeFluxSponge`), both closed same day. See "Recently
closed" and "Fresh sweep, 2026-09-14" below. Next work should come from
user direction or another fresh sweep.)*

## Needs further triage (real content exists in the original; scope/priority unconfirmed)

- **`effectSap`** (`BlockEffect`, a sticky-web trap block) — confirmed real
  but low priority. Its only two true-original placers are
  `EntityEldritchWarden` (an unbuilt boss) and `FocusEffectCurse`'s own
  block-hit branch - which this port's modern `FocusEffectCurse` already
  documents dropping deliberately ("that block isn't ported, and there's
  nothing sensible to fall back to"). Not worth building until either real
  consumer exists.
- **`liquidDeath`** (`BlockFluidDeath`) — confirmed real but has no
  standalone placer outside client-rendering setup (`ProxyBlock.java`);
  genuinely tangled with the already-known-blocked finite-fluid/Taint
  world-spread subsystem, not a separate gap.
- **`grassAmbient`, `shimmerleaf`, `vishroom`, `bloom`** — worldgen
  plant/ambient blocks. Earlier session notes already confirmed
  `world/plants/` as worldgen-only and out of near-term scope; these are
  likely the same bucket. Low priority.
- **`tubeOneway`** — a **known, already-diagnosed blocker**, not a fresh
  gap: needs the same unported per-face wand raytracing that already
  blocked the Levitator/Redstone Relay (and shaped `TileTube`'s own
  "all 6 sides always open" simplification). Its sibling `tubeBuffer` is
  closed (see "Recently closed" below) - it didn't need per-face toggling
  for its own real distinguishing feature (multi-aspect storage), but
  one-way tubes are *defined* by a single configurable facing, so this one
  stays blocked until that raytracing gap is addressed some other way.

## Confirmed dead-in-the-true-original (do not port — matches nothing real)

- **`eldritchEye`, `runedTablet`** (items). Declared as public fields in the
  true original's own `ItemsTC.java` and referenced in `ConfigAspects.java`
  (object-tag registration) and `ConfigEntities.java` (a mob drop table) —
  but **never actually assigned/registered anywhere** in `ConfigItems.java`
  or any other true-original file (confirmed via exhaustive `git grep` for
  `ItemsTC.eldritchEye =` / `ItemsTC.runedTablet =` across the whole
  pristine-original tree — zero hits). These are vestigial/dead fields in
  the shipped original itself, not a porting gap. No action needed.
- **`fluxScrubber`, `auraTotem`, `taintDust`** (blocks) — confirmed
  2026-09-09, same real pattern as `eldritchEye`/`runedTablet`: declared as
  public fields in the true original's own `BlocksTC.java` but **never
  actually assigned/registered anywhere** (confirmed via exhaustive
  `git grep` for `fluxScrubber =`/`auraTotem =`/`taintDust =` across the
  whole pristine-original tree — zero hits, and zero other real usages
  anywhere either). Vestigial/dead fields in the shipped original itself.
  No action needed.
- **`effectShock`** (`BlockEffect`) — confirmed 2026-09-09 real but
  genuinely unreachable in the true original itself: registered and has
  real self-reactive behavior in `BlockEffect.java`, but has **zero real
  placer anywhere** in the whole pristine-original tree (only its own
  class ever references it). Never actually creatable in the true
  original. No action needed.
- **`effectGlimmer`** (`BlockEffect`) — confirmed 2026-09-09 already
  superseded, not a gap: its only two true-original placers
  (`TileLampArcane`, `ToolEvents`) are both already real and shipped in
  this port using vanilla's own `LightBlock` (added 1.17) instead - a
  real, documented, strictly-better modern replacement for the exact
  "hand-rolled invisible light marker" trick `effectGlimmer` existed for
  (see `TileLampArcane`'s own doc comment). No action needed.
- **`empty`** (`BlockTranslucent`) — confirmed 2026-09-09 real (registered)
  but has zero real usages anywhere in the true original outside its own
  registration - a functionally inert novelty block, not worth porting.
- **`thaumcraft.common.golems.ai.AINearestValidTarget`** (Java class, not
  a block/item) — confirmed 2026-09-09, same dead-field pattern: declared
  and implemented in the true original but **zero real usages anywhere**
  in the whole pristine-original tree. A same-named but unrelated class
  exists as a local inner class inside `EntityTurretCrossbow`/
  `EntityTurretCrossbowAdvanced` (already real/shipped in this port under
  their own separate logic) - do not confuse the two. No action needed.
- **The "cultist dimension"** — confirmed in an earlier session chunk to
  never have existed in real shipped Thaumcraft 6 (TC5-only concept, or a
  still-open Azanor GitHub suggestion). See memory file for the full
  investigation. The real Ancient/Eldritch stone family was shipped instead,
  faithfully matching the true original's own genuinely-unreachable content.
- **`stairsEldritch`** (block) — confirmed 2026-09-14 by the fresh sweep,
  same dead-field pattern: declared in the true original's own
  `BlocksTC.java` but **never assigned/registered anywhere** (its stair-
  family siblings - `stairsArcane`/`ArcaneBrick`/`Greatwood`/`Silverwood` -
  are all real and confirmed already built here). No action needed.
- **`creativePlacer`** (item) — confirmed 2026-09-14 by the fresh sweep,
  same dead-field pattern: declared in the true original's own
  `ItemsTC.java` but **zero real assignment/usage anywhere** (unlike its
  near-namesake `ItemCreativeFluxSponge`, which is real and now built - see
  "Recently closed"). No action needed.

## Confirmed already-built or intentionally consolidated (false positives from the raw name diff — no action needed)

- **Grapple Gun components** (`grappleGunTip`, `grappleGunSpool`) — checked
  2026-09-09. The modern `grapple_gun.json` recipe is already a single-step
  `arcane_shaped` recipe crafted directly from raw ingredients (iron ingot,
  string, redstone, planks), not from the true original's own two
  intermediate arcane-crafted components. A deliberate, already-made
  consolidation (matching this port's own established
  metadata/step-collapsing convention elsewhere), not a gap — building the
  two intermediate items now would just add redundant, functionally-inert
  crafting materials. No action needed. (`jarBrace`, found in the same sweep
  pass but unrelated to the grapple gun, is a real, separate item — see
  "Recently closed" below, now built.)
- **`enchantedPlaceholder`** (item) — checked 2026-09-14 by the fresh sweep.
  Real and registered in the true original, but its only two real
  consumers anywhere in that codebase are the stale, never-ported
  `BlockUtils`/`InventoryUtils` classes' own "simulate a block break with a
  specific tool's enchantments" helper (a fake-item stand-in used when
  silk-touch/fortune is being overridden but the real tool item is
  unavailable/empty). This port's own real golem-breaker-seal and turret-
  bore mechanics already use direct modern vanilla APIs instead of that
  generic helper, so nothing in the compiled tree actually needs this item.
  Not tracked as an open gap unless a future consumer needs it.

## Confirmed already-built (false positives from the raw name diff — no action needed)

These surfaced in the mechanical field-name diff but were confirmed, on
inspection, to already exist under this port's own established naming/
consolidation conventions:

- **Blocks**: `doubleSlab*` family (handled via modern `type=bottom/top/double`
  slab blockstates, not separate double-slab blocks); `crystalAir/Earth/
  Entropy/Fire/Order/Taint/Water` (primal crystal clusters, already built);
  `slabArcaneBrick/Stone`, `stairsArcane/ArcaneBrick`, `pedestalArcane`,
  `smelterBasic` (pure naming-convention differences — e.g. `stoneArcaneSlab`
  instead of `slabArcaneStone` — already-shipped content).
- **Items**: `ingots`/`nuggets`/`plate`/`mind`/`modules`/`turretPlacer`
  (true original's single metadata-variant fields, already split into this
  port's own separate per-variant fields — `ingotBrass`/`ingotThaumium`/
  `ingotVoid`, `nuggetThaumium`/`nuggetBrass`/`nuggetRareearth`/etc.,
  `mindClockwork`/`mindBiothaumic`, `moduleVision`/`moduleAggression`,
  `turretPlacerBasic`/`Advanced`/`Bore` — all confirmed built and recipe'd
  this session); `amuletVis`/`bandCuriosity`/`charmVerdant`/`charmVoidSeer`/
  `ringCloud` (already-shipped curio items); `lootBag` (already shipped);
  `seals` (true original's single `ItemSealPlacer`-array field, already
  split into this port's own `sealGuard`/`sealLumber`/`sealHarvest`/
  `sealButcher`/`sealBreaker`/`sealPickup`/`sealStock`/`sealProvide`/
  `sealFill`/`sealEmpty`/`sealUse` + their Advanced variants — all confirmed
  built); `focus2`/`focus3` (superseded by this port's own, more faithful
  `focusBlank1`/`focusBlank2`/`focusBlank3` — the true original's real
  design turned out to be 3 blank tiered canvases assembled via the Focal
  Manipulator, not fixed per-element items; `focus1`/`focus2`/`focus3` as
  literal true-original field names were already claimed here by the
  earlier, since-superseded-in-spirit-but-kept-alongside `ItemFocusFire`
  etc. preset foci — see memory file's Focal Manipulator arc for the full
  story).

## Out of scope by standing exclusion (not a gap — a deliberate non-goal)

- **`baubles`** (`ItemBaubles`, confirmed real in the true original). Covered
  by the standing Baubles-integration-out-of-scope exclusion unless the user
  says otherwise. Not tracked as a gap.

## Deferred subsystems (already scoped in memory, not re-derived here)

Large, already-investigated subsystems with their own real blockers or open
sub-arcs — see the project memory file for full detail, not repeated here:

- **Research Table minigame** — **fully closed 2026-09-09.** The real
  "core" tier (`api.research.theorycraft`, all 9 cards + `AidBookshelf`) and
  the true original's larger `common.lib.research.theorycraft` "extra
  content" tier (all 19 remaining cards, all 8 remaining aids) are now
  ALL registered - see `Thaumcraft.java`'s own `commonSetup` doc comment.
  `AidGlyphedStone`/`AidBasicAuromancy`/`AidBasicEldritch` all turned out to
  be the same real story - each aid class already existed but was never
  registered, and each one's supposedly-missing trigger block was already
  real and shipped under this port's own modern name (`stoneAncientGlyphed`
  as-is, `wandWorkbench` as `focalManipulator`, `eldritch` as
  `cultistRitualAltar`). `AidBrainInAJar` needed real new work (`jarBrain`,
  the "Brain in a Jar" XP-battery device). `CardCelestial` - **this file's
  own earlier "Baubles-tied" note for it was wrong and got corrected the
  same session** (direct source inspection found zero Baubles/curio
  interface anywhere in its real dependency chain) - needed real new work
  too (`ItemCelestialNotes`, `ScanSky`, and restoring `ItemThaumometer`'s
  own "scan nothing" hook) - see `git log` around 2026-09-09 for the 5
  commits closing this whole arc (trimmed from "Recently closed" below).
- **Golem construction pipeline** — **closed 2026-09-09.** Every genuinely
  real item this arc's own re-scope turned up has now shipped:
  `GolemHelper`/`IGolemAPI`/`IGolemProperties`/`EnumGolemTrait`,
  `EntityThaumcraftGolem`, `GolemProperties`, `ItemGolemBell`/
  `ItemGolemPlacer`, the real task/seal-assignment system, `GolemArmDart`,
  and (closing this arc) CLIMBER-trait pathfinding - see `git log` around
  2026-09-09 for the closing commits (trimmed from "Recently closed"
  below). Client-only golem-builder-screen button widgets/
  part-model renderers were NOT diffed carefully - likely mostly moot given
  `GuiGolemBuilder`'s own doc comment already describes a deliberate
  "lighter, simplified screen, not a line-for-line port" treatment, same as
  every other client screen this port has built; flag if a future sweep
  finds otherwise.
- Focal Manipulator — **closed**, full arc shipped (tile, block, menu,
  screen, craft button, graph-save packet, node-graph canvas). Listed here
  only so it isn't mistakenly re-opened by a future sweep.

**A pattern worth remembering for whoever picks this file up next**: this
whole "Deferred subsystems" section had gone stale in ways the rest of this
file hadn't - each entry here is a coarse, old estimate nobody had reason to
revisit until someone actually went looking. Before treating any remaining
entry above as an accurate scope, do the same precise diff/direct-source-check
this session did (true original's file list for that area vs.
`build.gradle`'s include list; for a claimed technical blocker, check whether
a real modern vanilla precedent already solves it, the way `AmethystClusterBlock`
turned out to directly answer the crystal-cluster rendering "blocker") rather
than trusting the prose - it was wrong about all 4 of the original entries
here to some degree (monster roster, Research Table, and golem construction
were all far more complete than documented, and golem construction's own
remaining CLIMBER-pathfinding residual turned out small too once actually
investigated - vanilla's own `Spider`/`WallClimberNavigation` answered it
directly; crystal-cluster rendering's own claimed blocker turned out to not
really exist either, same `AmethystClusterBlock` story; of Research Table's
own 4 flagged "block-blocked" aids, 3 turned out to not be blocked at all -
each trigger block was already real and shipped under a different modern
name, just never connected - the 4th (`AidBrainInAJar`) and the separately-
flagged `CardCelestial` (wrongly noted as Baubles-tied) both DID need real
new work, which also happened). **All 4 original "Deferred subsystems"
entries are now closed - this section is empty of open work.**

## Standing manual-verification item (not a code gap)

- **UI visual-parity checklist** (`thaumcraft-ui-visual-parity-checklist.md`
  in project memory) — a running list of client-rendering simplifications
  made throughout the port that need an actual `runClient` playtest to
  confirm they look/feel right. Not re-derived here; keep using that file.

## Recently closed (kept briefly for continuity, safe to delete once stale)

*(Trimmed 2026-09-14 - the previous ~15 entries here, from Mirror of
Essentia through CardCelestial/"Celestial Studies", are all long-since
merged into the sections above and fully detailed in `git log` and the
project memory file; not re-listed here to keep this file scannable.)*

- **Dyed banners** (`BlocksTC.banners`, 16 `DyeColor` variants) — closed
  2026-09-14, found by the fresh full sweep below. `BlockBannerTC`'s own
  doc comment already flagged this: the true original shared its class
  between the server-placed Crimson Cult banner (already shipped) and 16
  real, unrelated, player-craftable dyed variants (deferred - "nothing in
  this port needs them yet"). Real client rendering work, not just
  registration: `TileBannerRenderer` had no per-dye tint at all - added one
  via `ModelPart`'s real color-render overload, plus a real `ItemColor`
  handler for the held-item icon (a block color handler would be
  unreachable - `RenderShape.INVISIBLE`). Real arcane-shaped recipe per
  color (dyed wool + stick + any wood slab, `BASEINFUSION` research + 10
  vis), matching the true original's own real recipe exactly. See commit
  "Give the 16 dyed banners their real craftable variants".

- **Flux Sponge** (`creativeFluxSponge`) — closed 2026-09-14, second fresh-
  sweep find. A real, creative-only debug item (no recipe, `Rarity.EPIC`,
  explicit "Creative only" tooltip) - right-click drains real flux from a
  real 9x9-chunk area, sneak-right-click also removes nearby real
  `EntityFluxRift`s. See commit "Give the Flux Sponge its real debug
  mechanic".

## Fresh sweep, 2026-09-14 (full re-diff of `BlocksTC.java`/`ItemsTC.java`)

TODO.md's actionable sections were fully empty after the 2026-09-09 backlog
closed out entirely (see git log around that date for the full arc). Rather
than guess at what's left, re-ran the same full field-name diff the
2026-09-09 sweep used (true original's `BlocksTC.java`/`ItemsTC.java` field
declarations vs. this port's own registered ids, converted snake_case →
camelCase, then hand-triaged every non-obvious miss against direct source
reads) - 161 block fields + 111 item fields in the true original, 131/296
ids registered here respectively. Findings:

- **Real gaps found and closed**: `banners` (16 dyed banner variants),
  `creativeFluxSponge` - see "Recently closed" above for both.
- **Confirmed dead-in-the-true-original** (new, added to that section
  below): `stairsEldritch`, `creativePlacer` - both declared fields with
  **zero real assignment/usage anywhere** in the whole pristine-original
  tree, same vestigial-field pattern as `eldritchEye`/`fluxScrubber`/etc.
- **Confirmed already-built or not needed** (new, added to that section
  below): `enchantedPlaceholder` - real and registered in the true
  original, but its only real consumers are the stale, never-ported
  `BlockUtils`/`InventoryUtils` "simulate breaking a block with a specific
  tool's enchantments" helpers; this port's own real golem-breaker/turret-
  bore mechanics already use direct modern vanilla APIs instead, so nothing
  in the compiled tree actually needs this item. Not tracked as an open gap.
- **Everything else** in the ~70 raw candidate misses resolved to already-
  known naming-convention differences (crystal `Aspect`-keyed ids,
  `doubleSlab*`/reversed-word-order slab-stair names, `condenserlattice`'s
  own casing quirk in the true original, etc.) or already-documented
  "Needs further triage"/"Confirmed dead" entries from the 2026-09-09 sweep.

**TODO.md's actionable sections are empty again as of this sweep closing
out.** The natural next step for whoever picks this up next is another
fresh sweep (the same technique, applied again after enough new work has
landed) or user-directed work - there's no more pre-scoped backlog here.

---
*Originally generated 2026-09-09 via a full `BlocksTC.java`/`ItemsTC.java`
field-name sweep against the true original (commit `31baee9`); re-run in
full 2026-09-14 once the prior backlog closed out. Re-run the same way
again whenever this file's actionable sections go empty - update
incrementally as items close or new gaps are found in between.*
