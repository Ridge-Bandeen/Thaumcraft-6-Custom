# Thaumcraft 1.21.1 Port — Open Work

Only what is still open. Finished work, its reasoning, and everything found to be dead in the original or not a
gap is in `TODO_ARCHIVE.md` - check there before re-investigating something. Visual/feel checks that need the user
in a client are tracked in the `thaumcraft-ui-visual-parity-checklist` memory, not here.

## Code work

- Parity sweep of the "simplified"/"stand-in"/"cut" doc notes (list: `grep -iE "simplif|stand-in|cut|not ported"`
  over compiled classes): devices, mobs, drops and golems done 2026-10-03 (see archive). Left: foci and their
  entities, items (caster vis draw, armour, phial/alembic, resonator, void tools), research/scans, worldgen, GUIs
  (Focal Manipulator, focus pouch, hand mirror, seal config), client FX classes.
- **Next up - ThaumCompat hooks** (see `COMPAT_HOOKS.md`, port reply sections): batch 1 = #1 `ThaumcraftSetupEvent`,
  #2 crucible heater tag + `receiveExternalHeat`, #3 crucible-base trigger tag, #6 refining data map, #13 chest loot
  tables; then #5, #8, #9, #10; then #4, #7, #11, #12.
- Golem renderer fidelity: per-part model classes (wheel spin from `GolemLegWheels.ani`, claws, breakers, darts,
  hauler), head look, idle sway, arm pose when carrying, the base model's stub arms, per-group textures.
- Void Siphon's void streaks (the original's FXVoidStream, an ender-shader stream from each rift).
- Renderer fidelity: no full sweep yet of already-registered renderers for simplified detail/animation (the mob
  sweep only covered entities that had been invisible placeholders).
- Aspect table: entries for content the port hasn't built are skipped (ambient grass, the Pure bucket); the
  original's shield-banner and per-colour potion loops are only covered by the generic bonuses.

## Design questions for the user

(none open)

## Notes

- The Eldritch Warden and other bosses cap any hit at 35 and buff themselves on bigger hits (as the original), so
  `/kill` fails; use `/data merge entity <sel> {Health:0f}`.
- New source files must be added to `build.gradle`'s explicit include list or they silently don't compile.
- Python on Windows writes CRLF in text mode; open files with `newline=''` when editing them from scripts.
