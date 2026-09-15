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

- **Label dyeing recipe** (a real component-matching custom `Ingredient`) —
  {@code ItemLabel}'s own doc comment already flags this as a deliberate,
  known scope cut: the original dyed a blank label with a specific aspect
  using a filled Phial matched by NBT (`IngredientNBTTC`, a custom 1.12.2
  ingredient type). Needs a real custom `Ingredient` matching a specific
  `ASPECTS` component value - new recipe-system work. The true original's
  own `CraftingEvents` phial-refund hook (crafting an aspected label gives
  the filled phial back rather than consuming it) is downstream of this and
  only worth building alongside it. Small and niche - the core
  apply-a-label-to-lock-a-jar mechanic is already real and tested regardless
  of how a filled label is obtained.

*(`BlocksTC`/`ItemsTC` registries themselves are confirmed complete as of
2026-09-14 - two field-name sweeps in a row found nothing real left there.
The item above came from a broader, deeper sweep of the whole file tree,
which also turned up `required_craft`/`required_item` auto-progression
and the warp-on-craft penalty - both now closed, see "Recently closed"
below. See "Fresh sweep, 2026-09-14 (part 2)" below for what else that
sweep turned up and what's still unscoped.)*

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
- ~~**Player/World/Entity/Chunk/Crafting/Server lifecycle events**~~ —
  **scoped 2026-09-14, and it was a false alarm: this package is already
  done.** The whole-tree sweep flagged it because all 7 original files are
  missing from `build.gradle`, and the raw handler count (34) dwarfed
  `Thaumcraft.java`'s own 12. But the files were never meant to be ported
  *as files* - earlier sessions (see the memory file's own 2026-09-06/07
  entries) already read all 7 in full and redistributed their real content
  into **10 focused, real, compiled modern classes** that all exist today:
  `ChampionModEvents`, `FortressMaskEvents`, `MobDeathDropEvents`,
  `RunicShieldEvents`, `PassiveResearchEvents`, `WarpEvents`,
  `WornGearEvents`, `GearTooltipEvents`, `ToolEvents`, `EssentiaHandler` -
  plus `Thaumcraft`'s own `tickGolemSeals`/`onNoteBlockPlay`. The rest was
  confirmed moot: `ChunkEvents`/`WorldEvents`' aura+seal persistence is
  superseded by the real `SavedData` migration, `KeyHandler`'s two keys are
  superseded by `FocusRadialOverlay` and `ItemGolemBell`'s own real
  interactions, and `ServerEvents`' gradual-mining `VirtualSwapper` is an
  already-documented, already-accepted drop. **The real lesson this
  re-confirms: a file-path diff measures files, not behaviour** - always
  check where the behaviour actually went before calling it a gap.
  One genuinely-open leftover did fall out of this scoping pass and is
  listed under "Confirmed real gaps" above (label dyeing). Two more,
  `required_craft`/`required_item` auto-progression and `CraftingEvents`'
  warp-on-craft penalty, were also found here and are now closed (see
  "Recently closed" below) - **the warp-on-craft one is a correction, not
  just a closure**: it was first written up right here as a real no-op
  even if ported, reasoning from `ThaumcraftApi.getWarp` reading a per-item
  value nothing assigns. That reasoning conflated two unrelated mechanisms
  that both happen to be called "warp" - see the commit message ("Restore
  the real warp-on-craft penalty - an earlier call here was wrong") and
  `WarpEvents#onItemCrafted`'s own doc comment for the full story. The
  `GearTooltipEvents` cut this originally pointed at is real and still
  open (the separate `TC.WARP`-NBT worn-gear addend), just not the same
  thing.

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

- **`required_craft`/`required_item` research auto-progression** — closed
  2026-09-14. `ThaumcraftResearchLoader` deliberately skipped parsing both
  stage gates, saying why: display-only without a crafting/possession
  hook that didn't exist yet. New `ResearchRequirements` + real
  `ResearchCraftingEvents` build that hook - a real craft-reference
  registry granting the original's own `"[#]"`-keyed virtual research
  flags, plus a real live inventory check for `required_item`, both
  exposed as a real `isStageSatisfied` query. The legacy-spec resolver
  (1.12.2 metadata-variant specs like `ingot;1;2`) was built by reading
  the true original's own `ConfigItems` subtype arrays rather than
  guessing - and that reading also turned up several specs that are
  already dead in the true original itself (`thaumcraft:metal`,
  `thaumcraft:nitor`, `thaumcraft:leather` were never real registered
  items), so resolving those to nothing is faithful, not a gap.
  Deliberately **not** wired to actual stage advancement - blocked on the
  Thaumonomicon reader's atomic-completion architecture, same as before.
  See commit "Give research its real craft/possession auto-progression
  gates".

- **`CraftingEvents` warp-on-craft penalty** — closed 2026-09-14, and a
  correction of an earlier session's own wrong call (see the "Fresh
  sweep, 2026-09-14 part 2" note above for the full story). The user
  asked for a wiki double-check of the "must be a decompilation artifact"
  claim; reading the true original directly disproved it outright -
  `ThaumcraftApi.getWarp(ItemStack)` reads a static item→warp map
  (`addWarpToItem`), never the `TC.WARP` NBT tag that was the basis for
  the original wrong conclusion. New `ThaumcraftApi.addWarpToItem`/
  `getWarp` plus a real `WarpEvents#onItemCrafted` hook (gated on
  `wussMode`, matching the original) restore the mechanic; the one real
  registration shipped Thaumcraft 6 has - Brain in a Jar, 1 permanent
  warp - is wired in `Thaumcraft`'s setup. See commit "Restore the real
  warp-on-craft penalty - an earlier call here was wrong".

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

- **`ScanGeneric`/`ScanEnchantment`/`ScanPotion`** — closed 2026-09-14, the
  first of the whole-tree sweep's own real gaps. `ScanGeneric` is the true
  original's own general catch-all: anything with real aspects grants real
  OBSERVATION progress toward every real research category on its first
  scan. Restored two things `ScanningManager`'s own doc comment listed as
  cut, because the item-shaped handlers need them to be reachable at all -
  real inventory-scanning recursion (via NeoForge's own
  `Capabilities.ItemHandler.BLOCK`, the helper whose absence was the
  original stated reason for the cut) and real `getItemFromParms`
  block-to-item resolution. `ScanPotion` keeps the original's own
  one-instance-per-effect shape; `ScanEnchantment` couldn't (modern
  enchantments are data-driven, nothing to enumerate at setup) so it's one
  dynamic instance that walks the player through an item's enchantments one
  scan at a time. See commit "Give the thaumometer its real general scan
  mechanic".

- **Robe/Void-robe dye support** — closed 2026-09-14. Both robe classes'
  own doc comments already listed this as a cut, and `ItemRobeArmor`'s even
  named the modern replacement. The original's two dye recipe classes were
  byte-for-byte clones of vanilla's own 1.12.2 `RecipesArmorDyes`, so
  instead of porting them the six pieces now sit in the real
  `#minecraft:dyeable` tag - which is exactly what vanilla's own
  `ArmorDyeRecipe` **and** its own cauldron-wash interaction key off, so
  both are real again with zero mod-side recipe code. Color lives in
  vanilla's own `DYED_COLOR` component and tints both the real worn armor
  layer (both materials' `Layer` now passes `dyeable=true`) and the real
  item icon, falling back to the original's own real default purple when
  undyed. See commit "Give the robe armor sets their real dye support".

## Fresh sweep, 2026-09-14 part 1 (full re-diff of `BlocksTC.java`/`ItemsTC.java`)

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

## Fresh sweep, 2026-09-14 part 2 (whole-tree, "how close to done are we?")

User asked to verify how close to complete the whole port really is, not
just `BlocksTC`/`ItemsTC` (already confirmed complete twice - see part 1
above). Broadened the method: diffed the true original's **entire**
`src/main/java` file tree (903 real `.java` files via
`git show 31baee9 --stat --name-only`) against every file currently listed
in `build.gradle`'s own `include` list (572 files) - 482 raw "missing path"
candidates, 151 confirmed genuinely-new modern-only files (no 1.12.2
counterpart at all - registries, data components, etc.), the rest
hand-triaged by directory.

**Real, honest picture**: the ~482 raw misses break down into a few large,
well-understood buckets, most of which are NOT oversights:

- **Client-only rendering/GUI/particle FX/OBJ loading** (~120 files across
  `client/renderers/*`, `client/fx/*`, `client/gui/*`, `client/lib/obj`) -
  a real, large, but already **consistently, deliberately deferred**
  boundary this whole port has respected from the start (every device this
  session ships documents its own "no bespoke renderer/GUI yet" cut where
  relevant). Not new information, just now has a real total file count.
- **CodeChicken library dependency** (~30 files under `codechicken/lib/`) -
  the same already-known wand-raytracing blocker (tube facing, Levitator,
  Redstone Relay, Pattern Crafter, Tube One-way) at its real total size.
  Needs a real modern-API replacement investigated as its own chunk before
  any of its dependents can unblock.
- **Old 1.12.2 architecture, fully superseded** (`proxies/` - the whole
  Forge client/server-proxy split doesn't exist as a concept in modern
  NeoForge; `common/container/Container*`/`Inventory*`/`Slot*` - superseded
  by this port's own real `*Menu` classes, confirmed 1:1 for every real
  device that has one; `common/config/Config*.java` - superseded by
  `BlocksTC`/`ItemsTC`/`Thaumcraft.java` themselves; `common/blocks/BlockTC*`
  base classes/`IBlockFacing*` interfaces - superseded by direct vanilla
  base classes + `BlockStateProperties.ENABLED`/`FACING`, already this
  port's own established convention). None of these are gaps - checked a
  representative sample of each bucket directly, not just assumed.
- **Worldgen** (`common/world/biomes`, `common/world/objects` - custom
  biomes, tree/flower/mound generators) - the same already-established
  "worldgen is out of near-term scope" bucket `grassAmbient`/`shimmerleaf`/
  etc. already covered, just confirmed to extend to the whole layer.
- **Networking packets** (~35 old files vs. 4 real modern ones) - not
  individually audited this pass, but the large gap is consistent with
  most of the originals existing purely to sync now-deferred client
  rendering/particle state, not real gameplay - flagged as an assumption,
  not independently confirmed like the buckets above.
- **`common/golems/ai`** (12 files, `AIArrowAttack`/`AIFollowOwner`/
  `AIGotoHome`/`AINearestValidTarget`/`AIOwnerHurtByTarget`/`AIOwnerHurtTarget`/
  `FlightMoveHelper`/`FlightNodeProcessor`/`GolemNodeProcessor`/`ITargets`/
  `PathNavigateGolemAir`/`PathNavigateGolemGround`) - re-verified individually
  given the golem arc's own "fully closed" claim; ALL confirmed either dead-
  in-the-true-original, or already faithfully replaced by direct vanilla
  API reuse (`FollowOwnerGoal`, `Mob#restrictTo`, `OwnerHurtByTargetGoal`/
  `OwnerHurtTargetGoal`, `FlyingPathNavigation`, `WallClimberNavigation`),
  matching this session's own already-documented "reuse vanilla directly"
  precedent exactly. The golem arc's closure holds up under this deeper check.
- **Tool/potion/recipe-type "missing files" that were really just renames**:
  `common/items/tools/ItemThaumium*`/`ItemElemental*` (all real, just
  registered as plain `Item` inline rather than as ported custom classes -
  `thaumiumPick`/`elementalPick`/`voidPick` etc. all confirmed real and
  already covered by the part-1 field sweep); `common/lib/potions/Potion*`
  (all real, renamed `Potion*` → `MobEffect*` matching modern Minecraft's
  own 1.20.5+ rename, all 9 registered in `ThaumcraftMobEffects`);
  `InfusionRunicAugmentRecipe` (real, ported as `RunicArmorRecipe`);
  `ShapedArcaneVoidJar`/`RecipeMagicDust` (real, both now real modern
  data-driven recipe JSON instead of a hardcoded Java recipe class -
  `jar_void.json`/`salis_mundus.json` already exist); `DustTriggerOre`
  (already explicitly documented as intentionally not ported -
  `ThaumcraftDustTriggers`'s own doc comment: "no real registered trigger
  in the original ever needed it for anything this port has built").

**Real gaps this deeper sweep actually found** (added to "Confirmed real
gaps"/"Needs further triage" above): `ScanGeneric`/`ScanEnchantment`/
`ScanPotion` (real, valuable, well-scoped - the generic thaumometer-scan-
for-research mechanic), robe/void-robe armor dye recipes (real, small,
cosmetic), and the `common/lib/events` lifecycle-handler subsystem (real,
34 total handlers, genuinely **unscoped** - not confirmed as a real gap of
that exact size, just confirmed NOT already fully covered by
`Thaumcraft.java`'s own much smaller 12-handler surface; needs its own
per-handler investigation before treating it as a single big gap).

**Honest answer to "are we near the end"**: for real, player-facing
gameplay content (blocks, items, devices, mobs, recipes, research), yes -
`BlocksTC`/`ItemsTC` are confirmed complete, and this deeper sweep found
only 2 small-to-medium real gaps (`ScanGeneric` trio, robe dyes) plus one
genuinely-unscoped-but-possibly-smaller-than-it-looks area (lifecycle
events). The large remaining file count is real but is almost entirely
**already-known, already-accepted scope boundaries** (client rendering,
CodeChicken, worldgen) rather than undiscovered gameplay gaps - this port
is much closer to "content-complete, client-and-polish-incomplete" than
"still missing mechanics."

---
*Originally generated 2026-09-09 via a full `BlocksTC.java`/`ItemsTC.java`
field-name sweep against the true original (commit `31baee9`); re-run in
full 2026-09-14 once the prior backlog closed out, then broadened the same
day to a whole-tree sweep to answer "how close to done are we." Re-run the
`BlocksTC`/`ItemsTC` sweep whenever this file's actionable sections go
empty again; the whole-tree sweep is worth re-running much less often
(most of its own findings are large, slow-moving scope buckets, not fast-
changing content) - update
incrementally as items close or new gaps are found in between.*
