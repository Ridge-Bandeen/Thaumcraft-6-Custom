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

- **Triple Meat Treat** (`tripleMeatTreat`) — **re-scoped, bigger than it
  looks; not actually a quick win.** The item itself is trivial (a custom
  `ItemFood`/`FoodProperties` — 6 hunger / 0.8 saturation, always edible,
  applies `Regeneration` 100 ticks amplifier 0 at 66% chance on eat), but its
  real crafting recipe (`RecipeTripleMeatTreat` — 3 *distinct* meat-chunk
  variants, no duplicates, + 1 sugar, shapeless-with-a-distinctness-
  constraint so needs a real custom `Recipe` class, not a JSON shapeless
  recipe) depends on a whole prerequisite item family that also doesn't
  exist yet: **`chunks`/`ItemChunksEdible`** — 6 real, distinct raw-meat-chunk
  items (beef/chicken/porkchop/fish/rabbit/mutton; 1 hunger/0.3 saturation,
  not always edible, real `nuggetMeat`-tag members), obtained via true
  original's **generic vanilla-furnace smelting-bonus mechanic**
  (`ThaumcraftApi.addSmeltingBonus(new ItemStack(Items.BEEF), ...)` etc. -
  smelting raw beef/chicken/porkchop/rawfish/rabbit/mutton in **any** vanilla
  furnace has a chance to also yield the matching chunk). **This is a
  materially different mechanism from the smelting-bonus system this port
  already has** (`TileInfernalFurnace`'s own private `SMELTING_BONUSES` -
  deliberately scoped to ore→nugget bonuses inside the Infernal Furnace
  only, per that class's own doc comment). The true original's meat-chunk
  bonus hooked vanilla furnace smelting generically, which this port has no
  equivalent for at all yet (no `FurnaceSmeltEvent`-equivalent hook exists).
  Real scope: (1) a new, first-of-its-kind generic "any vanilla furnace,
  chance of bonus drop" hook (NeoForge's actual smelt-event API needs
  confirming for 1.21.1 - check whether it's still `FurnaceSmeltEvent` or
  moved), (2) the 6 chunk items, (3) the 6 smelting-bonus registrations, (4)
  the custom distinct-3-chunks-plus-sugar recipe, (5) the treat item itself.
  Both stale reference files (`ItemTripleMeatTreat.java`,
  `RecipeTripleMeatTreat.java`, `ItemChunksEdible.java`, all still
  1.12.2-era unconverted source sitting inert in the tree, not in
  `build.gradle`'s compile-include list) are good line-for-line references
  for steps 2-5 once the new furnace hook exists.

- **Loot Urns** (`lootUrnCommon`/`lootUrnUncommon`/`lootUrnRare`). A real,
  distinct dungeon-decoration BLOCK type (`BlockLoot` with a `LootType`
  enum), separate from the already-shipped Loot Bag/Loot Crate items. True
  original: `new BlockLoot(Material.ROCK, "loot_urn_common", BlockLoot.LootType.COMMON)`
  (and Uncommon/Rare siblings). Likely low priority — dungeon/structure
  worldgen dressing, not a progression gate — but a real, unbuilt block.

- **Pattern Crafter** (`patternCrafter` / `BlockPatternCrafter` /
  `TilePatternCrafter`). A real crafting BlockEntity device in the true
  original with no equivalent anywhere in the modern port. Purpose not yet
  investigated this sweep — read `TilePatternCrafter.java` (true original,
  `git show 31baee9:src/main/java/thaumcraft/common/tiles/crafting/TilePatternCrafter.java`)
  before scoping.

- **Tube Buffer** (`tubeBuffer`). A `TileTube` family variant with no
  modern-port equivalent (confirmed by direct field-name grep against
  `BlocksTC.java` — zero hits). Status/complexity not yet investigated this
  sweep; check `TileTube`'s true-original buffer-mode handling before
  scoping. (Its sibling `tubeOneway` is a **known, already-diagnosed
  blocker** — needs unported per-face wand raytracing — not a fresh gap.)

- **Essentia Transport Input/Output** (`essentiaTransportInput`/
  `essentiaTransportOutput`). Already flagged as a known real gap in earlier
  session notes (not re-investigated this sweep beyond confirming the name
  diff still holds); carried forward here so it isn't lost.

## Needs further triage (real content exists in the original; scope/priority unconfirmed)

- **`effectShock`/`effectSap`/`effectGlimmer`** (`BlockEffect`) — likely
  transient, purely-cosmetic VFX placeholder blocks (placed briefly, removed
  automatically) rather than real player-facing content. Worth a quick read
  of `BlockEffect.java` (true original) to confirm before deciding whether
  to port or explicitly write off.
- **`empty`** (`BlockTranslucent`) — likely an invisible structural/render
  placeholder, not real content. Probably a write-off once confirmed.
- **`liquidDeath`** (`BlockFluidDeath`) — a real fluid block, but likely
  tangled with the already-known-blocked finite-fluid/Taint subsystem (per
  earlier session notes: "Taint/world-corruption (tangled with the blocked
  finite-fluid)"). Confirm it's the same blocker before treating as separate.
- **`grassAmbient`, `shimmerleaf`, `vishroom`, `bloom`** — worldgen
  plant/ambient blocks. Earlier session notes already confirmed
  `world/plants/` as worldgen-only and out of near-term scope; these are
  likely the same bucket. Low priority.
- **`fluxScrubber`, `taintDust`, `auraTotem`** — not pinned down precisely
  this sweep (didn't surface in the targeted `ConfigBlocks.java` greps used);
  need a follow-up direct read of the true original's block registration to
  confirm real vs. naming-mismatch false-positive before acting.

## Confirmed dead-in-the-true-original (do not port — matches nothing real)

- **`eldritchEye`, `runedTablet`** (items). Declared as public fields in the
  true original's own `ItemsTC.java` and referenced in `ConfigAspects.java`
  (object-tag registration) and `ConfigEntities.java` (a mob drop table) —
  but **never actually assigned/registered anywhere** in `ConfigItems.java`
  or any other true-original file (confirmed via exhaustive `git grep` for
  `ItemsTC.eldritchEye =` / `ItemsTC.runedTablet =` across the whole
  pristine-original tree — zero hits). These are vestigial/dead fields in
  the shipped original itself, not a porting gap. No action needed.
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

- Research Table minigame (~4700 lines total incl. 51-file card/aid deck) —
  real value, "many small bespoke pieces," not one clean slice.
- Non-boss monster roster (~2700+ lines) — would be this port's first real
  vanilla Goal-based AI port; zombie variants inside are trivial/peelable if
  someone wants a partial slice.
- Crystal cluster connected-texture rendering (`BlockCrystal`) — real soft
  blocker: Forge's `IExtendedBlockState`/unlisted-properties baked-model
  pattern has no modern equivalent yet.
- Golem construction pipeline — far bigger than it looks (~4000+ lines, 140
  files), own API layer (`GolemHelper`) is pure stub. Multi-session arc, not
  a quick chunk. (Note: the Golem Press *ritual* that used to unlock
  `golemBuilder` itself is now closed — see "Recently closed" below — this
  remaining item is the much bigger golem AI/behavior pipeline behind it.)
- Focal Manipulator — **closed**, full arc shipped (tile, block, menu,
  screen, craft button, graph-save packet, node-graph canvas). Listed here
  only so it isn't mistakenly re-opened by a future sweep.

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

---
*Generated 2026-09-09 via a full `BlocksTC.java`/`ItemsTC.java` field-name
sweep against the true original (commit `31baee9`). Re-running the sweep
from scratch isn't necessary for future work — update this file incrementally
as items close or new gaps are found instead.*
