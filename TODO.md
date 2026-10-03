# Thaumcraft 1.21.1 Port — Open Work

Only what is still open. Finished work, its reasoning, and everything found to be dead in the original or not a
gap is in `TODO_ARCHIVE.md` - check there before re-investigating something. Visual/feel checks that need the user
in a client are tracked in the `thaumcraft-ui-visual-parity-checklist` memory, not here.

## Code work

- Parity sweep of the "simplified"/"stand-in"/"cut" doc notes (list: `grep -iE "simplif|stand-in|cut|not ported"`
  over compiled classes): devices, mobs, drops and golems done 2026-10-03 (see archive). Left: foci and their
  entities, items (caster vis draw, armour, phial/alembic, resonator, void tools), research/scans, worldgen, GUIs
  (Focal Manipulator, focus pouch, hand mirror, seal config), client FX classes.
- **Next up - remove the port-invented fixed foci.** The original had no per-effect focus items, only the blank
  `focus_1/2/3` (15/25/50 complexity) built at the Focal Manipulator. The port's 12 fixed foci (`ItemFocusFire` as
  `focus_1`, Frost, Break, Fire Bolt, Heal, Curse, Earth, Fireball, Flux, Air, Trap, Sanctuary) with their arcane and
  crucible recipes, models and lang go; `focus_blank_1/2/3` become `focus_1/2/3` again (recipes `focus_1` crucible,
  `infusion_focus_2/3`; drop the aliases in `LegacyRecipeKeys`/`ResearchRequirements`; ItemsTC `focus1/2/3`).
  ~60 test references: casting tests build a FocusPackage from node lists; workbench tests move to the original
  banner recipe (10 vis, BASEINFUSION); Thaumatorium tests to `hedge_clay` (dirt catalyst, Aqua 5); pouch/caster
  tests use blank foci.
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
