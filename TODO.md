# Thaumcraft 1.21.1 Port — Open Work

Only what is still open. Finished work, its reasoning, and everything found to be dead in the original or not a
gap is in `TODO_ARCHIVE.md` - check there before re-investigating something. Visual/feel checks that need the user
in a client are tracked in the `thaumcraft-ui-visual-parity-checklist` memory, not here.

## Code work

- Parity sweep of the "simplified"/"stand-in"/"cut" doc notes (list: `grep -iE "simplif|stand-in|cut|not ported"`
  over compiled classes): done (see archive).
- ThaumCompat hooks: all of `COMPAT_HOOKS.md` (#1-#23, the setup-event delivery fix, undead tags, Nitor) done 2026-10-05; watch that
  file for new requests.
- Renderer fidelity: the noted list and a first sweep of the rest are done (see archive). Still to compare: GUI
  screens and the remaining client FX classes not yet checked against their originals.

## Design questions for the user

(none open)

## Notes

- The Eldritch Warden and other bosses cap any hit at 35 and buff themselves on bigger hits (as the original), so
  `/kill` fails; use `/data merge entity <sel> {Health:0f}`.
- New source files must be added to `build.gradle`'s explicit include list or they silently don't compile.
- Python on Windows writes CRLF in text mode; open files with `newline=''` when editing them from scripts.
