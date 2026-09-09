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

*(Empty as of 2026-09-09 — Loot Urns, the last item here, closed. See
"Recently closed" below. Next work should come from "Deferred subsystems" or
a fresh sweep.)*

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

- **Research Table minigame** — **effectively closed 2026-09-09.** The
  real "core" tier (`api.research.theorycraft`, all 9 cards + `AidBookshelf`)
  is fully ported, and of the true original's larger
  `common.lib.research.theorycraft` "extra content" tier, **18 of 19
  remaining cards and ALL 8 remaining aids are now registered** (see
  `Thaumcraft.java`'s own `commonSetup` doc comment for the exact running
  list). `AidGlyphedStone`/`AidBasicAuromancy`/`AidBasicEldritch` turned out
  to be the same real story - each aid class already existed but was never
  registered, and each one's supposedly-missing trigger block was already
  real and shipped under this port's own modern name (`stoneAncientGlyphed`
  as-is, `wandWorkbench` as `focalManipulator`, `eldritch` as
  `cultistRitualAltar`). `AidBrainInAJar` needed real new work - a real
  `TileJarBrain`/`BlockJarBrain` "Brain in a Jar" XP-battery device, now
  shipped too - see "Recently closed" below for all 4 commits. The ONLY
  genuinely open item left in this whole arc: `CardCelestial` (needs
  `ItemCelestialNotes`, itself confirmed flavor-only/Baubles-tied by an
  earlier sweep - out of scope by the standing Baubles exclusion unless
  that changes).
- **Golem construction pipeline** — **closed 2026-09-09.** Every genuinely
  real item this arc's own re-scope turned up has now shipped:
  `GolemHelper`/`IGolemAPI`/`IGolemProperties`/`EnumGolemTrait`,
  `EntityThaumcraftGolem`, `GolemProperties`, `ItemGolemBell`/
  `ItemGolemPlacer`, the real task/seal-assignment system, `GolemArmDart`,
  and (closing this arc) CLIMBER-trait pathfinding - see "Recently closed"
  below for the last one. Client-only golem-builder-screen button widgets/
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
really exist either, same `AmethystClusterBlock` story; 3 of Research
Table's own 4 flagged "block-blocked" aids turned out to not be blocked at
all - each trigger block was already real and shipped under a different
modern name, just never connected - the 4th, `AidBrainInAJar`, DID need real
new work, a real `TileJarBrain`/`BlockJarBrain` XP-battery device, now
shipped). This section now has only ONE tiny, genuinely open, non-blocking,
Baubles-gated item left anywhere in it (`CardCelestial`) - everything else
that was here across all 4 original entries is closed.

## Standing manual-verification item (not a code gap)

- **UI visual-parity checklist** (`thaumcraft-ui-visual-parity-checklist.md`
  in project memory) — a running list of client-rendering simplifications
  made throughout the port that need an actual `runClient` playtest to
  confirm they look/feel right. Not re-derived here; keep using that file.

## Recently closed (kept briefly for continuity, safe to delete once stale)

- **Mirror of Essentia** (`mirrorEssentia`) — closed 2026-09-09. Real
  `BlockMirrorEssentia`/`BlockMirrorEssentiaItem`/`TileMirrorEssentia`
  shipped, following `TileMirror`'s own `MirrorLink` pairing pattern
  exactly. See commit "Give the Essentia Mirror its real cross-dimensional
  relay" for the real mechanic (a pure `IAspectSource` pass-through that
  cube-scans near its linked partner, not an item-transport relay).

