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

- **Golem Press ritual** (`GolemBuilder`'s real activation multiblock).
  `BlocksTC.golemBuilder` and the `GOLEM_BUILDER` BlockEntityType already
  exist and are wired up, but the true original's real acquisition path — a
  `DustTriggerMultiblock` blueprint registered as
  `IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("MINDCLOCKWORK", golempressBlueprint))`
  plus a `ThaumcraftApi.addMultiblockRecipeToCatalog(new ResourceLocation("thaumcraft:GolemPress"), ...)`
  catalog entry — was never ported. This is the same well-established pattern
  already used for Salis Mundus / Infusion Altar conversion this session (see
  `ThaumcraftDustTriggers.java`), so it's a clean, self-contained chunk.
  Needs 4 new placeholder blocks first (`BlockPlaceholder`-style, matching the
  existing `placeholderNetherbrick`/`placeholderObsidian` pattern):
  `placeholderAnvil`, `placeholderBars`, `placeholderCauldron`,
  `placeholderTable` — each is a display-only stand-in for a vanilla block
  (`Blocks.ANVIL`/`Blocks.IRON_BARS`/`Blocks.CAULDRON`/`BlocksTC.tableStone`
  respectively) inside the multiblock structure. True-original blueprint
  shape (`ConfigRecipes.java:124-128`, true original):
  ```java
  final Part GP1 = new Part(Blocks.IRON_BARS, new ItemStack(BlocksTC.placeholderBars));
  final Part GP2 = new Part(Blocks.CAULDRON, new ItemStack(BlocksTC.placeholderCauldron));
  final Part GP4 = new Part(Blocks.ANVIL, new ItemStack(BlocksTC.placeholderAnvil));
  final Part GP5 = new Part(BlocksTC.tableStone, new ItemStack(BlocksTC.placeholderTable));
  final Part[][][] golempressBlueprint = { { { null, null }, { GP1, null } }, { { GP2, GP4 }, { GP3, GP5 } } };
  ```
  (GP3 not shown in the grep excerpt — re-read `ConfigRecipes.java` around
  line 120-128 in the true original for the full blueprint before building.)

- **Mirror of Essentia** (`mirrorEssentia`). `TileMirrorEssentia.java`
  already exists in the modern tree
  (`src/main/java/thaumcraft/common/tiles/devices/TileMirrorEssentia.java`)
  but is a fully orphaned file — no `BlocksTC.mirrorEssentia` block, no
  `BlockEntityType` registration, nothing consumes it. A real, distinct
  device from the already-shipped plain item-transport `Mirror`. Scope: wire
  up the block + BlockEntityType + blockstate/models/loot table/recipe, verify
  the tile's own logic still matches the true original's `TileMirrorEssentia`.

- **Grapple Gun components** (`jarBrace`, `grappleGunTip`, `grappleGunSpool`).
  The assembled `grappleGun` item exists in the modern port, but the true
  original crafts it from two intermediate arcane-crafted components that
  don't exist here at all:
  ```java
  // true original, ConfigRecipes.java:260-262
  GrappleGunTip:   "BRB"/"RHR"/"BRB", B=plateBrass, R=nugget(redstone), H=Blocks.TRIPWIRE_HOOK
  GrappleGunSpool: "SHS"/"SGS"/"SSS", G=mechanismSimple, S=string, H=Blocks.TRIPWIRE_HOOK
  GrappleGun:      "  S"/"TII"/" BW", B=plateBrass, I=plateIron, T=grappleGunTip, W=plankWood, S=grappleGunSpool
  ```
  Need to check whether the modern `grappleGun` recipe already collapses
  these two steps into one (likely, given the established metadata/step
  -consolidation convention elsewhere in this port) — if so this is a
  documentation-only close, not new code. **`jarBrace`** is unrelated to the
  grapple gun despite being found in the same sweep pass — it's a plain
  `ItemTCBase` tool item used directly against a placed `BlockJar`
  (`BlockJar.java:127,182` in the true original — right-clicking a fillable
  jar with it does something tool-like; read `BlockJar`'s true-original
  `onBlockActivated` around line 180 to confirm the exact behavior before
  building it). Crafted `"NSN"/"S S"/"NSN"`, N=nuggetBrass, S=stickWood.

- **Triple Meat Treat** (`tripleMeatTreat`). Simple, self-contained,
  low-effort real gap: a custom `ItemFood` (`ItemTripleMeatTreat` in the true
  original) — 6 hunger / 0.8 saturation, always edible, applies
  `Regeneration` (100 ticks, amplifier 0) at 66% chance on eat. No custom
  behavior beyond that (`getItem`/variant-mesh boilerplate is 1.12.2-only
  rendering plumbing, not needed in this port's item-model system). Good
  quick win whenever picking up small items.

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
  a quick chunk. (Note: the Golem Press *ritual* above is a much smaller,
  separable piece of this — the ritual itself doesn't require the full golem
  AI/behavior pipeline to be useful as a standalone chunk.)
- Focal Manipulator — **closed**, full arc shipped (tile, block, menu,
  screen, craft button, graph-save packet, node-graph canvas). Listed here
  only so it isn't mistakenly re-opened by a future sweep.

## Standing manual-verification item (not a code gap)

- **UI visual-parity checklist** (`thaumcraft-ui-visual-parity-checklist.md`
  in project memory) — a running list of client-rendering simplifications
  made throughout the port that need an actual `runClient` playtest to
  confirm they look/feel right. Not re-derived here; keep using that file.

---
*Generated 2026-09-09 via a full `BlocksTC.java`/`ItemsTC.java` field-name
sweep against the true original (commit `31baee9`). Re-running the sweep
from scratch isn't necessary for future work — update this file incrementally
as items close or new gaps are found instead.*
