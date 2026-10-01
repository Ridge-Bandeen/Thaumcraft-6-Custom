# Thaumcraft 1.21.1 Port — Open Work

Only what is still open. Finished work, its reasoning, and everything found to be dead in the original or not a
gap is in `TODO_ARCHIVE.md` - check there before re-investigating something. Visual/feel checks that need the user
in a client are tracked in the `thaumcraft-ui-visual-parity-checklist` memory, not here.

## Code work

- Infusion Matrix: none of the original's client effects during infusion (arcs to pedestals/zapped entities, the
  essentia streams and sounds); the FX classes (`FXArc`, ...) exist now.
- Infusion recipes: the port's `InfusionRecipe` only outputs a plain stack; the original also produced NBT-tag,
  enchantment and XP-cost outputs - check every original infusion recipe still works.
- Parity sweep: ~50 compiled block/tile classes still describe "simplified"/"stand-in"/"cut" behaviour in their doc
  comments (many stale - the Alchemical Furnace screen, the pedestal renderer and casters all exist now). Check each
  against the original and restore or correct; the standard is full parity (see the parity memory).
- Crucible: the original filled from any fluid container holding a bucket of water (FluidUtil); the port accepts only
  the water bucket.
- The original's potion post-shaders (Death Gaze desaturate, Blurred Vision blur, Unnatural Hunger, Sun Scorned) and
  the warp fog packets aren't ported; `disableShaders` would gate them too.
- Audit the remaining stub `.mtl` files (golem_*) for the missing-`map_Kd` problem once golems are played.
- Verify other `getNearestPlayer`-style 1.12 -> 1.21 API traps (`getClosest*`, `canAttack`) in files not yet playtested.
- `FXGeneric` defaults gravity to 1.0; the original's nitor particles hover, so check other default-gravity effects
  when playtesting particles.
- Pech: the original's small held-item offset (`LayerHeldItemPech`, bows especially) isn't reproduced.
- `AuraThread.processAuraChunk` reads a chunk's vis/flux, works, then writes back off the server thread without
  locking, so a server-side change in between is lost. Same design as the original; rare, small.
- Renderer fidelity: no full sweep yet of already-registered renderers for simplified detail/animation (the mob
  sweep only covered entities that had been invisible placeholders).
- Aspect table: entries for content the port hasn't built are skipped (ambient grass, the Pure bucket); the
  original's shield-banner and per-colour potion loops are only covered by the generic bonuses.

## Design questions for the user


- Should vanilla candles be usable where Thaumcraft recipes want Thaumcraft candles (as glass bottles stand in for
  phials)?

## Notes

- The Eldritch Warden and other bosses cap any hit at 35 and buff themselves on bigger hits (as the original), so
  `/kill` fails; use `/data merge entity <sel> {Health:0f}`.
- New source files must be added to `build.gradle`'s explicit include list or they silently don't compile.
- Python on Windows writes CRLF in text mode; open files with `newline=''` when editing them from scripts.