- **Golem Press ritual** (`GolemBuilder`'s real activation multiblock) —
  closed 2026-09-09. Real `golemPressBlueprint()` shipped in
  `ThaumcraftDustTriggers.java`, migrating `golemBuilder` off its
  now-stale-precedent "direct block, no ritual" doc comment onto the same
  real `DustTriggerMultiblock` pattern the Infernal Furnace and Thaumatorium
  were already migrated to. 4 new purely-decorative placeholder blocks
  (`placeholderBars`/`Cauldron`/`Anvil`/`Table`, matching the
  `placeholderNetherbrick`/`Obsidian` pattern, not the true original's own
  heavier `BlockPlaceholder`). See commit "Give the Golem Builder its real
  acquisition ritual".

- **Jar Brace** (`jarBrace`) — closed 2026-09-09. `TileJarFillable#isBlocked`
  was hardcoded `false` (its own doc comment flagged this exact gap). Real
  `applyBrace()` + `ItemsTC.jarBrace` (plain item, asset scaffolding already
  in the tree) shipped, wired through `BlockJar#useItemOn`. See commit "Give
  the Warded Jar a real jar brace".

- **Pattern Crafter** (`patternCrafter`) — closed 2026-09-09. Real
  `TilePatternCrafter`/`BlockPatternCrafter` shipped: a vis-powered
  auto-crafter using real adjacent-inventory lookups (NeoForge's
  `Capabilities.ItemHandler.BLOCK`) and real vanilla `CraftingRecipe`
  matching via `CraftingInput`. No CodeChicken sub-hit raytracing (a plain
  right-click cycles the pattern instead - the same dependency wall that
  already blocked the Levitator/Redstone Relay/Tube One-way); `ENABLED`
  redstone toggle replaces the original's bespoke wand-toggle. See commit
  "Give the Arcane Pattern Crafter a real vis-powered auto-crafting
  mechanic".

- **Tube Buffer** (`tubeBuffer`) — closed 2026-09-09. Real
  `TileTubeBuffer`/`BlockTubeBuffer` shipped: a real multi-aspect essentia
  store (up to 10 total units, any mix), not a `TileTube` subclass (a
  buffer's real suction is a small fixed value, not `TileTube`'s own
  dynamic neighbor-derived one). Deliberately weak base suction (1,
  matching the true original's own real formula and confirmed by the
  research text) means it only pulls from a neighbor with
  `getMinimumSuction() == 0` (a plain tube, not a jar) - by design. See
  commit "Give the Tube Buffer a real multi-aspect essentia store".

- **Essentia Transport Input/Output** (`essentiaTransportInput`/
  `essentiaTransportOutput`) — closed 2026-09-09. **Corrects an earlier
  scoping note in this same file** that assumed (from the true original's
  own dispenser/hopper-themed crafting recipe) that these devices interact
  with physical essentia-container items in an adjacent inventory - reading
  the true original's own real `EssentiaHandler` directly disproved that:
  both devices only ever scan for real `IAspectSource` **block entities**
  (jars, altars, etc.), the dispenser/hopper components are just thematic
  crafting flavor. Real long-range bridge instead: Input pulls 1 unit from
  a real tube-network neighbor and pushes it into the nearest real
  `IAspectSource` within 16 blocks (any direction); Output is the exact
  mirror. `EssentiaHandler.java` gained a real `addEssentia` counterpart to
  its existing `drainEssentia`, refactored to share one scan helper. See
  commit "Give the Essentia Input/Output devices their real long-range
  bridge mechanic".

- **Triple Meat Treat** (`tripleMeatTreat`) — closed 2026-09-09. **Corrects
  an earlier scoping note in this same file** that assumed a new generic
  vanilla-furnace smelt-event hook was needed - reading the true original's
  own `CommonInternals`/`ThaumcraftApi` directly showed the shared
  `smeltingBonus` list has exactly ONE real consumer anywhere in the whole
  original codebase: `TileInfernalFurnace`, the same device this port
  already has its own real (ore-scoped) smelting-bonus system on. No
  vanilla-furnace hook ever existed for this in the true original (and none
  exists in NeoForge 1.21.1 either, confirmed directly against the jar).
  6 new chunk items (`chunkBeef`/`Chicken`/`Pork`/`Fish`/`Rabbit`/`Mutton`)
  registered as real Infernal Furnace smelting bonuses (33% each); Triple
  Meat Treat's own recipe (3 distinct chunks, no duplicates, + sugar) is a
  real custom `CraftingRecipe` (`CustomRecipe`, same vanilla mechanism
  `RepairItemRecipe` uses), not a JSON shapeless recipe. See commit "Give
  Triple Meat Treat its real chunk family and custom crafting recipe".

- **Non-boss monster roster** — closed 2026-09-09. **Corrects an earlier
  "Deferred subsystems" scoping note in this same file** that estimated a
  whole multi-session arc (~2700+ lines) - a precise diff of the true
  original's entire `entities/monster/` tree against `build.gradle` found
  the roster (regular mobs and most bosses) already almost entirely built
  from earlier sessions this note hadn't caught up with. Only 2 real files
  were missing: `ChampionModDummy` (confirmed dead-in-the-true-original
  itself, no action needed) and `EntityTaintacleGiant` (the Taint faction's
  own boss - real `EntityTaintacle` subclass with its own hand-duplicated
  boss-bar/enrage/self-heal mechanic and a real death-drop dedup, `/summon`-
  reachable today though its true original's own real placement was
  worldgen-only, tied to the still-blocked Taint-corruption subsystem). See
  commit "Give the Taint faction its own boss - the Giant Taintacle".

- **AidGlyphedStone** — closed 2026-09-09. A pure registration gap, not new
  functionality - its own card (`CardGlyphs`) was already registered, and
  its blocking dependency (`stoneAncientGlyphed`) shipped earlier this same
  session as part of the Ancient/Eldritch stone family, without the two
  being connected at the time. See commit "Register AidGlyphedStone - its
  own real blocker shipped earlier this session".

- **GolemArmDart** (the "DARTS" golem arm's real ranged attack) — closed
  2026-09-09. The arm itself (components, RANGED/FIGHTER/CLUMSY/FRAGILE
  traits) was already registered; this wired in its real
  `GolemArm.IArmFunction` - a real vanilla `Arrow` projectile (not a new
  `EntityType`, since the true original's own `EntityGolemDart` was itself
  just a cosmetically-reskinned `EntityArrow`) plus vanilla's own
  `RangedAttackGoal` (the interface already expects that return type, no
  custom `Goal` needed) added to `EntityThaumcraftGolem#createAI` for
  RANGED-trait golems with a wired function. See commit "Give the golem
  Dart arm its real ranged attack".

- **Loot Urns** (`lootUrnCommon`/`lootUrnUncommon`/`lootUrnRare`) — closed
  2026-09-09, the last item in "Confirmed real gaps". Real `Material.ROCK`
  sibling of the already-shipped loot crates, sharing the same `BlockLoot`
  class and vanilla-loot-table-based drop mechanic (each tier nests
  `gameplay/loot_bag_<tier>`, same as the crates). Block models/textures for
  all three tiers already existed from earlier scaffolding; what was missing
  was the `BlocksTC`/`ItemsTC` registrations, 3 blockstates (still stale
  `forge_marker` format), 3 item models, and 3 loot tables. Real, documented
  simplification kept: one shared `VoxelShape` across crate and urn rather
  than the true original's own genuinely smaller urn hitbox.
  `SoundType.DECORATED_POT` used as a real vanilla substitute for the true
  original's own custom "urnbreak" sound (no modern equivalent exists in
  this port). Along the way, found and fixed a real pre-existing bug: loot
  crates and the crimson cult banner had only stale, dead `tile.X.name` lang
  keys and no real `block.thaumcraft.X` key, so all four rendered as raw
  untranslated keys in-game - fixed for all four. See commit "Give dungeon
  urns their real BlockLoot registration - loot_urn_common/uncommon/rare".

- **Crystal cluster connected-texture rendering** — closed 2026-09-09,
  removed from "Deferred subsystems". The old blocker note ("Forge's
  `IExtendedBlockState`/unlisted-properties baked-model pattern has no
  modern equivalent yet") was itself wrong - vanilla's own real
  `AmethystClusterBlock` already solves the identical "attach to and render
  from a single real face" need with nothing more exotic than a stored
  `DirectionProperty` plus per-facing blockstate model rotation, no custom
  baked model required. `BlockCrystal` now stores a real `FACING` property
  (set from the clicked face on placement, computed fresh per spread target
  during natural growth); `getShape`/`canSurvive` read it directly instead
  of live-rescanning all 6 neighbors. All 7 aspect blockstates converted
  from `multipart` to real `variants` keyed by `facing=<direction>`, using
  the exact rotation matrix read off vanilla's own `amethyst_cluster.json`.
  Real, still-open remaining cut: the true original's own bespoke OBJ
  crystal-cluster mesh (a genuinely faceted shape) isn't ported - this
  closed the real per-face ORIENTATION gap, not the mesh geometry itself
  (stays a simple oriented box, same "no bespoke mesh renderer yet" gap the
  advanced turret's OBJ mesh carries - a separate, smaller, non-blocking
  follow-up). See commit "Give crystal clusters real per-face orientation -
  resolves the crystal-cluster rendering soft blocker".

- **CLIMBER-trait golem pathfinding** — closed 2026-09-09, closing the
  entire golem construction pipeline arc. The earlier note assumed this
  needed the modern `NodeEvaluator` API investigated first - it didn't.
  The true original's own real CLIMBER-specific class, `PathNavigateClimber`,
  turned out to be genuinely absent from the whole pristine-original tree
  this port was decompiled from (referenced by `EntityThaumcraftGolem.java`
  but never actually present - confirmed via exhaustive search, not a
  porting loss since it was never there). Checked whether modern vanilla
  already solves the same "climb any wall you're pushed against" need
  rather than guessing at the missing class: it does, via `Spider`'s own
  real `WallClimberNavigation` + `onClimbable()` override pattern. This port
  already had the synced `isBesideClimbableBlock` flag (from an earlier pass
  this same session) but nothing ever read it back into real physics -
  `createGolemNavigation` now returns `WallClimberNavigation` for
  CLIMBER-trait golems, and a new `onClimbable()` override closes the loop.
  See commit "Give CLIMBER-trait golems their real wall-climbing
  pathfinding".

- **AidBasicAuromancy, AidBasicEldritch** — closed 2026-09-09. Both were
  the same real story as `AidGlyphedStone`: already-written aid classes,
  never registered or added to `build.gradle`, each pointing (in their
  stale, unconverted content) at a true-original field name
  (`wandWorkbench`/`eldritch`) that this port had already shipped under a
  different modern name (`focalManipulator`/`cultistRitualAltar`
  respectively) - nobody had connected the aid to its already-built block.
  Fixed the references, added both to `build.gradle`, registered both in
  `Thaumcraft.java`. Only `AidBrainInAJar` remains genuinely blocked now
  (needs the still-unported `jarBrain` block). See commit "Register
  AidBasicAuromancy and AidBasicEldritch - their blockers were already
  resolved".

- **Brain in a Jar** (`jarBrain`) — closed 2026-09-09, closing the entire
  Research Table "extra content" arc down to just `CardCelestial`. A real
  `TileJarBrain`/`BlockJarBrain` pair (split off from the plain essentia
  `BlockJar`) - passively pulls and consumes nearby real vanilla
  `ExperienceOrb`s within 8 blocks (capping at 2000 XP), right-click drains
  a real random amount back out as real orbs (a 40-tick cooldown stops it
  re-swallowing what it just spawned), real comparator output scaling with
  fill. The original's own client-only idle-animation and its
  `getEnchantPowerBonus() -> 5.0f` (no modern per-block equivalent - tagged
  into vanilla's own flat `minecraft:enchantment_power_provider` instead)
  are the only real cuts. `AidBrainInAJar` wired in alongside it. See
  commit "Give the Brain in a Jar its real XP-battery mechanic - closes the
  Research Table arc's last block-blocked aid".

---
*Generated 2026-09-09 via a full `BlocksTC.java`/`ItemsTC.java` field-name
sweep against the true original (commit `31baee9`). Re-running the sweep
from scratch isn't necessary for future work — update this file incrementally
as items close or new gaps are found instead.*
