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

~~**Real crash-risk bug fixed 2026-09-16**: `EntityTaintacleGiant` (the Taint
faction's own boss, real/complete/`/summon`-reachable) had zero entity
renderer registered at all~~ - found while scoping the Custom Mob
Models/Renderers layer (cross-checked every real registered `EntityType`
against `ThaumcraftClient`'s own `registerEntityRenderer` calls, one at a
time, rather than assuming the list was complete). Not just cosmetic - the
first time any client ever saw or summoned one, NeoForge would have thrown a
real "no renderer registered for this entity type" error. Fixed by reusing
`RenderTaintacle` (the class already extends `EntityTaintacle`, so it's real,
type-compatible) with the true original's own real bigger shadow/chain-length
values (`1.0f, 14`, vs `TAINTACLE`'s `0.6f, 10`). Worth periodically re-running
this same registered-type-vs-registered-renderer cross-check as new entities
get added, since nothing else currently catches this class of gap
automatically (GameTest never spawns a real client to hit it).

**Full parity audit, 2026-09-15 evening**: user asked for 3 point-form lists
(this port's features / true original's features via code+wiki / the player-
facing diff between them), then to fix every real difference found except
Baubles - including the client-only layer (custom mob renderers/models,
particle FX, custom sounds), a boundary this whole port had deliberately
deferred until now. Real findings from that audit, triaged:

- **Not actually gaps** (re-checked directly, closing the loop the earlier
  "not yet individually re-verified" caveat left open): `EntityGolemDart`
  (real modern substitute already - `GolemArmDart` fires a plain vanilla
  `Arrow`), `EntitySpecialItem` (real modern substitute already - the only
  real/compiled original call site, `TileCrucible`, already spits a plain
  vanilla `ItemEntity`; its other 2 real call sites live inside
  `EntityUtils`/`ServerEvents`, both still correctly-unported separately),
  `EntityFallingTaint` (real modern substitute already - `BlockTaint#tryToFall`
  already uses vanilla `FallingBlockEntity.fall`).
- **Confirmed dead-in-the-true-original** (new): `EntityHomingShard`,
  `EntityRiftBlast` - both have real registered renderers in the true
  original but zero real `new EntityHomingShard`/`new EntityRiftBlast` call
  sites anywhere in the whole pristine-original tree. Move to "Confirmed
  dead" once this audit closes.
- ~~**`BlockTaintFibre`'s fortune-scaled drop chance**~~ — closed 2026-09-15.
  Now reads the real breaking player's own Fortune level via
  `EnchantmentHelper#getItemEnchantmentLevel` (`rand.nextInt(5) <= fortune`,
  matching the true original exactly) instead of a flat 20%.
- ~~**`TileTubeBuffer`'s bellows-amplified suction + real 3-state choke
  throttle**~~ — closed 2026-09-15, both together (the throttle's own first
  2 states only became meaningfully distinct once bellows amplification was
  real again).
- **Confirmed genuinely impossible, not a gap**: Vis-Exhaust's real milk-
  immunity - modern milk unconditionally clears every active effect, and
  `MobEffectInstance`'s own per-effect curative-item list was removed from
  the engine entirely (confirmed via a real compile error against
  `getCurativeItems()`, not assumed) - already correctly documented
  elsewhere in this port (`BlockFluxGoo`'s own doc comment).
- ~~**`FocusEffectExchange`/`FocusMediumPlan`**~~ — closed 2026-09-15. The
  real mass block-swap focus pair, plus `ItemCaster`'s own real block-picking
  (sneak-click) restored alongside it. Found and fixed a real, separate
  regression along the way: `BlockScheduler#tick` was never actually wired
  into any real production event - `FocusEffectBreak`'s own already-shipped
  gradual break has been silently non-functional in real live gameplay this
  whole time (only ever exercised by tests calling `tick()` directly). Now
  wired into a real `LevelTickEvent.Post` subscriber. Committed `5a6ebb6`.
- ~~**`TileStabilizer`'s real charge-network instability mitigation**~~ —
  closed 2026-09-15. Real self-charging energy meter, real Flux Rift
  pacification, and the real `TileInfusionMatrix` ejection redirect all
  restored - a plain BFS through connected `BlockInlay` positions stands in
  for the true original's own persistent charge-gradient blockstate search
  (same real reachability result, no player-visible difference). Committed
  `0ad4d7a`.
- **Client-only layer** (custom mob renderers/models, particle FX, custom
  sounds) - tracked separately given the real scale difference (~120 files):
  - ~~**Custom sounds**~~ — closed 2026-09-15, in two commits. First, `1575529`
    registered all 65 real `SoundEvent`s (`ThaumcraftSounds`, replacing the
    stale `SoundsTC`) plus 4 real `SoundType`s (`GORE`/`CRYSTAL`/`JAR`/`URN`,
    built via NeoForge's `DeferredSoundType` since a `SoundType` needs its
    `SoundEvent`s before `SOUND_EVENT`'s own `RegisterEvent` fires) - also
    fixed a real pre-existing `sounds.json` typo (`runicshieldecharge` →
    `runicshieldcharge`). Then `de14106` rewired every real call site that had
    been standing in with a vanilla sound (19 direct `playSound`/
    `getAmbientSound`-family call sites across blocks/entities/items/tiles/
    menus, plus the 4 `SoundType`s onto their real block registrations in
    `BlocksTC`), each cross-checked against the true original's own
    `SoundsTC.*` assignment. Found 2 real, previously undocumented gaps along
    the way: `BlockJar`'s fill/lock/label actions were on generic vanilla
    bottle/tripwire sounds instead of the original's own jar/key cues
    (including a real dual-sound dump case), and `TileFocalManipulator`'s
    fail/finish/start cues were on the wrong vanilla substitutes entirely.
  - **Particle FX** (`FXDispatcher`/`client/fx/*`) - in progress. Scoping found
    this codebase already has real, mostly-unused scaffolding: a
    `common/lib/network/fx` packet package (16 `PacketFX*` classes, only 3
    wired up before this chunk) + `client/fx/ClientFxHandlers` (client-side
    receiver, plain-vanilla-particle placeholders). Real approach: extend that
    pattern with real custom `Particle` subclasses (confirmed via the pinned
    1.21.1 decompiled sources, not version-skewed docs: `Particle#render` is
    abstract, fully custom multi-quad geometry is natively supported, no
    `RenderLevelStageEvent`/vanilla-`ParticleType` registry needed for these
    server-triggered cosmetic broadcasts).
    - **Beams family sanity-checked first**: `FXBeamWand`/`FXBeamBore` are
      confirmed dead code in the true original itself (zero real call sites
      anywhere, including via the Arcane Bore - which really uses
      `FXBoreSparkle`/`FXBoreParticles`/`FXBoreStream` instead - and every
      real wand focus medium, which only Touch/`FXFocusEffect` and
      Bolt/`FXBolt` actually dispatch anything for) - won't be ported.
    - ~~**`FXArc`**~~ — closed 2026-09-15. Real modern `Particle` subclass
      restored (`thaumcraft.client.fx.beams.FXArc`, a per-segment quad-ribbon
      reformulation of the original's own raw-GL double-plane cross-billboard,
      additively blended, bound directly to the real `beamh.png` texture via a
      dedicated custom `ParticleRenderType`). Wired to 4 real trigger points:
      `EntityEldritchGolem`'s headless-phase arc-lightning decoration
      (client-only, no packet), `EntityCultistPortalGreater`'s stage-0 pulse,
      `EntityEldritchWarden`'s field-frenzy ring pulse, `TileInfusionMatrix`'s
      instability-event zap/eject feedback (packet-driven via the newly
      modernized `PacketFXBlockArc`) - none of these 3 packet call sites were
      ever previously self-documented as gaps, only found by cross-referencing
      the true original directly. Needs a human `runClient` visual check - see
      the UI visual-parity checklist memory's own new section.
    - ~~**`FXBolt`**~~ — closed 2026-09-15. Real modern `Particle` subclass
      restored (`thaumcraft.client.fx.beams.FXBolt`) - checked for an easier
      path than a true tube mesh first (per the user's own "check the easy
      way before the hard way" instruction) and found real precedent:
      Electroblob's Wizardry's own `ParticleLightning` (a well-regarded
      modded-Minecraft lightning-spell VFX reference) renders its own bolts as
      layered flat "sheared box" strips, not true cylinders, since a thin
      additively-blended bolt doesn't read as flat at normal viewing
      distances. Reused `FXArc`'s own real cross-billboard quad-ribbon
      technique instead, called twice (full width, then 1/3 width,
      additively overlapping for a brighter core - matches the true
      original's own real "2 nested cones" structure), with the real jitter
      recomputed fresh every render call (growing amplitude, matching the
      true original's own real animated "intensifying crackle" look) rather
      than fixed at spawn like `FXArc`. Wired to `EntityWisp`'s zap (a real
      `PacketFXWispZap` restored, replacing this port's own earlier
      documented `PacketFXSlash` reuse - not worth a second packet class
      while there was no real bolt renderer to show, worth it now) and
      `FocusMediumBolt`'s cast trail (a real `PacketFXZap` restored, plus its
      own `execute()` fully implemented for the first time - was a bare
      `return true;` stub before). Also picked up 2 more real, previously
      undocumented sound gaps in the same pass: `EntityWisp`'s own
      `zap`/`wisplive`/`wispdead` (the doc comment's "whole unregistered
      subsystem" claim was stale, left over from before the Sounds chunk).
    - ~~**`FXGeneric`**~~ — closed 2026-09-15, its own dedicated chunk given
      how many other effects depend on it. Real modern `SingleQuadParticle`
      subclass restored (`thaumcraft.client.fx.particles.FXGeneric`) - reused
      vanilla's own real camera-facing billboard rendering rather than
      reimplementing it (every real `FXGeneric` particle is a single flat
      quad, unlike `FXArc`/`FXBolt`'s own multi-segment ribbons), only
      overriding the real grid-frame UV lookup and quad size. Binds the true
      original's own real shared particle sheet directly (`particles.png`, a
      1024x1024 grid already sitting unused in the resource tree - same
      "assets ready, only Java missing" shape `Sounds`/the beam textures had).
      Ported the real fluent-setter API closely (`setMaxAge`/`setParticles`/
      `setScale`/`setAlphaF`/`setRBGColorF`/`setLoop`/`setGravity`/
      `setSlowDown`/`setRandomMovementScale`/`setRotationSpeed`/`setWind`
      /etc. - even the real moon-phase-driven pseudo-wind direction formula),
      keyframed alpha/scale interpolation over the real particle lifetime, and
      a real minimal delayed-spawn queue (`ClientFxHandlers#addDelayed`,
      replacing the true original's own `ParticleEngine.addEffectWithDelay`).
      Real, documented simplification: the true original's own "angled" fixed-
      orientation mode (`setAngles`, ground-decal-style particles) isn't
      supported yet - needs a full `render()` override bypassing
      `SingleQuadParticle`'s own camera-facing math, deferred until a real
      effect that actually needs it gets restored.
      - Wired to 2 real trigger points immediately: `ToolEvents`' ore-scan
        reveal (`ClientFxHandlers#handleScanSource`/`markGroup`, now a real
        looping glow marker instead of vanilla dust, including the real
        distance-staggered "sonar ping" delay) and `FXArc`'s own trailing
        ember spawn at every jittered waypoint (both previously documented,
        deliberate simplifications, now closed).
    - ~~**`FXBoreSparkle`/`FXBoreParticles`**~~ — closed 2026-09-16, the
      Arcane Bore's real digging particle burst (was never self-documented as
      a gap before, only found by cross-checking the true original directly).
      `FXBoreSparkle` extends `SingleQuadParticle` (same "easy way" as
      `FXGeneric` - a single flat quad, only the grid UV needed overriding),
      a small homing spark on the shared `particles.png` sheet. `FXBoreParticles`
      extends vanilla's own real `TerrainParticle` directly - it already does
      everything the true original's own version hand-rolled (real per-block
      particle-icon lookup, tinting, UV jitter), so only the real homing
      motion (identical formula between the two classes, matching the true
      original's own real code duplication) needed a real override. Wired via
      a real modernized `PacketFXBoreDig` (entity-id-resolved, matching
      `PacketFXSlash`'s established convention) and the new `addDelayed`
      queue for the real 50-particles-over-N-ticks stagger.
      - **`FXBeamBore`'s own sibling, `FXBoreStream`, confirmed dead code**
        while scoping this: `boreTrailFx` (its only real dispatch method) has
        zero real call sites anywhere in the true original, same "declared,
        never called" pattern as `FXBeamWand`/`FXBeamBore` - won't be ported.
      - `FXFocusEffect` turned out not to be a real class at all - the true
        original's own `PacketFXFocusEffect` calls each individual real
        `FocusEffect` subclass's own `renderParticleFX` override directly, so
        "restoring" it really means wiring real particle rendering into every
        already-ported `FocusEffect` (~15-20 real subclasses), not building
        one shared class - a real, bigger, more diffuse piece of work, tracked
        separately rather than folded into this chunk.
    - ~~**`FXSwarm`**~~ — closed 2026-09-16. `EntityTaintSwarm`'s own real
      "buzzing insect cloud" visual - genuinely undocumented before (this
      entity is otherwise invisible/hitbox-only, so the true original's own
      visible swarm IS entirely these particles - `RenderFocusProjectile`,
      its current placeholder renderer, draws nothing). No packet needed at
      all - the true original kept its own `ArrayList<FXSwarm>` directly on
      the entity (safe in 1.12.2's looser common/client split); this port
      keeps the same list in `ClientFxHandlers` instead, keyed by entity id,
      since a modern common-side entity class can't carry a client-only field
      type. Real steering-toward-target flight (clamped yaw/pitch turn-rate,
      not a simple homing vector like `FXBoreSparkle`), real 50-tick death
      fade, real shared 3-buzz sound cooldown, all faithfully reproduced. Also
      restored the entity's own real `swarm`/`swarmattack` sounds (were
      vanilla bee-sound stand-ins).
      - ~~**`FXSonic`**~~ — closed 2026-09-17, built to the detailed scoping
        notes logged the session before rather than re-deriving anything
        blind. Real, genuine architectural finding confirmed while building
        (not just scoped): the true original's own `FXSonic` "target" field
        is actually the SCREAMING MOB ITSELF, not the player being screamed
        at - `PacketFXSonic`'s own `source` is `getEntityId()` called from
        within the mob's own `attackEntityWithRangedAttack`, checked directly
        against `master`. That fully validated the planned fix: drawn
        directly from `RenderEldritchGuardian`/`RenderEldritchWarden`'s own
        `render()` overrides (both call `EldritchSonicFx.render(...)`, a new
        shared static helper - both mobs' own melee-range "scream" fallback
        already applies the real Wither/Warp/knockback gameplay, unchanged),
        rather than a separate tracked `Particle`. Real vanilla entity-event
        trigger (id 17, same real pattern established for the cultist
        portals/turret crossbow) replaces `PacketFXSonic` - freezes the
        mob's own real head yaw/pitch at the moment it fires (matching the
        true original's own real `Particle` constructor capturing
        `target.getRotationYawHead()`/`rotationPitch` ONCE, not every frame)
        while position keeps tracking live. `hemis.mtl` authored as the same
        synthetic stub `crossbow_advanced.mtl`/`golembuilder_override.mtl`
        already established. 15 separate `CompositeRenderable` instances
        (one per real `ripple<N>.png` frame, confirmed present) baked lazily
        on first use rather than all 15 eagerly at construction - a real
        difference from the scoping note's original plan, but a strict
        improvement (avoids ever baking a frame a given play session never
        actually reaches). Real, disclosed simplification: the true
        original's own 50%-grey vertex dim is dropped -
        `CompositeRenderable`'s own real `Mesh#render` hardcodes full
        white/opaque per vertex with no exposed tint parameter, confirmed
        directly in its own real decompiled source this time (not just
        assumed from the crossbow precedent). Two clean 501/501
        `runGameTestServer` passes and a clean `test` task.
    - Scoped 2026-09-17: `FXDigging`/`FXSwarmRunes`/`FXGenericP2P`/
      **`FXGenericP2E`** (added to the dead list after checking its own
      real call site directly - `FXDispatcher.drawWispyMotesEntity` itself
      has zero real callers anywhere in the pristine tree, not just
      `FXGenericP2E` itself) confirmed **dead in the true original itself**
      (zero real construction sites anywhere, checked via `git grep` - none
      of these 4 will be ported). ~~`FXEssentiaStream`~~ — closed
      2026-09-17, see the closing note further below.
      **`FXPlane`'s own one real call site** (the ARCING infusion
      enchantment's cleave slash, via `PacketFXSlash`) already has a
      working, documented vanilla-particle-burst substitute in place
      (`ClientFxHandlers#handleSlash`) - a real, deliberate, already-
      accepted simplification, not an open gap; re-visiting it for the
      real textured slash-plane visual is a possible future upgrade, not a
      blocker.
      - ~~**`FXBlockRunes`/`FXBlockRunes2`**~~ — closed 2026-09-17. A real
        fixed-orientation quad painted flush against a block face (NOT a
        camera-billboard), built as a custom `Particle#render()` override
        (same real shape `FXEldritchOrbSpikes`/`FXCultistPortalLesser`
        already established) rather than `SingleQuadParticle`. Wired to 3
        of its 4 real call sites: `TileInfusionMatrix`'s own real crafting
        rune (the `crafting` field was already real-synced, no new sync
        wiring needed this time), `ItemThaumometer`'s own real scan effect
        (both the entity-target and block-target real interaction hooks),
        and `BlockPavingStoneBarrier`'s own real 3-way ambient warding-
        state cue (powered/dormant vs. barrier-damaged vs. a real living
        non-player intruder detected, via a new `animateTick` override -
        `TileBarrierStone` already exposed every real check needed:
        `gettingPower()`, the real `BlocksTC.barrier` presence check). The
        4th real call site, `BlockPedestal`'s own real offering rune, is
        genuinely blocked, not just unbuilt: it reads a `BlockInlay.CHARGE`
        blockstate value this port's own `BlockInlay` deliberately doesn't
        track at all (a real, already-documented, separate decision -
        the original's whole redstone-wire-style charge-propagation
        network was dropped as cosmetic-only when `BlockInlay` was first
        ported, well before this chunk). Two clean 500/500
        `runGameTestServer` passes and a clean `test` task.
      - ~~**`FXBreakingFade`**~~ — closed 2026-09-17, all real reachable call
        sites now wired (2 in the first pass, 2 more right after). Real,
        checked-not-guessed finding: modern vanilla's own real equivalent
        of the true original's own real `ParticleBreaking` base is
        `BreakingItemParticle` (a real 4x4 random-sub-quadrant sampling of
        an item's own particle icon, confirmed via its own real decompiled
        source) - but its own real constructors are `protected` and
        unreachable from a mod, so this replicates its own real sprite-
        lookup/sub-quadrant-UV technique directly in a new public class
        instead, reusing vanilla's own real `ParticleRenderType#TERRAIN_SHEET`
        bucket (the item icon lives in the block atlas already - no new
        texture binding needed). Wired to `EntityTaintacle`'s own real
        "arising from the ground" cue, `EntityThaumicSlime`'s own real
        landing-splat cue (a new `wasOnGroundLastTick` field drives the
        real "just touched down" edge detection), and `EntityBottleTaint`'s
        own real `taintsplosionFX`/`bottleTaintBreak` pair (a real vanilla
        entity-event trigger, id 3, matching the true original's own real
        `world.setEntityState` call exactly - the `bottleTaintBreak` half
        never needed a custom particle class at all, just plain vanilla
        item-crack particles + a splash sound). `ChampionModInfested`'s own
        real "gore" sound cue is restored too (a real, separate, previously
        stale doc-comment claim corrected along the way - the sound
        registry this cue needed has existed since the "Custom sounds"
        chunk, this call site just hadn't been revisited) - its own real
        `showFX` particle flourish stays dropped, since that's a real cut
        at the shared `IChampionModifierEffect` interface level (every
        champion-mod effect implements it), a bigger, separate decision
        from this chunk's own scope, not a simple missing call. Real,
        checked-not-assumed finding: `EntityFallingTaint` (a real 5th
        `FXBreakingFade` caller in the true original, `taintLandFX`) is a
        stale, unbuilt 1.12.2 leftover in this port's own tree, not the
        real class - this port already replaced that whole mechanic with
        vanilla's own `FallingBlockEntity`, confirmed via an earlier
        full-tree audit this session (`BlockTaint#tryToFall`). Its own real
        6th call site, `splooshFX`, stays dead (zero real callers). Two
        clean 500/500 `runGameTestServer` passes and a clean `test` task,
        both passes.
        ~~`EntityFallingTaint`'s own real landing-FX equivalent~~ — closed
        2026-09-24, a separate later chunk. Real architectural-fork question
        put to the user (per the autonomy memory's own stated exception -
        "the main place I want input is where we plan to intentionally
        deviate from the 1.12 version"); user chose "reintroduce a custom
        entity" over a hook-free polling workaround or leaving it un-
        restored. Investigation then found a cleaner real modern extension
        point than a full custom `FallingBlockEntity` subclass would have
        needed: decompiling vanilla's own real `FallingBlockEntity#tick()`
        (via the NeoForm decompile cache, not guessed) showed it already
        calls a real `Fallable#onLand(Level, BlockPos, BlockState,
        BlockState, FallingBlockEntity)` hook the instant it successfully
        places itself back down - a real, purpose-built modern extension
        point for exactly this "a falling block just landed" case, which
        didn't exist when the original custom-entity-to-vanilla-
        `FallingBlockEntity` simplification was first made. `BlockTaint` now
        implements `Fallable` directly; `onLand` plays the real `gore` sound
        and broadcasts a new `PacketFXTaintLand` (client resolves into 10x
        `FXBreakingFade` dark-tinted splat particles, matching the true
        original's own real `taintLandFX` loop exactly) - same real visual/
        audio cue as the true original, zero new entity class needed, and
        vanilla's own already-correct falling-block physics/rendering stay
        untouched. Two clean 499/499 `runGameTestServer` passes and a clean
        `test` task.
      - **`FXGenericGui`** - real research pass completed 2026-09-24 (a full
        `Explore`-agent read of the true original source, not guessed) -
        turned out to be a MUCH bigger real gap than "port one particle
        class." Real findings, 3 separate layers:
        - The core 2D screen-space render technique is now understood and
          unblocked: the true original's own real `ParticleEngine` renders
          "GUI-layer" (4-5) particles from a full-screen orthographic pass
          that runs every frame regardless of which screen (if any) is
          open - modern NeoForge's own real `RenderGuiEvent.Post` (already
          used by this port's own `FocusRadialOverlay`/
          `SanityCheckerOverlay`) is a faithful equivalent, already
          confirmed to fire while a modal screen is open too. Drawing a
          real tinted, additively-blended textured quad in that space needs
          a manual `Tesselator`/`BufferBuilder` draw (no existing
          precedent in this port does a *tinted* `GuiGraphics` blit or GUI-
          space additive blending yet) - mechanically the same technique
          `FXGeneric`'s own already-real `ParticleRenderType` already uses
          for its in-world case, just swapping the pose stack source.
        - **`GuiResearchTable`'s own real call site is blocked on missing
          prerequisite UI, not just missing FX**: the true original's own
          real sparkle trigger is tied to a real "research-percentage
          sidebar, counting up live" feature (`tempCatTotals`/`sparkle`
          list in the true original's own `GuiResearchTable`) that this
          port's own already-ported `GuiResearchTable` never built at all -
          confirmed via grep, zero hits for `sparkle`/`categoriesBlocked`/
          `penaltyStart` in the current file, which is a documented,
          deliberate simplified rewrite (no item-cost icons, no per-card
          hover text, no scrollbar). Restoring the sparkle here means
          restoring that whole percentage-sidebar feature first - real,
          separate, larger UI-feature scope, not a quick FX follow-on.
        - ~~**`HudHandler`'s own real "knowledge gained" HUD notification**~~
          — closed 2026-09-24. Confirmed, not assumed: `PacketKnowledgeGain`
          and the `HudHandler` it targeted were both stale, unported 1.12.2
          copies (old `IMessage`/`EntityPlayer`/`SideOnly` imports); the real
          modern knowledge-gain entry point,
          `ThaumcraftInternalMethodHandler#addKnowledge`, sent NO client
          notification of any kind before this chunk - no packet, no sound,
          no HUD cue, a real complete feature gap, not a simplification
          anyone chose. Restored as a new `KnowledgeGainOverlay` class
          (matching the `FocusRadialOverlay`/`SanityCheckerOverlay` sibling
          pattern): a modern `PacketKnowledgeGain` `CustomPacketPayload`,
          fired once PER POINT of real post-clamp knowledge delta (matching
          the true original's own real `ResearchManager#addKnowledge` loop,
          not once per call - `addKnowledge` now computes a real before/
          after delta and loops), and the real multi-phase icon animation
          (fly-in/settle/shrink-toward-the-book-icon, birth/death glow-burst
          flashes, category icon overlay, book-icon fade) ported faithfully
          from `HudHandler#renderKnowledgeGains`'s own real formulas. Built
          a real new shared "tinted, optionally-additive 2D screen-space
          quad via a manual `Tesselator`/`BufferBuilder` draw against the
          `GuiGraphics` pose stack" technique along the way - directly
          reusable for `FXGenericGui`'s own real particle system whenever
          that gets built. Real, separate, still-NOT-built gap: the true
          original's own real sparkle-particle tie-in (`current.sparks`,
          `drawSimpleSparkleGui`) isn't wired - that's `FXGenericGui`'s own
          real particle system itself, not this notification feature; the
          `sparks` field is carried faithfully but currently always false.
          Two clean 499/499 `runGameTestServer` passes and a clean `test`
          task.
        ~~**`GuiResearchTable`'s own real percentage-sidebar half**~~ —
        closed 2026-09-24, same session, right after the mob models/HUD
        notification. The initial "762 vs. 123 lines, `FLUX_RIFT`-scale"
        framing was about the true original's own real WHOLE screen (card
        hover/zoom-in/zoom-out animation state per card, a real dedicated
        Galactic font renderer for card flavor text, real gilded-paper vs.
        plain-paper card textures) - once actually checked directly, the
        percentage sidebar specifically turned out to be a MUCH smaller,
        self-contained gap: this port's own real `ResearchTableData`
        already fully tracked `categoryTotals`/`penaltyStart`/
        `categoriesBlocked` server-side and synced it via
        `ClientResearchTableCache` well before this chunk - purely a
        missing RENDERING feature, not a missing mechanic. Restored a real
        client-local smoothly-animated `tempCatTotals` copy (1%/tick count-
        up/down toward the real synced value, throttled to once per real
        game tick, matching the true original's own real `tempCatTotals`/
        `nexCatCheck` shape exactly) plus a real sorted icon+percentage list
        positioned off the right edge of the shared 255-wide GUI panel
        (real hover tooltips, real gold/white/muted-red color coding past
        `penaltyStart`/`categoriesBlocked`, matching the true original's own
        real layout and formulas). Real, disclosed simplification, matching
        `KnowledgeGainOverlay`'s own identical gap: the true original's own
        real sparkle-particle trail tied to this same live-counting
        animation isn't wired - that's `FXGenericGui`'s own real particle
        system, still separately-scoped future work, not something either
        of this session's 2 prerequisite chunks needed to include. Two
        clean 499/499 `runGameTestServer` passes and a clean `test` task.
        **This closes every previously-identified open item in the whole
        port except `FXGenericGui`'s own real particle/sparkle system
        itself** - both of its former prerequisite blockers are now done.
      - ~~**`TileInfusionMatrix`'s "pedestal items flying into the matrix
        during a craft" particle visual**~~ — closed 2026-09-24. Found
        2026-09-17 while scoping `FXEssentiaStream`, genuinely unrelated to
        essentia tube flow despite the superficially similar name. New
        `PacketFXInfusionSource` (source pedestal pos, target matrix pos,
        the consumed `ItemStack`) broadcast from `TileInfusionMatrix
        #craftCycle`'s own real ingredient-consumption branch on every
        pedestal→matrix transfer. Client dispatch
        (`ClientFxHandlers#handleInfusionSource`) matches the true
        original's own real weighting exactly: 1-in-3 chance of a
        `FXBoreSparkle` (new public `setRBGColorF` wrapper lets this call
        override its default green tint), else a real `BlockItem` draws 4x
        `FXBoreParticles` (block-icon based, new public `setAlphaF`
        wrapper), else 4x new `FXBoreParticlesItem` (item-icon based, a
        real separate sibling class rather than a second constructor on
        `FXBoreParticles` - that class extends vanilla's own `TerrainParticle`
        specifically for its free block-icon lookup, which Java's `super()`
        rules won't let a second, item-icon-sourced constructor share; the
        true original's own real class duplicated this same homing-physics
        `onUpdate` between its two variants too, so keeping it duplicated
        here matches the true original's own real shape). Real, disclosed
        simplification: this port's own already-simplified
        `TileInfusionMatrix#craftCycle` consumes a matching pedestal's item
        in one real atomic step, not the true original's own real
        multi-tick `itemCount` countdown, so this fires one real short
        particle burst at the moment of consumption rather than
        reintroducing a persistent multi-tick tracking map - same real
        visual language, simpler trigger shape matching this port's own
        already-simpler crafting flow. The true original's own real
        XP-cost recipe branch (`drawInfusionParticles4`, a player→altar
        variant) isn't ported either - `recipeXP`/`recipeType` don't exist
        in this port's own real crafting model at all (a real, separate,
        already-existing simplification, not new here). Two clean 500/500
        `runGameTestServer` passes and a clean `test` task.
      - ~~**`FXEssentiaStream`**~~ — closed 2026-09-17. The initial read of
        `RenderEventHandler`'s own role turned out to be wrong in a real,
        important way once actually read in full: it's NOT a per-frame
        world-scan of tube connections at all - the true original's own
        real static {@code EssentiaHandler.sourceFX} map is populated by a
        real client-bound network packet (`PacketFXEssentiaSource`),
        exactly the same real packet-driven pattern this whole session has
        used repeatedly (`PacketFXBlockArc`/`PacketFXSlash`/etc) -
        `RenderEventHandler`'s own client tick handler just polls and
        drains that queue once per tick. This is a MUCH smaller, more
        contained real integration point than the earlier scoping guess,
        and this port's own already-simplified `EssentiaHandler`
        (2 clean methods, `drainEssentia`/`addEssentia`, not the true
        original's own more complex real caching/retry version) made it
        even more tractable: both real methods now broadcast a real new
        `PacketFXEssentiaSource` on every real successful transfer.
        `TileWaterJug` (the other real call site the earlier scoping note
        flagged) reuses the exact same particle class, not separately
        wired this pass. Real, genuine architectural substitution for the
        particle's own real tube geometry, not a simplification: the true
        original's own geometry came from the same real
        `com.sasmaster.glelwjgl` third-party library `RenderFluxRift`
        already needed a substitute for - reused that exact same real
        ring-extrusion technique here too, applied to this particle's own
        real dynamic, growing-then-shrinking point/radius history (a
        single real tracked head homes toward the target with real
        drag+attraction physics - same real shape `FXVisSparkle` already
        established - leaving a real tapered trail behind it) rather than
        `RenderFluxRift`'s own fixed point list. Real, faithful
        de-duplication kept: concurrent real triggers for the exact same
        real source→target→colour connection extend an already-active
        real stream instead of spawning a redundant new one, matching the
        true original's own real static-map key-reuse logic exactly - real
        and load-bearing, since an actively-draining device calls this
        every few real ticks and needs to read as one continuous flow.
        Real, disclosed simplifications: the true original's own real
        ad-hoc index-based radius taper (first ~5/last ~10 points each
        individually formula'd) is replaced with a real smoothstep taper
        at both real ends instead (same real visual goal - a tapered
        capsule shape); the true original's own real continuous per-FRAME
        wobble recompute is baked in once per real TICK instead (same real
        tradeoff `FXSmokeSpiral` already made); real per-point brightness
        pulsing is dropped for a flat per-particle colour. A real,
        previously-unnoticed SEPARATE FX subsystem was found and
        deliberately NOT pulled into this chunk while reading
        `TileInfusionMatrix`'s own real code: a real "pedestal items flying
        into the matrix during a craft" particle visual
        (`drawInfusionParticles1/2/3`), genuinely unrelated to essentia
        tube flow despite sharing a superficially similar name
        (`TileInfusionMatrix.SourceFX`, a different, local class) - logged
        as new, real, separate remaining scope, not built here. Two clean
        500/500 `runGameTestServer` passes and a clean `test` task.
      - ~~**`FXFireMote`/`FXVisSparkle`/`FXSmokeSpiral`/`FXSlimyBubble`/
        `FXWispEG`**~~ — closed 2026-09-17, one batch since all 5 are real,
        faithful `SingleQuadParticle`-based billboard quads off the shared
        `particles.png` sheet, matching the true original's own real
        per-class motion/animation formula exactly (`FXFireMote`'s own real
        slow-aging shrink/fade; `FXVisSparkle`'s own real drag+attraction
        homing-toward-a-fixed-point physics; `FXSmokeSpiral`'s own real
        expanding-then-contracting double-helix orbit around a fixed origin
        - baked into the particle's own real tracked position once per
        TICK here rather than recomputed every FRAME like the true
        original, since the true original's own render-time offset never
        actually moves the particle's own real position at all, checked
        directly rather than assumed; `FXSlimyBubble`'s own real 3-phase
        forming/rising/popping frame-index state machine; `FXWispEG`'s own
        real "drafts along with its own owner's real horizontal motion
        while airborne" formula). Wired to every real confirmed call site
        (found via `git grep`, not assumed): `EntityFocusProjectile`'s own
        real Fire/Flame focus trail and `EntityAlumentum`/
        `EntityCausalityCollapser`'s own real flight trail (`FXFireMote`,
        shared class, different literal colours per site);
        `TileFocalManipulator`/`TileRechargePedestal`'s own real vis-spend
        sparkle cue (`FXVisSparkle`, restored via a real new vanilla
        entity-event trigger neither tile had before); `EntityEldritchWarden`'s
        own real teleport/materialize cue and `ItemElementalSword`'s own
        real hover-trail (`FXSmokeSpiral` - the Warden's own real
        "growing from small to full size" spawn burst reuses `tickCount`
        directly instead of adding a new synced `spawnTimer` field, since
        both already climb in exact lockstep on a freshly-spawned entity);
        `BlockLiquidDeath`'s own real ambient surface bubble
        (`FXSlimyBubble`, via a new `animateTick` override - the true
        original's own real block class here was a stale, unbuilt 1.12.2
        Forge-fluid leftover, not the real ported `LiquidBlock`-based
        one); `EntityEldritchGuardian`/`EntityEldritchWarden`'s own real
        constant per-tick ambient dust trail (`FXWispEG`). Real, disclosed
        simplification: `EntityFocusProjectile`'s own real trail colour
        (originally picked per-tick from whichever focus effect would run
        next) is plain white instead, since the carried `FocusPackage` is
        deliberately never synced to the client - adding client spawn-data
        sync for one cosmetic trail colour wasn't worth it this pass. Two
        clean 500/500 `runGameTestServer` passes and a clean `test` task.
      - ~~**`FXVent`/`FXVent2`**~~ — closed 2026-09-16 (had been wrongly filed
      as "confirmed zero call site" - a real call site existed after all,
      found while restoring `GOLEM_BUILDER`'s own renderer). Both real,
      faithful `SingleQuadParticle`-based billboard puffs off the shared
      `particles.png` sheet, matching the true original's own real
      grow-then-fade shape exactly (spawns small, grows toward a real
      target scale, fading out as it approaches it - opaque when
      fresh/small, transparent as it finishes "dissipating"). Checked
      real differences between the two classes directly rather than
      assumed identical: `FXVent2` has real per-particle random gravity
      (up or down) and real per-channel colour jitter `FXVent` doesn't,
      plus a slightly faster real growth rate. Wired to BOTH of the true
      original's own 2 real call sites, found via a direct `git grep`
      rather than assumed single-purpose: `TileGolemBuilder#tickPress`'s
      own real 60%/90%+ press-ram thresholds (`FXVent`, the follow-up
      already flagged) AND `TilePotionSprayer`'s own real post-spray vent
      cue (`FXVent2`, a genuinely separate real device not previously on
      this chunk's radar at all) - added a real client-only `venting`
      countdown plus a real vanilla entity-event trigger (id `0`, packing
      the sprayed potion's own real colour into the event's own generic
      second int parameter) to `TilePotionSprayer`, which had no such
      mechanism at all before. Both particle spawns routed through a new
      `ClientFxHandlers#spawnVentBurst`/`spawnVentBurst2` pair rather than
      referencing the client-only particle classes directly from the 2
      common-side tiles' own method bodies - the same real dist-crash
      class this port already hit once for `FocusEffect#renderParticleFX`,
      deliberately avoided here from the start rather than found the hard
      way again. Two clean 503/503 `runGameTestServer` passes and a clean
      `test` task.
    - The rest of this list had no real, currently-compiled call site
      found during this pass's scoping search, so likely low-value until
      whatever gameplay feature they'd back gets built first.
    - ~~**`FocusEffect#renderParticleFX` restoration**~~ — closed 2026-09-16.
      All 10 real `FocusEffect` subclasses with a real particle FX
      (`Air`/`Break`/`Curse`/`Earth`/`Exchange`/`Fire`/`Flux`/`Frost`/`Heal`/
      `Rift`) now render their true original's own real `FXGeneric`-configured
      burst (colors, alpha/scale keyframes, particle-grid indices, gravity,
      slowdown, random-movement jitter, rotation - each matched to that
      effect's own real pre-port config exactly), plus 7 of them
      (`Air`/`Break`/`Earth`/`Fire`/`Flux`/`Frost`/`Heal`) self-trigger a real
      `PacketFXFocusPartImpact` from their own `execute()` via the new shared
      `FocusFxHelper`, and `FocusMediumTouch#execute` now broadcasts a real
      `PacketFXFocusEffect` rendering every real effect in the cast package at
      once - both matching the true original's own real dual-broadcast shape.
      `FXGeneric`'s own gravity default was also fixed in passing (see below).
      - **Real dedicated-server crash found and fixed while verifying this
        chunk**: the first cut declared each `renderParticleFX` override
        directly inside its own common-side `FocusEffectXxx` class,
        constructing `FXGeneric`/casting to `ClientLevel` right there. That
        class is loaded and bytecode-verified on BOTH sides (its `execute()`
        half is real server-invoked gameplay code) - the JVM's own verifier
        resolves every locally-referenced type in a method's body at
        class-verification time, even for a method that's never actually
        invoked there, so the moment any `FocusEffectXxx` class got touched on
        a dedicated server, NeoForge's own dist-check threw `"Attempted to
        load class net/minecraft/client/particle/Particle for invalid dist
        DEDICATED_SERVER"` - 29 real GameTests failed on this. Fixed by adding
        a plain, common-safe `FocusGenericFx` config carrier (primitives/
        arrays only, no client type anywhere) and a single new
        `ClientFxHandlers#spawnGeneric(Level,...,FocusGenericFx)` that does
        the actual `FXGeneric` construction - every real `renderParticleFX`
        body now only ever builds a config object and hands it off, matching
        the same "common code only calls a client-only class from a method
        whose own signature carries no client type" boundary `spawnGolemArc`
        already established, just generalized. A real, previously-undocumented
        lesson for the rest of this port: a client-only reference is only
        safe inside a method that's *itself* declared on a client-only-scoped
        class (like `ClientFxHandlers`, `@EventBusSubscriber(...,
        value=Dist.CLIENT)`) - declaring it directly on a common class that
        also carries real server-invoked gameplay methods crashes even if
        that specific method is never called server-side.
  - **Custom mob models/renderers** (`client/renderers/*`, `client/lib/obj`)
    - now scoped in detail (2026-09-16), via a real cross-check: every entity
    type currently registered with the invisible `RenderFocusProjectile`
    placeholder in `ThaumcraftClient.java` checked directly against the true
    original's own real renderer for that entity, rather than assuming the
    placeholder is always the documented "never had a model" case it
    legitimately is for `FOCUS_PROJECTILE`/`FOCUS_CLOUD` - it wasn't, for
    `FOCUS_MINE`/`GOLEM_ORB`/`ELDRITCH_ORB`/`TURRET_CROSSBOW_ADVANCED`/`WISP`
    /`FLUX_RIFT`/`CULTIST_PORTAL_LESSER`/`CULTIST_PORTAL_GREATER`. Real
    findings:
      - ~~**`WISP`**~~ — closed 2026-09-16. `EntityWisp` had genuinely no
        visible model at all (not the documented-elsewhere "deliberate no
        client FX" case its own doc comment incorrectly claimed before this
        fix) - the true original's own real `RenderWisp` never drew a
        skeletal model either, just 3 real billboarded glow quads every frame
        (2 off the shared `particles.png` sheet, one off a second distinct
        `auranodes.png` sheet tinted by the wisp's own real aspect colour).
        Rather than hand-rolling a brand-new camera-facing-quad primitive
        inside a custom `EntityRenderer` (real, new, and unverifiable
        rendering-pipeline work since GameTest never renders a frame - the
        "check the easy way first" call here), restored via a new
        `FXWispGlow` - 3 real client-local particles that snap to the target
        entity's own position every tick (zero physics), reusing the exact
        same already-proven `SingleQuadParticle` billboard machinery
        `FXGeneric`/`FXSwarm` already established, rather than needing a
        genuinely new `EntityRenderer`-based rendering primitive at all.
        Spawned once (fire-and-forget) from `EntityWisp#aiStep`'s own
        client-side branch via `ClientFxHandlers#maintainWisp`; each particle
        self-removes the instant its target dies (matches the true
        original's own real "stop drawing the moment health hits 0" - no
        fade needed). `FOCUS_PROJECTILE`/`FOCUS_CLOUD` also share the
        placeholder but were confirmed to be genuinely blank/real placeholder
        cases in the true original itself (`RenderFocusCloud#renderEntityAt`
        is a real empty method body).
      - ~~**`FOCUS_MINE`**~~ — closed 2026-09-16. True original's own real
        `RenderFocusMine` draws a real `ModelGrappler`-shaped mesh (a cube
        core plus 3 mutually-perpendicular prongs, a caltrop/mine shape) with
        a pulsing white-to-red beam texture, not just a particle trail - a
        real `ModelGrappler`-equivalent already existed in this port (`
        RenderGrapple`, built for the unrelated `EntityGrapple`, shares the
        exact same true-original model class) and was reused directly rather
        than rebuilt from scratch. New `RenderFocusMine` (a plain
        `EntityRenderer`, since `EntityFocusMine extends ThrowableProjectile`
        isn't a `Mob`) renders the same 4-part geometry bound to the real
        `mine.png` texture, with a real per-vertex red pulse tint matching
        the true original's own exact `(counter+partialTicks)%8/8` formula -
        substituting the entity's own free, already-real `tickCount` field
        for the original's now-dropped dedicated `counter` field (both just
        count real ticks since spawn, so this is a faithful, not simplified,
        substitution). Two clean 504/504 `runGameTestServer` passes and a
        clean `test` task.
      - ~~**`GOLEM_ORB`**~~ — closed 2026-09-16. Turned out simpler than
        expected once actually read: unlike `ELDRITCH_ORB` below, the true
        original's own real `RenderElectricOrb` is just a SINGLE real
        billboarded quad (not procedural spike geometry) off the shared
        `particles.png` sheet - a real pulsing "bob" scale
        (`sin(ticksExisted/5)*0.2+0.2`, faithfully reproduced), a real
        6-frame flicker, and a real 2-variant UV row picked by the orb's own
        real `red` flag. Restored via a new `FXGolemOrb`, same real
        `SingleQuadParticle`-tracking-particle shape `FXWispGlow` already
        established (one quad this time, not three). Real fixed-point
        discovery along the way: `red` existed as a real field on
        `EntityGolemOrb` already but was never synced to the client (`
        defineSynchedData` was empty, a real, deliberate-at-the-time "no
        client FX" cut this same doc comment used to claim) - now a real
        synced boolean, since the client genuinely needs it to pick the
        right UV variant. Two clean 504/504 `runGameTestServer` passes and a
        clean `test` task.
      - ~~**`ELDRITCH_ORB`**~~ — closed 2026-09-16, same session right after
        `GOLEM_ORB`. Built exactly to the real detailed scoping notes logged
        earlier that same day (see the previous commit's own message) - a
        real `FXEldritchOrbSpikes` (extends `Particle` directly, not
        `SingleQuadParticle` - a real custom `render()` building 12
        procedural triangular spikes as 36 real `TRIANGLES`-mode vertices,
        untextured/vertex-coloured, additively blended) plus a real separate
        `FXEldritchOrbGlow` (the billboard layer, same `SingleQuadParticle`
        shape as `GOLEM_ORB`'s own, but real NORMAL alpha blend - checked
        directly, NOT additive like `GOLEM_ORB`'s own glow - and a real
        different `gridSize=64`/13-frame/row-3/fixed-`0.75`-scale config, no
        pulsing bob). Both spawned together, fire-and-forget, from
        `EntityEldritchOrb#tick`'s own client-side branch.
        - Real mechanical translation, not guesswork: the 12 spikes' fixed
          `new Random(187L)` shape (6 rotation angles + length + width per
          spike, 96 values total) is precomputed once in a static
          initializer: confirmed by direct trace that the true original
          never resets/pushes a matrix between spikes within one frame, so
          each spike's own 6 rotations apply CUMULATIVELY on top of every
          earlier spike's own rotations (one shared transform for the whole
          loop, not 12 independent ones) - reproduced via one running
          `Quaternionf` updated across the loop and snapshotted per spike,
          with the real per-frame `ticksExisted/80` spin term folded into
          each spike's own final rotation call.
        - Resolved the color ambiguity flagged during scoping by taking the
          documented, disclosed choice: the true original's own real
          outer-spike-vertex `color(64.0f,64.0f,64.0f,255.0f*...)` call
          (almost certainly a real `/255`-normalization bug in the true
          original, unverifiable without a real 1.12.2 client) is rendered
          as the obviously-intended `64/255` grey, not a literal
          byte-for-byte guess at old LWJGL overflow behaviour.
        - Also disabled face culling entirely (the true original enables it,
          which needs exact triangle winding to avoid inside-out geometry
          from some angles - unverifiable blind, and getting it wrong risks
          invisible spikes, a worse outcome than the minor realism loss of
          always-visible double-sided triangles).
        - **Unrelated flaky-test fallout while verifying this chunk**:
          `taintFibreConnectsSpreadsAndDiesWithoutARealSeed` (already on the
          "watched" flaky list from a first failure on 2026-09-15) failed a
          second time during this chunk's own `runGameTestServer` runs, on a
          completely unrelated file - moved to "disabled" per the
          established policy (see the flaky-GameTest-human-check-list
          memory), `@GameTest` commented out, not deleted. Also hit 2 real
          `runGameTestServer` runs that failed with exit code 1 and NO
          visible failed-test summary at all (not even a flaky one) -
          confirmed via full untruncated log capture that these were some
          other transient GameTestServer-level hiccup, not a real test
          regression (a clean rerun immediately after showed 503/503
          passing with zero code changes in between). Worth remembering: a
          `tail -40`-style truncated capture of a `runGameTestServer` run
          can cut off the actual pass/fail summary line entirely behind a
          long tail of "player left the game" shutdown noise - redirect to a
          real file and `grep` for `required tests` rather than trusting a
          short tail when a run's outcome is ambiguous.
      - ~~**`CULTIST_PORTAL_LESSER`/`CULTIST_PORTAL_GREATER`**~~ — closed
        2026-09-16, same session right after `ELDRITCH_ORB`. Built to the
        detailed scoping notes logged just before: added the real
        `activeCounter` (Lesser only - Greater's own real animation drives
        off plain `tickCount` instead, since it has no activation gate to
        count ticks-since) and `pulse` fields to both entity classes, both
        deliberately plain (not synced) - both sides independently increment
        `activeCounter` once the already-synced `isActive()` goes true, and
        `pulse` is set via a real vanilla entity-event broadcast (`byte 16`,
        matching the true original's own real `setEntityState`/
        `handleStatusUpdate` pair, the same established pattern
        `EntityArcaneBore`'s own dig FX already uses) fired at the real same
        3 trigger points the true original had (Greater: banner placement,
        loot-crate placement, every stage escalation; Lesser: minion spawn
        only), then decremented independently on both sides every tick.
        Restored via `FXCultistPortalLesser`/`FXCultistPortalGreater`, 2 real
        custom-`render()` particles (not `SingleQuadParticle` - genuinely
        non-uniform scale, X/Z by one real multiplier, Y by a real different
        one, matching the true original's own real per-axis `scale`/
        `scaley` split) sharing one real billboard-quad-drawing helper
        (`FXCultistPortalLesser#renderQuad`, package-visible specifically for
        this reuse - only the surrounding animation formula differs between
        the two real portal variants). Real wall-clock-based 16-frame
        flicker (`System.nanoTime()/50_000_000L % 16`, matching the true
        original's own real non-tick-based cadence exactly, not a
        simplification). Two clean 503/503 `runGameTestServer` passes and a
        clean `test` task.
      - ~~**`TURRET_CROSSBOW_ADVANCED`**~~ — closed 2026-09-16. Built to the
        detailed OBJ-loading research logged just before, and the real API
        path compiled clean on the first attempt: `ObjLoader.INSTANCE
        .loadModel(new ObjModel.ModelSettings(objLocation, true, true,
        false, true, mtlOverride))` + `ObjModel#bakeRenderable(
        StandaloneGeometryBakingContext.create(Map.of("Material__25",
        texture)))` baked once in `RenderTurretCrossbowAdvanced`'s own
        constructor, producing a real `CompositeRenderable` rendered every
        frame. Authored the researched minimal synthetic `.mtl` stub
        (`crossbow_advanced.mtl`, explicitly not recovered data - the true
        original never shipped one for this file either) to satisfy
        NeoForge's own modern loader. Real per-part rigging via a fresh
        `CompositeRenderable.Transforms` map built each frame from real
        `Matrix4f` values (not `PoseStack` pushes - that API's own real
        shape), matching the true original's own real `renderTurret`
        formula for all 8 named parts (`legs`/`mech`/`box`/`shield`/
        `brain`/`loader`/`bow1`/`bow2`) exactly, including the real
        `loader` Z-slide and `bow1`/`bow2` opposite-direction recoil swing.
        Added the real missing `getLoadProgress` mechanism to
        `EntityTurretCrossbow` (a real client-only field, driven by a
        vanilla entity-event broadcast already fired by
        `reloadFromDispenserBelow`, inherited for free by
        `EntityTurretCrossbowAdvanced`) - for the bow-recoil swing itself,
        reused real vanilla `swing(InteractionHand)`/`getAttackAnim`
        instead of replicating the true original's own separate custom
        swing-event mechanism, since vanilla's own real, already-proven
        mechanism gives the same real visual trigger. One real, disclosed
        simplification: the true original's own real hurt-red vertex tint
        is dropped (`CompositeRenderable`'s own real mesh path hardcodes
        full white/opaque per vertex, no exposed tint parameter) - the
        real hurt-jiggle translate (the more noticeable half of that same
        effect) is kept. Two clean 503/503 `runGameTestServer` passes and
        a clean `test` task - confirms the whole real OBJ-loading pipeline
        is genuinely safe on a dedicated server too (the renderer's own
        constructor, which does the real model loading/baking, only ever
        runs client-side via `EntityRenderersEvent.RegisterRenderers`,
        same real safety property every other renderer this session
        already established). This same real infrastructure (`ObjLoader`
        + `StandaloneGeometryBakingContext` + `CompositeRenderable`, plus
        the "synthesize a trivial `.mtl` stub" fix) now unblocks
        `FXSonic`/`GOLEM_BUILDER`/`orb.obj`/`turret.obj`/the whole
        `golem_*.obj` customization-parts system too, whenever those get
        picked up.
      - ~~**`FLUX_RIFT`/`TILE_HOLE`/`TILE_VOID_SIPHON`**~~ — closed 2026-09-17,
        the last item in this whole section, tackled together since all 3
        share one real shader. True original's own real `RenderFluxRift`/
        `TileHoleRenderer`/`TileVoidSiphonRenderer` all painted a
        camera-relative procedural starfield via one shared real custom
        GLSL shader (`ender.vert`/`ender.frag`, computing colour purely
        from each fragment's own view-space position plus real `yaw`/
        `pitch` uniforms - genuinely independent of the drawn mesh's own
        UVs). Real modern port, verified against real decompiled/vanilla
        shader sources before writing anything (not guessed): a plain JSON
        program definition + `#version 150` GLSL (real syntax fixes vs the
        original's own `#version 120` - `varying`→`in`/`out`,
        `texture2D`→`texture`, `gl_FragColor`→an explicit `out vec4`, and
        the original's own uniform literally named `texture` renamed since
        that's a reserved GLSL 150 built-in), registered via NeoForge's
        real `RegisterShadersEvent`. Real custom `Yaw`/`Pitch`/`Time`
        uniforms (no vanilla equivalent - vanilla's own reserved `GameTime`
        uniform is a 0-1 daily-cycle fraction, checked directly and ruled
        out) are set manually each draw via a new shared `EnderShader`
        helper, which draws immediately (bypassing the standard
        `MultiBufferSource` batching pipeline - custom uniforms have no
        supported hook into that system's own `RenderStateShard`s).
        `TileHoleRenderer`/`TileVoidSiphonRenderer` needed no new tile-side
        state at all (both already fully ported gameplay-wise). The
        genuinely hard part, `RenderFluxRift`'s own real tapered tube mesh
        (the true original's own real `com.sasmaster.glelwjgl` third-party
        tessellation library, no modern equivalent, not itself part of this
        port) - real architectural substitution, not a cut: a standard
        ring-extrusion algorithm with a running, minimally-rotated
        ("parallel-transport-style") frame per point, avoiding the visible
        twist a naive fixed-world-up reference would produce once the
        tendril's own path turns through vertical. Real, disclosed
        simplifications: the true original's own real in-plane roll
        rotation and per-pass blend-function differences are both dropped,
        confirmed (not assumed) to be real no-ops given the shader's own
        fragment output is unconditionally opaque. Two clean 500/500
        `runGameTestServer` passes and a clean `test` task - **flagged as
        this port's single highest visual-verification-risk chunk**:
        GameTest cannot exercise client-side shader compilation or
        rendering AT ALL (`RegisterShadersEvent` only fires client-side),
        so this is the first chunk this whole session where even "no
        dedicated-server crash" gives no real signal the shader compiles or
        looks right - see the UI visual-parity checklist's own top entry.
    - ~~`FXSonic`~~ — closed 2026-09-17, see the Particle FX section above
      for the real closing note.
    - The much larger remaining bulk of `client/renderers/*` (custom mob
    *models* specifically - skeletal meshes, armor models, tile-entity
    renderers) was NOT swept in this pass - this scoping pass only checked
    entities currently hidden behind the `RenderFocusProjectile` invisible
    placeholder, which is a real, bounded, high-confidence subset (a wrong/
    missing renderer is either invisible or a hard crash, both cheap to
    detect by direct cross-check) but not the entirety of "Custom Mob
    Models/Renderers" as a whole - a real full sweep of already-registered-
    but-possibly-simplified renderers (real vs. placeholder model detail/
    animation fidelity) is still a separate, larger piece of work.
      - ~~**`ModelEldritchGuardian`**~~ — closed 2026-09-24. Real, faithful
        54-cuboid geometry+animation translation of the true original's own
        real hand-modeled mesh (matching its own real cuboid count exactly),
        replacing `ModelEldritchPlaceholder` for both
        `RenderEldritchGuardian`/`RenderEldritchWarden` - shared between
        both real renderers the same way the true original shared one real
        model class. Every part's rotation is explicitly reasserted every
        frame in `setupAnim` (not just the ones the true original's own
        `setRotationAngles` touched), same real defensive convention
        `ModelEldritchCrab` already established, since this model doesn't
        use `HierarchicalModel`'s automatic per-frame pose reset. Real,
        disclosed simplification for the Warden-only glowing `HoodEye` box:
        the true original's own real technique directly hacked the fixed-
        function lightmap coordinate (a pulsing raw value, decoded here as a
        real pulsing block-light level via `LightTexture.pack`) AND
        additively blended it slightly scaled up - this port keeps the real
        pulsing-lightmap mechanic but renders it through the same single
        `entityCutout` buffer as every other part rather than a second
        additive pass (`renderToBuffer` only receives one already-selected
        `VertexConsumer`; a genuinely separate additive pass would need the
        entity renderer's own `MultiBufferSource`, not available from inside
        a model class). Real, previously-undiscovered side effect: restoring
        this mesh unblocked the true original's own real arm-lift swing
        animation (`armLiftL`/`armLiftR`), which this port's own
        `EntityEldritchGuardian`/`EntityEldritchWarden` had never carried at
        all (a documented cut, tied specifically to the not-yet-ported
        mesh) - both real classes now carry their own independent
        `armLiftL`/`armLiftR` fields, decayed 0.05f/tick client-side, driven
        by a real new entity-event pair (`ORB_LEFT_EVENT`/
        `ORB_RIGHT_EVENT`, alternating on every real orb-blast ranged
        attack) matching the true original's own real `lastBlast` toggle
        exactly. Two clean 499/499 `runGameTestServer` passes (one test,
        `fluxGooFallsSpreadsDecaysAndAffectsEntities`, disabled for
        flakiness mid-chunk after a 2nd unrelated-file failure - see
        `flaky-gametest-human-check-list` memory) and a clean `test` task.
        ~~**`ModelEldritchGolem`**~~ — closed 2026-09-24, same session. Real,
        faithful 36-cuboid geometry+animation translation of the true
        original's own real hand-modeled mesh (matching its own real cuboid
        count exactly), replacing `ModelEldritchPlaceholder` for
        `RenderEldritchGolem` - the now fully-unused `ModelEldritchPlaceholder`
        class itself was deleted alongside this (every one of the 3
        Eldritch bosses now has a real mesh). Real, faithfully-kept true-
        original quirk: `frontcloth1` really is rendered twice in the true
        original's own real render order (harmless, same transform both
        times) - kept as-is rather than "fixed". Real, previously-unwired
        gap found and restored alongside this mesh: the true original's own
        real 10-tick attack-swing arm animation needed a real client-
        mirrored countdown, which needed the true original's own real
        `handleStatusUpdate` case-4 entity-event broadcast - this port's own
        `EntityEldritchGolem` had the real server-side attack-cooldown gate
        (`hurtTimer`) but never actually broadcast the matching client
        entity-event, since nothing client-side needed it before this mesh
        existed; `SoundEvents#IRON_GOLEM_ATTACK` (the true original's own
        real sound choice) is restored alongside it too. Real, disclosed
        simplification: the true original's own real "look straight ahead
        and nod based on `getSpawnTimer()`" head override is dropped - that
        field is real but currently server-only in the shared
        `EntityThaumcraftBoss` superclass (no `SynchedEntityData` accessor
        - a separate, bigger decision than this one model's own geometry
        port, and the affected head (`head`, not `head2`) isn't even the
        one rendered during the one case (going headless) where this would
        matter most). Two clean 499/499 `runGameTestServer` passes and a
        clean `test` task. **This closes the entire "mob model fidelity"
        item for all 3 Eldritch bosses** - `ModelEldritchGuardian`/
        `ModelEldritchWarden`/`ModelEldritchGolem` (shared: Guardian+Warden
        use one real class, Golem its own) all now render their real
        hand-modeled meshes.
  - **Block entity (tile entity) renderers - the tile-entity half of that
    "not yet swept" note above, now actually swept 2026-09-16.** Same real
    technique as the entity-renderer cross-check: every real
    `BlockEntityType` registered in `ThaumcraftBlockEntities.java` checked
    against `ThaumcraftClient.java`'s own `registerBlockEntityRenderer`
    calls, and every one WITHOUT a renderer checked directly against the
    true original's own single, exhaustive TESR registration site
    (`proxies/ProxyTESR.java` on `master`) - unlike a missing entity
    renderer, a missing block-entity renderer is NOT a crash risk (a block
    entity renders fine via its block's own static model with none at all),
    so every finding below is a real visual-fidelity gap, not a stability
    one. 11 already-registered renderers (banner/pedestal/recharge pedestal/
    research table/thaumatorium/infusion matrix/focal manipulator/crucible/
    alembic/dioptra/hungry chest) all correctly match the true original - no
    action needed on those. 19 of the 31 renderer-less block entities are
    real false positives, confirmed correct as-is (`ProxyTESR` never
    registered a renderer for any of them either): arcane workbench,
    stabilizer, arcane lamp, vis generator, water jug/everfull urn, arcane
    ear, levitator, infernal furnace, growth/fertility lamps, plain tube,
    tube filter, tube restrict, essentia input/output, condenser, potion
    sprayer, smelter, barrier stone, spa.
    - **11 real, confirmed visual-fidelity gaps** (the true original had a
      real, non-trivial TESR class with real rendering logic - none of these
      renderer classes exist in this port at all yet, confirmed via listing
      `client/render/*Tile*`):
      - ~~`TILE_HOLE`/`TILE_VOID_SIPHON`~~ — closed 2026-09-17 alongside
        `FLUX_RIFT`, see the Custom Mob Models/Renderers section above for
        the real closing note.
      - ~~**`TUBE_ONEWAY`/`TUBE_VALVE`/`TUBE_BUFFER`**~~ — closed 2026-09-16.
        Built exactly to the earlier scoping notes: added real
        `getUpdateTag`/`getUpdatePacket`/`setChanged` sync wiring to both
        `TileTube` (the shared base class - benefits `TileTubeValve` and
        `TileTubeOneway` at once) and standalone `TileTubeBuffer` (doesn't
        extend `TileTube`), plus a real client-only `rotation` field on
        `TileTubeValve` (matching the true original's own exact `+/-20
        degrees/tick` formula, gated on the now-actually-synced
        `allowFlow`). New shared `ModelTubeValve` (a real 2-box
        `CubeListBuilder` translation - rod + ring) backs all 3 new
        renderers: `TileTubeValveRenderer` (both parts, spinning),
        `TileTubeOnewayRenderer` (a single blue-tinted static rod on the
        tube's own facing side, shown only when a real connectable neighbor
        exists there - matches the true original's own real gate exactly),
        `TileTubeBufferRenderer` (one rod per real open+choked+connected
        side, red for a full choke, blue for a soft one). Also corrected a
        stale `TileTubeOneway` doc comment that had called the flow
        indicator dropped "keep the mechanic, drop the flourish" - true only
        because no `BlockEntityRenderer` precedent existed in this port yet
        at the time, same stale-cut shape `TileCentrifuge`/`TilePatternCrafter`
        already went through. Two clean 503/503 `runGameTestServer` passes
        and a clean `test` task (one unrelated flaky test,
        `taintSeedTracksItselfSpreadsFibresAndInflictsAreaFluxTaint`, failed
        once on an unrelated file during this chunk's own first verification
        run - logged to the "watched" flaky list, not disabled, since it
        hasn't recurred).
      - ~~**`JAR`/`JAR_VOID`/`JAR_BRAIN`**~~ — closed 2026-09-17, the last
        remaining large multi-subsystem renderer gap. Turned out to need a
        real, previously-unnoticed sync fix (not just a decoration
        prerequisite): `TileJarFillable` had NO `getUpdateTag`/
        `getUpdatePacket` at all - `amount`/`aspect`/`aspectFilter`/`blocked`
        were never actually visible to the client, meaning the liquid fill,
        aspect label, and lid overlays literally had no server state to draw
        from before this chunk. New shared `TileJarRenderer` (one class,
        matching the true original's own single-class `instanceof` split
        between the essentia and brain-jar cases exactly) handles: a lid
        brace overlay when blocked, a lid extension overlay when a real
        `IEssentiaTransport` neighbor sits above, a rotatable aspect-label
        sticker (background quad + a tinted aspect-icon quad - real,
        faithful translations of the true original's own real
        `UtilsFX.renderQuadCentered`/`drawTag` calls, using
        `Aspect#getImage()`/`Aspect#getColor()` which were already ported
        early in this whole project with exactly this eventual use in
        mind), and a continuously-scaled liquid-fill box (a real
        improvement over the true original's own discrete 5-model swap, not
        a simplification - see `TileJarFillable`'s own doc comment) tinted
        by the held aspect's own colour. Brain jar: the real "looks around,
        sighs occasionally" idle animation
        (`TileJarBrain#rota`/`rotb`/`targetYaw`, a real direct port of the
        true original's own exact ease-toward-target-angle formula,
        including the real random 5-30 second ambient sigh cadence) plus
        the same real brine-glass overlay geometry the essentia jar's lid
        brace reuses. Real, disclosed simplifications: this port's own
        `BlockJar` has no facing/orientation state at all, so the label
        sticker always faces one fixed direction rather than the true
        original's own real per-facing rotation; the true original's own
        `ModConfig.CONFIG_GRAPHICS.crooked` cosmetic toggle (a small random
        per-jar label tilt, on by default) is applied unconditionally
        rather than resurrecting the whole dropped graphics-config surface
        for one single-purpose boolean - same real tilt formula, just not
        user-disableable. Two clean 500/500 `runGameTestServer` passes and
        a clean `test` task - 4 more unrelated single-run flaky-test
        failures turned up across this chunk's own verification (Hungry
        Chest auto-consume, Workbench vis cost, Infusion Matrix speed
        corners, none recurring) - all logged to the watched list per
        standing policy, none disabled since none recurred.
      - ~~**`BELLOWS`**~~ — closed 2026-09-17. Turned out to need NO new sync
        wiring at all, unlike almost every other renderer restoration this
        port has done - `TileBellows`'s own real client-only `inflation`
        (breathing-bag) field is driven purely off
        `BlockStateProperties.ENABLED`, which is already a normal
        `BlockState` property and thus already synced to the client for
        free (no `getUpdateTag`/`getUpdatePacket` gap this time). Real,
        faithful 5-box `CubeListBuilder` translation of the true original's
        own real `ModelBellows` (bottom/top/middle planks, bag, nozzle),
        with the bag independently vertically scaled and both planks
        sliding apart/together in lockstep off the real inflation value -
        same nested push/scale/translate sequence as the true original's
        own real GL matrix stack, translated call-for-call into `PoseStack`
        operations (which compose the same way, same technique already
        proven for `MIRROR`'s own `translateFromOrientation`). Real,
        checked-not-assumed detail: the true original's own `Bag` box
        declares a per-part texture-size override
        (`setTextureSize(64,32)`, differing from the model's own base
        128x128) - but its own real UV unwrap at that texture offset fits
        exactly within the real physical 128x128 texture either way, so no
        conversion was actually needed. Real, disclosed simplification: the
        true original's own real "bore extension" nozzle (only drawn when a
        `TileTubeBuffer` sits directly against the bellows, reusing the
        same real `ModelBoreBase#renderNozzle` geometry
        `TileAlembicRenderer` already established) is restored faithfully
        for the 4 horizontal facings, but its own real per-facing rotation
        table for the 2 vertical facings resisted a clean 1:1 translation -
        approximated with the same general "point outward along facing"
        intent instead, a narrow decorative sub-case (needs a vertically-
        placed Bellows directly against a Tube Buffer). Two clean 500/500
        `runGameTestServer` passes and a clean `test` task - one more
        pre-existing "watched" flaky test
        (`cultistKnightWearsCrimsonPlateAndAlliesWithItsOwnKind`) hit its
        2nd failure mid-chunk and was disabled per standing policy
        (unrelated file, no plausible causal link to this chunk's own
        client-only rendering change).
      - ~~**`CENTRIFUGE`**~~ — closed 2026-09-16. Built to the scoping notes
        logged just before: added real client-only `rotation`/
        `rotationSpeed` fields to `TileCentrifuge` (ramping toward a target
        speed based on `aspectIn`/redstone power, exactly matching the true
        original's own real formula, including the real 180-degree-mark
        `pump` sound cue) plus real `getUpdateTag`/`getUpdatePacket`/
        `setChanged` sync wiring this tile never had before (a real,
        previously-unnoticed gap - `aspectIn` itself wasn't synced to the
        client at all until now, same real pattern `TileCrucible`/
        `TileFocalManipulator`/`TileInfusionMatrix` already established).
        New `TileCentrifugeRenderer` (a real 4-box `ModelCentrifuge`
        "spinny bit" translated into the modern `CubeListBuilder` system,
        same technique `RenderGrapple`/`RenderFocusMine` already proved) -
        the static top/bottom pair stays on the real already-present baked
        block model, matching the true original's own real split between
        its `renderBoxes()`/`renderSpinnyBit()` calls. Also corrected 2
        stale doc comments (`TileCentrifuge`'s own and `BlockCentrifuge`'s
        own) that had called dropping this animation "a real improvement" -
        true once, stale once a real `BlockEntityRenderer` precedent
        existed elsewhere in this port; per the project's own standing
        "reevaluate past cuts once their blocker resolves" directive, this
        restores real, complete parity (static model AND spin animation
        both) rather than leaving the tradeoff standing. Two clean
        503/503 `runGameTestServer` passes and a clean `test` task.
      - ~~**`MIRROR`/`MIRROR_ESSENTIA`**~~ — closed 2026-09-17. Turned out NOT
        to need a secondary render pass/framebuffer despite the 628-line
        scale scare - the true original's own real `TileMirrorRenderer`
        hand-rolls the exact same 16-layer parallax "portal tunnel" effect
        as vanilla's End Portal block, using raw fixed-function
        `GL_TEXTURE_GEN` matrix math (long gone from modern rendering).
        Modern Minecraft already ported that exact same effect to a
        dedicated fragment shader (`rendertype_end_portal`,
        `RenderStateShard.RENDERTYPE_END_PORTAL_SHADER`) driven by nothing
        but a plain position-only quad - so this reuses that real vanilla
        shader wholesale with Thaumcraft's own `tunnel.png`/
        `particlefield.png` swapped in for vanilla's `end_sky.png`/
        `end_portal.png` (mapped role-for-role: `tunnel.png` plays the same
        "background" role as the sky layer, `particlefield.png` the same
        "swirl" role repeated across the shader's own 15 foreground layers)
        via a new custom `RenderType` - a faithful port of the same real
        visual technique, not a simplification, and far less code than
        reimplementing the original's own dead GL calls. New shared
        `TileMirrorRenderer<T>` (one class, both tile types) draws a single
        inset quad flush with the block's exposed face - only when the
        tile's own real link is valid (`isLinkValid()`, the modern
        equivalent of the original's `linked` boolean - both tiles' own
        gameplay/link/sync logic was already fully ported before this
        chunk, confirmed by reading both in full) and the viewer is within
        the original's own real 32-block cutoff. Backface culling disabled
        on the quad rather than chasing exact vanilla winding order -
        harmless since the mirror's own support block already occludes the
        one side that would otherwise show through. Also re-evaluates
        `BlockMirror`/`BlockMirrorEssentia`/`TileMirror`/
        `TileMirrorEssentia`'s own stale doc comments, which had cited this
        port's early, blanket "no custom renderer restored yet" boundary -
        no longer true, per this port's own "re-evaluate past cuts once
        their blocker resolves" rule. Two clean 501/501 `runGameTestServer`
        passes and a clean `test` task (503→501: 2 more pre-existing,
        unrelated flaky tests hit their 2nd failure mid-chunk and were
        disabled per standing policy - see the flaky-GameTest-human-check-
        list memory).
      - `GOLEM_BUILDER` (`TileGolemBuilder`) - true original's own real
        `TileGolemBuilderRenderer` (104 lines), likely renders the
        in-progress golem parts being assembled - not yet read in detail.
      - ~~**`PATTERN_CRAFTER`**~~ — closed 2026-09-16. Turned out NOT to be
        `ModelBoreBase`-driven after actually reading the true original's
        real `renderTileEntityAt` in full - that `model` field is
        real-but-unused dead weight in this specific method (a copy-paste
        leftover, never called). The real visual is a facing-oriented
        mode-indicator quad (a real 10-frame strip on
        `pattern_crafter_modes.png`, one frame per real crafting pattern)
        plus 2 real counter-rotating brass-gear quads, driven by a real,
        previously-dropped client-only crank animation (`rot`/`rp`/
        `rotTicks` - a real vanilla block-event-triggered "burst then decay"
        spin, matching the true original's own exact formula including a
        real `clack` sound cue) - restored alongside real
        `getUpdateTag`/`getUpdatePacket` sync wiring this tile never had
        either (`type` wasn't visible to the client before). New
        `TilePatternCrafterRenderer` draws both real elements as plain flat
        quads. Real, disclosed simplification: the true original's own real
        gear icons used a genuinely different technique
        (`UtilsFX.renderTextureIn3D`, a real per-pixel-column-extruded mesh
        giving visible 3D bevelled thickness) with no precedent anywhere
        else in this port and no real behavioural/legibility cost to
        dropping just the bevel - flat quads instead, same real "keep the
        animation, simplify the pure decoration" call already made for
        `ELDRITCH_ORB`'s disabled face-culling. Exact sub-pixel icon
        placement is a reasonable approximation of the true original's own
        real nested transform stack, not a verified match - flagged in the
        UI visual-parity checklist for a human `runClient` check. Two clean
        503/503 `runGameTestServer` passes and a clean `test` task.
    - Real prioritization note, updated 2026-09-16 after scoping the last 3
      items directly (none turned out to be quick):
      - ~~`JAR`/`JAR_VOID`/`JAR_BRAIN`~~ — closed 2026-09-17, see the Custom
        Mob Models/Renderers section above for the real closing note. The
        real animated liquid fill turned out tractable as a plain 6-quad
        `VertexConsumer` box (same established technique `TileCrucibleRenderer`
        already proved for its own water fill) rather than needing the true
        original's own real `RenderCubes` class ported wholesale.
      - ~~`BELLOWS`~~ — closed 2026-09-17, see the Custom Mob Models/
        Renderers section above for the real closing note. Turned out
        smaller than feared once actually read - no new sync wiring
        needed at all (`inflation` is driven off an already-synced
        `BlockState` property).
      - ~~**`GOLEM_BUILDER`**~~ — closed 2026-09-16, same session right after
        `TURRET_CROSSBOW_ADVANCED` proved the OBJ-loading path out. Genuinely
        simpler than the crossbow: only 4 real named parts (`table`/`anvil`/
        `box`/`press`), only 1 animated, and no facing-rotation at all (this
        port's own `BlockGolemBuilder` has no facing property, matched
        as-is). `press` (a real client-only vertical-bob field, off the
        true original's own exact `sin(radians(press))*0.625` formula) is
        gated on already-synced `cost`/`golem` state. A real, checked-not-
        assumed difference from the crossbow case: `golembuilder.mtl`
        genuinely already exists as a real original asset (unlike
        `crossbow_advanced.obj`'s own missing one) - but its own real
        `map_Ka`/`map_Kd` lines reference a stale absolute Windows path
        from the original author's own machine, so a fresh minimal
        synthetic `.mtl` override was used anyway rather than risk it
        tripping the modern parser. **A real, previously-unnoticed bug
        found and fixed along the way, not just a rendering gap**:
        `TileGolemBuilder` had NO `saveAdditional`/`loadAdditional`
        overrides at all - an in-progress craft, or even a finished real
        golem placer sitting in the output slot, would silently vanish on
        a chunk save/reload. Fixed alongside the real sync wiring the
        renderer also needed (`getUpdateTag`/`getUpdatePacket`), the same
        "add both while touching this file anyway" efficiency already
        proven for `TileCentrifuge`/`TilePatternCrafter`. Real, disclosed
        cut: the true original's own real vent-steam particle bursts at
        the 60%/90%+ press thresholds aren't restored (a genuine, real
        call site for the still-unported `FXVent`/`FXVent2` classes,
        previously - incorrectly - filed as confirmed-zero-call-site in
        the Particle FX scoping notes; corrected there too) - the real
        lava-extinguish sound cues at those same thresholds ARE kept,
        since they need no new particle infrastructure. Two clean 503/503
        `runGameTestServer` passes and a clean `test` task. This confirms
        the real OBJ-loading infrastructure generalizes cleanly to a
        second, differently-shaped model on the first attempt - genuinely
        de-risks `FXSonic`/the `golem_*.obj` parts system for whoever
        picks those up next.
      - ~~`HOLE`/`VOID_SIPHON`/`FLUX_RIFT`~~ — closed 2026-09-17, bundled
        together as planned since all 3 share one real shader. This closes
        out the entire Custom Mob Models/Renderers "no renderer registered
        at all" sweep - only the much larger, separately-scoped mob *model*
        fidelity bulk (skeletal mesh detail, animation accuracy, armor
        layers for renderers that DO already exist) remains.
- **Deferred, not queued**: boss multiplayer target-scaling and the floating
  special-item death-drop visual both still route through the unported
  `EntityUtils` - same already-correctly-blocked dependency this file has
  documented repeatedly; not re-attempted as a side effect of this pass.

**Sweep of 2026-09-15**: the user asked to re-check every "isn't ported yet"/
"not real yet"-style doc comment across the whole `src/main/java` tree (51
files matched) for staleness, the same pattern that found `effectSap`'s own
stale reasoning - per the user's own standing directive, see
[[thaumcraft-port-autonomy]]. Most of the 51 remain genuinely, correctly
blocked (mostly on the large unported `EntityUtils`/`InventoryUtils` utility
classes, the magical-node system, and the config-driven starter-book system -
Baubles/Curios stays a separate, standing exclusion, not a "blocked" item).
The entries below are the real ones whose own cited blocker has since
resolved, or that were simply never picked up once reachable - each
cross-checked directly against current `build.gradle`/registry state, not
assumed from the doc comment alone.

**Correction #1, same day**: the user directly questioned my own first pass
at this sweep - "I think we've already done at least one of them (the
champion-mod system) so the others might be in better states than we think
too." They were right: the champion-mod system (`ThaumcraftAttributes
.CHAMPION_MOD`, `ChampionModEvents`, `ChampionUtils`, wired into
`Thaumcraft#registerChampionAttribute`) is fully real and complete - my own
first-pass sweep had wrongly filed it under "still blocked" without actually
checking. Re-verified the rest of that same "still blocked" bucket directly
after being called out (not just re-trusting my own first pass):
`EntityUtils`/`InventoryUtils` (re-checked more rigorously the second time -
confirmed no duplicate-path false positive like `RenderEldritchGuardian`
below, still genuinely just the stale 1.12.2 leftover files, and no focused
extraction exists for the SPECIFIC methods still cited -
`isFriendly`/`isVisibleTo`/`getPlayerSlotFor` - the way `ChampionUtils` was
already extracted from `EntityUtils` for the champion-mod half specifically;
confirmed the whole `common/lib/utils` package itself: only `RandomItemChooser`/
`CuboidTargeting`/`ChampionUtils` are real, everything else - including
`BlockUtils`, `Utils`, `HexUtils` - is still the stale original) and the
starter-book system (confirmed: `ConfigItems.startBook`'s only real
consumer, `PlayerEvents.java`, is still the stale, uncompiled 1.12.2
leftover, and this port has no other "give a starting item on first join"
mechanic that might have superseded it). Both really are still correctly
blocked. The magical-node system turned out to have the SAME real problem
as the Eldritch dimension below (see Correction #2) - see Correction #3.

**Correction #2, same day**: the user then asked about "the custom Eldritch
dimension" specifically, which this sweep had also filed under "still
blocked." That framing was wrong too, but in the OTHER direction from
champion-mod - not "it's actually real," but "it's not real at all, and
already confirmed so": a 2026-09-09 session (a different one) already
investigated this exhaustively after the user asked to build "the cultist
dimension" - a full-tree `git grep` for `DimensionType`/`WorldProvider`/
`PopulateChunkEvent`/`GenerateMinable`/`StructureComponent`/`MapGenStructure`
across the true original found zero real hits, and a community wiki
cross-check confirmed **Thaumcraft 6 has no custom dimension of any kind**
(the "Eldritch Obelisk"/"Outer Lands" concept that surfaced belongs to
Thaumcraft 5, a different mod version). `ModConfig.CONFIG_WORLD
.dimensionOuterId` (the config field this port's one remaining real doc
comment on it cites, `EntityEldritchGuardian`'s own per-dimension
absorption buff) is a real, generic "recognize some other mod's dimension
by numeric id" compatibility knob, not a TC6-authored place - so that cut
is permanently inapplicable, not a future item. Moved to "Confirmed
dead-in-the-true-original" below. `EntityEldritchGuardian`'s own doc
comment corrected to match. Also caught a real methodology gap while
checking this: the sweep's own `build.gradle` presence checks matched by
bare filename, not full path - `RenderEldritchGuardian
.java` exists TWICE (the real modern one at `client/render/`, a stale
1.12.2 leftover at `client/renderers/entity/mob/`), so a same-named file
could false-positive as "real" when only its stale namesake actually
matched. Worth double-checking full paths, not just filenames, on any future
sweep-style `build.gradle` check.

**Correction #3, same day**: applying that same "re-check, don't just
re-trust" discipline to the remaining "still blocked" bucket (per the
user's own follow-up: "update it first, then maybe do similar checks for
the other blockers?") found the magical-node system has the exact same
real problem as the Eldritch dimension - already investigated and
**confirmed dead** in an earlier session (2026-09-08, a real correction at
the time: "Aura Nodes is NOT a real Thaumcraft 6 feature"), just never
written into this file, so this sweep's own first pass repeated the same
"still blocked" mistake on it independently. TC6 explicitly *removed* the
TC4-era discrete vis-node system - confirmed via both the decompiled true
original (zero `TileNode`/`BlockNode`/world-gen-node files anywhere) and a
community wiki ("Starting with Thaumcraft 6, the concept of vis nodes was
completely removed"). TC6 replaced it entirely with the ambient per-chunk
Aura system (`AuraHandler`/`AuraChunk`/`AuraWorld`, real and ported since
the very first session), and the real TC6 equivalent world-gen feature -
Primal Crystal Clusters - is also already real and shipped. Moved to
"Confirmed dead-in-the-true-original" below.

- ~~**`BlockTaintFibre`'s leaf-touching "grow into `taintFeature`" branch**~~
  — closed 2026-09-15, and turned out much bigger once actually
  investigated: `TaintHelper#spreadFibres` was missing 3 whole real
  branches, not just leaf-touch (hemmed-in-taint conversion, taint-seed-
  edge spawn too - all 3 target dependency sets shipped together in the
  2026-09-03 taint-terrain arc). Also found and fixed a real, separate bug
  along the way: `EntityTaintSeed`/`EntityTaintSeedPrime` were both missing
  their real hitbox override. See "Recently closed" below.
- ~~**`TaintHelper`'s self-pruning stale `taintSeeds` entries**~~ — checked
  2026-09-15, real but downgraded, not closed as originally scoped: turns
  out `EntityTaintSeed`'s own real lifecycle (calls `removeTaintSeed` on
  death, re-registers via `addTaintSeed` every 1200 ticks) already
  substantially covers this in practice - a stale entry can only really
  outlive its seed if the entity is removed some other way entirely
  (unloading, `/kill`, etc.). A real, minor, still-open hardening item, not
  the load-bearing gap it was originally filed as - documented in
  `TaintHelper`'s own doc comment, not re-listed as a top-level entry here.
- ~~**`EntityCultistKnight`'s real curio bonus drop + third targeting
  entry**~~ — closed 2026-09-15. The curio drop turned out to already be
  real (`MobDeathDropEvents` already covers every `EntityCultist` subtype,
  stale doc comment only); restored the real `EntityEldritchGuardian`
  targeting entry. Committed `157f794`.
- ~~**`EntityCultist#isAlliedTo`'s missing `EntityCultistLeader` half**~~ —
  closed 2026-09-15. Restored, matching the true original's own
  `isOnSameTeam` exactly. Committed `bd2881c`.
- ~~**`EntityTaintacle#spawnTentacles`'s Eldritch-biome-gated extra fibre
  placement**~~ — closed 2026-09-15. Restored (both `EntityTaintacle` and
  `EntityTaintSeed`, which duplicates the identical logic, same as the true
  original). Real end-to-end coverage of the biome-gate branch itself needs
  a real Eldritch-biome position, which GameTest's own environment can't
  provide - tested the new `isAdjacentToSolidBlock` helper directly instead.
  Committed `7d0af5a`.
- ~~**`AidPortal`'s missing `AidPortalCrimson` variant**~~ — closed
  2026-09-15. Added, registered in `Thaumcraft.java` alongside
  `AidPortalEnd`/`AidPortalNether`. Committed `51b13fa`.
- ~~**`TileInfusionMatrix#triggerInstabilityEvent`'s trimmed outcome
  table**~~ — closed 2026-09-15. Restored the true original's own full real
  24-weighted-outcome table (`BlockFluxGoo` placement, real Flux Taint/Vis
  Exhaust affliction via a new `inEvHarm`, and `inEvEjectItem`'s real type
  0-5 split) now that both dependencies are real. `TileStabilizer`'s
  charge-network item-redirect mitigation stays genuinely unbuilt
  (`TileStabilizer.java` itself is still the stale 1.12.2 leftover); also
  fixed a doc-comment mix-up that lumped the unrelated, already-real
  `BlockInlay` (a plain passive stabilizer) in with that unbuilt subsystem.
  Committed `243918d`.
- ~~**`ItemPechWand`'s real acquisition path**~~ — corrected 2026-09-15, not
  actually a gap: `EntityPech`'s own real `TRADE_REWARDS` table already
  lists the wand as a real tier-5 "thaum/wand" reward, matching the true
  original's own `ConfigEntities#tradeInventory` placement exactly - the
  doc comment just hadn't caught up to the Pech trading chunk landing.
  Added real GameTest coverage confirming it. Committed `90d7e50`.
- ~~**`AspectList(ItemStack)`'s dead constructor stub**~~ — closed
  2026-09-15. Now routes through `AspectHelper#getObjectAspects`, already
  fully wired (`ThaumcraftInternalMethodHandler` → `ThaumcraftObjectTags`)
  for a different caller - this constructor was the one piece nobody had
  gone back to wire up.
- ~~**`ItemsTC.TIER_PRIMAL`'s stale repair ingredient**~~ — closed
  2026-09-15. Now repairs with the real `ingotVoid`, matching its sibling
  tiers.
- ~~**`GolemProperties`' FLYER leg using `Items.FEATHER`**~~ — closed
  2026-09-15. Now costs a real `BlocksTC.levitator`.
- ~~**`TileArcaneWorkbench`'s wide-vis draw never actually wired up**~~ —
  corrected 2026-09-15, not actually a gap: the sweep's grep-based check only
  looked at `TileArcaneWorkbench.java` itself and found no
  `getWideVis`/`drainWideVis` calls there, but the real wiring lives in
  `ArcaneWorkbenchMenu` instead (a different file) - already real, fully
  working, and already covered by its own real GameTest
  (`workbenchChargerDrawsVisFromNeighbouringChunksToo`). Only
  `TileArcaneWorkbench`'s own stale doc comment needed fixing. Committed
  `897ce21`. Same "check the actual dependent file, not just the one citing
  the cut" methodology gap as the `RenderEldritchGuardian` duplicate-path
  lesson above - a single-file grep can miss real wiring that lives one hop
  away in a different class entirely.
- ~~**`ItemResonator`'s missing `TileTubeBuffer` branch, and
  `TileTubeBuffer`'s own separate stale CodeChicken note**~~ — closed
  2026-09-15. Gave `TileTubeBuffer` the same real per-face toggling
  `TileTube`/`TileTubeFilter`/`TileTubeRestrict`/`TileTubeOneway` already
  have (via `IInteractWithCaster`/`CuboidTargeting`, mirroring onto a real
  neighboring `TileTube` via a new public `TileTube#setSideOpen` hook), then
  restored `ItemResonator`'s own missing branch on top (lists every real
  aspect a multi-aspect buffer holds, not just one). The true original's own
  sneak-click "choke" throttle stays cut, now for a more precise reason:
  without real bellows amplification its first two states are numerically
  identical in this port, so a real 3-state cycle would show 2
  functionally-identical settings - worse than not offering it. Committed
  `f135fb4`.
- ~~**`Thaumcraft.java`'s loot-bag drop uses a flat 8% chance instead of the
  real champion-mutation gate**~~ — closed 2026-09-15. Restored the true
  original's own exact `livingDrops` condition: a real champion (id 0-13,
  excluding Tainted/13), excluding `EntityThaumcraftBoss` (bosses get their
  own drops), always drops one - no independent chance roll on top. Tier
  stays a weighted pick since `LivingDropsEvent` genuinely has no modern
  `lootingLevel` equivalent (confirmed by reading the event class directly).
  Also caught and fixed a real regression the boss-champion-eligibility
  commit (`abf93fe`) introduced while restoring this: `ChampionModEvents
  #onDrops`'s bonus-XP-on-champion-death didn't exclude bosses, so a champion
  boss would have gotten it despite the true original explicitly excluding
  `EntityThaumcraftBoss` from that same condition. Committed `9af125c`.

~~**Real design fork surfaced by the champion-mod correction, decided
2026-09-15**~~ — closed 2026-09-15. `Thaumcraft#registerChampionAttribute` had
deliberately excluded this mod's own boss-tier entities from ever rolling as a
champion (no modern per-instance "every `EntityCreature`" equivalent to the
original's blanket 1.12 `EntityConstructing` approach, so a curated type list
was used instead - and bosses simply weren't added to it). Flagged to the user
rather than decided unilaterally, since the true original's own blanket
approach would really have included bosses too. **User's call: "Yes, make
them eligible" - real parity, matching the original.** Shipped:
`CultistLeader`/`EldritchWarden`/`EldritchGolem`/`TaintacleGiant` (a 4th boss,
`EldritchGolem`, found only by re-reading the true original directly rather
than trusting the other 3 bosses' own citations as exhaustive - it has the
same real champion tie-in too) added to `registerChampionAttribute`'s
eligible list; `ChampionUtils#makeChampion` now branches on
`EntityThaumcraftBoss` the same way the true original's own
`EntityUtils#makeChampion` did, calling a new `onChampionRolled` hook instead
of the generic flat-buff/name-overwrite treatment; `CultistLeader`/
`EldritchWarden`/`EldritchGolem` each override it to fold the champion mod's
name into their own real title-based name (also fixing a real `%d`/`String`
format-string bug the true original's own Praetor/Warden name formats share);
`TaintacleGiant` (not an `EntityThaumcraftBoss`) gets a real
`finalizeSpawn` calling `ChampionUtils.makeChampion` unconditionally, matching
the true original's own unconditional `onInitialSpawn` call. Committed
`abf93fe`.

A few more sweep items are real but not blocked by anything, just not yet
picked up - not stale cuts, just open:
`BlockTaint#canFallBelow`'s wood-log-proximity check (a 3x3x3 scan, real but
minor); `GolemInteractionHelper`'s pre-placement collision nudge (real but
minor, current fallback already fails cleanly); `ModelTaintacle`/
`ModelTaintSeed`'s `flailIntensity`/`attackAnim` post-attack animation spike
(needs a small synced float added to `EntityTaintacle`/`EntityTaintSeed`
themselves, purely cosmetic payoff). Listed here for completeness, not
elevated to their own top-level entries.)*

*(`BlocksTC`/`ItemsTC` registries themselves are confirmed complete as of
2026-09-14 - **three** field-name sweeps in a row now (most recent one
re-run after Tube One-way's new registrations landed) have found nothing
real left there; the ~70 raw candidate misses on the 3rd pass were all
already-known naming-convention differences or already-documented
false positives, re-verified individually rather than assumed - see
`git log`/session memory for the exact list checked. The 3 items that did
live here - `required_craft`/`required_item` auto-progression, the
warp-on-craft penalty, and label dyeing - all came from a broader, deeper
sweep of the whole file tree and are all now closed, see "Recently closed"
below. See "Fresh sweep, 2026-09-14 (part 2)" below for what else that
sweep turned up and what's still unscoped.)*

## Needs further triage (real content exists in the original; scope/priority unconfirmed)

**Empty as of 2026-09-15.** The last entry here, `effectSap`, closed the same
day it was scoped - see "Recently closed" below. Its own "low priority, no
real consumer" reasoning was itself stale: `EntityEldritchWarden` turned out
to already be a fully shipped boss, just missing 2 of its own real mechanics
specifically because this block didn't exist yet - the same
"re-check the reason, not just the cut" lesson this whole project keeps
re-confirming.

- ~~**`effectSap`**~~ — closed 2026-09-15. See "Recently closed" below.
- ~~**`liquidDeath`**~~ — closed 2026-09-14. The "tangled with the
  finite-fluid subsystem" reasoning above was itself stale/wrong once
  re-checked directly: `BlockFluidDeath`'s own true original base class is
  Forge's OLD `BlockFluidClassic` (an infinite-source-block wrapper), NOT
  the finite one `BlockFluxGoo`/`BlockPurifyingFluid` needed - so it never
  actually needed the finite-fluid engine at all, and got the real modern
  vanilla `FluidType`/`LiquidBlock`/`BucketItem` API instead. See "Recently
  closed" below.
- ~~**`grassAmbient`, `shimmerleaf`, `vishroom`**~~ — closed 2026-09-14.
  Confirmed placed exclusively by the true original's own
  `BiomeGenMagicalForest#decorate` override, genuinely inseparable from
  the custom-biomes arc rather than a standalone plant-scatter task - so
  it landed as part of "World generation, part 2" (see "Recently closed"
  below) once the custom-biomes question itself got resolved.
  `grassAmbient` didn't need its own block (see
  `BlockPlantShimmerleaf`'s own doc comment). `bloom` turned out not to be
  a real standalone block at all - only shader/model texture assets exist
  for it in the true original, most likely a decoration on the (still
  not-ported) big magic tree feature.
- ~~**Mound generation**~~ — closed 2026-09-14. See "World generation,
  part 3" under "Recently closed" below.
- ~~**Essentia tube chains can deadlock on a suction tie**~~ — closed
  2026-09-15. Found 2026-09-14 while testing the tube click-precision
  restoration, a real bug in this port's own `TileTube#calculateSuction`,
  not a gap in the original's own content. Root cause, confirmed by direct
  comparison against the true original rather than inventing a fix: the
  original always stored a neighbor's demand *minus one* per hop
  (`setSuction(st, restrict ? suck/2 : suck-1)`) - this port had silently
  dropped that real per-hop decay, storing the raw value verbatim instead,
  which is exactly what let 2 chained tubes tie. Restored the real decay
  (now applied via `adjustSuction`, called after the threshold check rather
  than before, matching the original's exact formula split); also reverted
  a related, now-provably-unnecessary `<=` workaround in
  `TileJarFillable#tick` back to the original's real strict `<`. New
  GameTest reproduces the exact deadlock scenario and confirms it's gone.
  See commit "Fix the real essentia tube chain suction deadlock - restore
  per-hop decay".
- ~~**`tubeOneway`**~~ — **closed 2026-09-14, and the "known,
  already-diagnosed blocker" note here was stale.** It claimed Levitator/
  Redstone Relay were *also* still blocked on the same unported CodeChicken
  raytracing (`IndexedCuboid6`/`RayTracer`) - direct inspection found both
  had already shipped, each replacing the original's precise multi-hitbox
  click UI with a plain `useWithoutItem`-cycles-a-value interaction. First
  closed with the same real click-to-cycle substitute (commit "Give Tube
  One-way its real directional suction/flow mechanic"), then **superseded
  the same day** once the user asked how CodeChickenLib actually worked -
  turned out fully replaceable with modern vanilla APIs (no third-party
  library needed), so the real precise click UI came back for real instead
  of staying a placeholder. See "Recently closed" below for the full arc.
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
  Three real leftovers fell out of this scoping pass - label dyeing,
  `required_craft`/`required_item` auto-progression, and `CraftingEvents`'
  warp-on-craft penalty - and all three are now closed (see "Recently
  closed" below) - **the warp-on-craft one is a correction, not
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

- **`EntityHomingShard`, `EntityRiftBlast`** — confirmed 2026-09-15 during
  the full parity audit: both have real registered client renderers in the
  true original, but zero real `new EntityHomingShard`/`new EntityRiftBlast`
  call sites anywhere in the whole pristine-original tree - never actually
  spawned by anything. No action needed.
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
- **The "cultist dimension" / custom Eldritch dimension** — confirmed in an
  earlier session chunk (2026-09-09) to never have existed in real shipped
  Thaumcraft 6 (TC5-only concept, or a still-open Azanor GitHub suggestion).
  See memory file for the full investigation. The real Ancient/Eldritch
  stone family was shipped instead, faithfully matching the true original's
  own genuinely-unreachable content. Re-surfaced 2026-09-15 by the
  whole-tree sweep, which had wrongly filed it as "still blocked" (real
  content, not yet reachable) rather than "confirmed dead" (never real at
  all) - `ModConfig.CONFIG_WORLD.dimensionOuterId` (the config field this
  port's one remaining real citation, `EntityEldritchGuardian`'s own
  per-dimension absorption buff, references) is a real, generic "recognize
  some other mod's dimension by numeric id" compatibility knob, not a
  TC6-authored place; that cut is permanently inapplicable, not a future
  item. `EntityEldritchGuardian`'s own doc comment corrected to match.
- **The "magical-node" / Aura Nodes system** — confirmed dead 2026-09-08 (an
  earlier session): Thaumcraft 6 explicitly *removed* the TC4-era discrete
  vis-node system (`TileNode`/`BlockNode`/world-gen-node placement) -
  confirmed via the decompiled true original (zero such files anywhere) and
  a community wiki quote ("Starting with Thaumcraft 6, the concept of vis
  nodes was completely removed... Aura no longer works the same way as in
  Thaumcraft 4.2 where finding and tinkering with nodes are necessary"). It
  was replaced entirely by the ambient per-chunk Aura system
  (`AuraHandler`/`AuraChunk`/`AuraWorld`), already real and ported since the
  very first session of this project; the real TC6-era equivalent world-gen
  feature, Primal Crystal Clusters (`BlockCrystal`, 7 real Aspect
  registrations), is also already real and shipped. This finding was never
  written into this file at the time, so the 2026-09-15 whole-tree sweep
  independently re-filed it as "still blocked" (real content, not yet
  reachable) rather than "confirmed dead" (never real at all) - see
  Correction #3 above. No action needed; any doc comment still citing a
  future "Aura Node" target should be corrected to explain this the way
  `EntityEldritchGuardian`'s was for the dimension, if one is found.
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

- **World generation, part 1** — closed 2026-09-14. User asked to tackle
  "the world generation stuff we postponed." Turned out much larger than
  a single item once actually investigated - 4 real, independent pieces
  closed this pass:
  - **Biome-weighted aura generation** - `AuraHandler`'s own doc comment
    had already flagged this ("every chunk seeds with the same flat
    baseline... the original weighted this by `BiomeDictionary` tags...
    that isn't ported"). New `thaumcraft.common.world.biomes.BiomeHandler`
    replaces the stale, never-compiled 1.12.2 leftover at the same path,
    re-derived onto NeoForge's own real `c:` common biome tags - a clean
    near-1:1 modern replacement for `BiomeDictionary.Type`. Found and
    fixed a real quirk while reading the original: it registered `DRY`
    twice, and real `HashMap` overwrite semantics meant only the second
    (ENTROPY, not the first's FIRE) was ever really in play.
  - **Primal crystal cluster worldgen** - a real branch of
    `ThaumcraftWorldGenerator#generateOres` never ported alongside the
    cinnabar/quartz/amber ores it sat next to. New `CrystalClusterFeature`
    (real modern `Feature`, JSON-driven placement) picks one real Aspect
    per vein (biome-weighted 2/3 of the time via the new `BiomeHandler`,
    uniformly random among the 6 primal aspects otherwise - Flux
    deliberately excluded, matching the original's own real bound).
  - **Cinderpearl worldgen** - the block's own doc comment already named
    the exact plan (vanilla's own `RandomPatchFeature`); just executed it.
  - **Cultist Portal (Lesser) natural spawn** - the entity was already
    real and fully shipped with zero natural spawn trigger anywhere. New
    `CultistPortalSpawnFeature` restores the true original's own rare
    surface spawn.

  See commits "Give aura generation its real per-biome weighting", "Give
  primal crystal clusters their real natural worldgen", "Give cinderpearl
  real worldgen and restore the cultist portal spawn". Deliberately
  deferred, not closed: mound generation (a ~2500-line hand-placed
  schematic, real but low value for the effort - still open, see "Needs
  further triage" above). The 3 plants tied to Magical Forest and the
  custom-biomes question itself both got picked up the same day - see
  "World generation, part 2" below.

- **World generation, part 2 - the 3 custom biomes** — closed 2026-09-14,
  same session as part 1. Real end-to-end: `thaumcraft.common.world.biomes.ThaumcraftBiomes`
  registers Eerie/Magical Forest/Eldritch as real biome JSON (climate,
  colors, mob spawns, vegetation - re-derived directly from the true
  original's own `BiomeGenEerie`/`BiomeGenMagicalForest`/`BiomeGenEldritch`,
  none of which were ever really about custom terrain shape - see that
  class's own doc comment for the full story), plus the 2 real plants
  exclusive to Magical Forest (`BlockPlantShimmerleaf`/`BlockPlantVishroom`;
  `grassAmbient` deliberately isn't a distinct block - its only real
  distinguishing behavior was a client-only particle effect).

  The real hard part, investigated with the user directly before writing
  any code: modern Minecraft's overworld biome placement
  (`multi_noise_biome_source_parameter_list`) has no plain-datapack
  extension point at all (verified directly against the real codec, not
  assumed) - the only real path is reimplementing vanilla's own internal
  `OverworldBiomeBuilder` (confirmed by reading Biomes O' Plenty's real
  source: ~870 lines, and non-cooperative with other biome mods - first-
  registered wins, full stop). Took a real dependency instead: Biolith
  (TerraformersMC), a small library built for exactly this, with a real
  additive datapack placement API, bundled invisibly via NeoForge's own
  `jarJar` mechanism - **this project's first-ever external dependency**,
  a real, deliberate, discussed precedent, not a casual addition.

  Real bugs the verification loop itself caught: biome JSON's own real
  `carvers` field is a map, not an array (`{}` not `[]` - a naive read of
  the schema gets this wrong); 2 real mob-category mismatches NeoForge
  warns about at startup (Bat is really `AMBIENT` not `CREATURE`; Pech is
  really `CREATURE` not `MONSTER` - this port's own already-established
  modern categorization, not the original's coarser 1.12.2 list
  membership); 6 Thaumcraft mobs had never been given a real
  `RegisterSpawnPlacementsEvent` registration at all (a real, separate,
  pre-existing gap this session's own spawn lists happened to surface,
  now fixed). Real, confirmed-by-direct-attempt limitation: `runGameTestServer`
  never exposes a real configured `MultiNoiseBiomeSource`, even via the
  overworld's own freshly-fetched registry entry - real terrain placement
  needs a real `runClient` exploration pass, tracked in the project's own
  new [[thaumcraft-worldgen-manual-verification]] memory file.

  See commits "Add Biolith and the 2 real Magical-Forest-only plants" and
  "Ship the real Eerie/Magical Forest/Eldritch biomes and their real
  overworld placement".

- **World generation, part 3 - mound dungeon** — closed 2026-09-14.
  `WorldGenMound` was deferred in part 1 as "too big" purely by its
  2560-line decompiled file size, without actually reading it - re-checked
  directly this pass and it turned out to be purely mechanical: ~2441
  static `setBlockState` calls (23 distinct block expressions, translated
  by a hand-written script), not algorithmic complexity, plus only 5 real
  dynamic positions computed at runtime. The static bulk became a real
  modern structure NBT template (`data/thaumcraft/structure/mound.nbt`,
  19x16x19, hand-written gzip'd NBT since no Python NBT library was
  available offline); new `MoundFeature` places it via the real
  `StructureTemplateManager` API, then places the 5 dynamic extras with the
  exact same real weighted-random logic and offsets as the original: 2
  tiered loot-crate/urn spots, a chest (1-in-3 trapped + buried TNT)
  carrying the real vanilla `minecraft:chests/simple_dungeon` loot table,
  and 2 monster spawners (Skeleton/Zombie). Real placement: the original's
  own real 1-in-100-per-chunk-attempt rarity, gated by a
  `neoforge:add_features` biome modifier. See commit "Give the true
  original's mound dungeon its real worldgen".

- **Liquid Death** — closed 2026-09-14. User asked to move to "finite
  fluids" next; investigating found the real finite-fluid engineering work
  (`BlockFluxGoo`) was already shipped 2026-09-03, and `BlockPurifyingFluid`
  (same finite engine, a separate real fluid) was already shipped too, in
  an earlier uncaptured session - only `liquidDeath` genuinely remained,
  and its own true original base class (`BlockFluidClassic`, Forge's OLD
  infinite-source-block wrapper) meant it never actually needed the
  finite-fluid engine at all. Built real modern `FluidType`/`Fluid`
  (source+flowing)/`LiquidBlock`/`BucketItem` registrations
  (`ThaumcraftFluids`, new) - this port's first vanilla-style fluid.
  `BlockLiquidDeath#entityInside` replicates the original's own real
  `onEntityCollidedWithBlock` (dissolve damage tapering with the real
  `FluidState` amount, real horizontal-motion slow); a dissolve kill's own
  real consequence (victim drops its essentia as crystal-essence stacks)
  was already shipped and needed no new wiring. Real content chain mostly
  already in place once checked: research entry, bucket icon/texture/item
  model, aspect-tag precedent. Added the real Crucible recipe (empty bucket
  + Death 100/Alchemy 20/Entropy 50) and fixed a stale, silently-
  unparseable uppercase recipe reference in `alchemy.json`'s own
  `LIQUIDDEATH` stage. See commit "Give Liquid Death its real vanilla-style
  fluid - the first non-finite one".

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

- **Real CodeChicken-precision click targeting, no library needed** —
  closed 2026-09-14, superseding the same day's own earlier "Tube One-way"
  closure below. User asked directly how CodeChickenLib's raytracer
  actually worked, suspecting the click-to-cycle placeholders were a real
  step down from the original - they were. Traced the library to source:
  `RayTracer`/`IndexedCuboid6` was a hand-rolled per-face-plane ray/box
  test, needed only because 1.12.2 blocks had exactly one fixed collision/
  selection box with no way to expose several small distinguishable
  sub-regions. Modern `VoxelShape` (unions of boxes) + `AABB#clip` (vanilla
  now ships the exact same ray-vs-box computation natively) make the whole
  library unnecessary - not a simplification, a capability gap that closed
  itself. New `CuboidTargeting` (`thaumcraft.common.lib.utils`) is the real
  replacement: given a player and a list of labeled real-world AABBs,
  finds whichever one their current line of sight hits nearest, within
  their real block-interaction reach - about a tenth the code of the
  original library. Three real devices restored on top of it:
  - **Redstone Relay**: 2 genuinely separate clickable dials (IN/OUT)
    restored, replacing "plain click cycles OUT, shift-click cycles IN."
  - **Levitator**: turned out to be two different shapes conflated into
    one made-up approximation - its real solid collision (near-full block,
    inset only on the face it blows air through) and its real small button
    nub (opposite "control panel" side) are separate again, both
    re-derived from the true original's own numbers.
  - **Tube family** (base/Filter/Restrict/Oneway): the big one - real
    per-face open/close toggling and the real central "cycle facing"
    button both restored, wand-gated via the already-real
    `IInteractWithCaster` hook (matching the true original's own actual
    requirement, unlike Levitator/Redstone Relay which never needed a
    wand). `facing` moved from `TileTubeOneway` up onto the shared
    `TileTube` base, matching the true original's own class layout.
  See commits "Restore real CodeChicken-precision click targeting - no
  library needed" and "Restore the tube family's real wand-gated per-face
  click UI". Also surfaced a real, separate, pre-existing bug - see the
  new "Essentia tube chains can deadlock on a suction tie" entry above.

- **Tube One-way** (`tubeOneway`) — closed 2026-09-14, the last "Needs
  further triage" item with a real, well-defined blocker. See the
  "Needs further triage" section above for the full story - the claimed
  raytracing blocker was stale (Levitator/Redstone Relay had already
  solved it with a plain click-to-cycle substitute, just never applied
  here); real directional suction/flow restriction re-derived from the
  true original's own source. See commit "Give Tube One-way its real
  directional suction/flow mechanic".

- **Label dyeing recipe family** — closed 2026-09-14, the last "Confirmed
  real gaps" entry. `ItemLabel`'s own doc comment already flagged this as
  a deliberate cut: the original dyed a blank label with a specific aspect
  using a filled Phial matched by NBT (`IngredientNBTTC`, a custom 1.12.2
  ingredient type), and washed it back to blank with a second recipe.
  Rather than build a whole `ICustomIngredient` plumbing layer for one
  item (a phial is one item regardless of aspect - nothing at the JSON
  level can tell them apart), both are real `CustomRecipe`s checking the
  real `ASPECTS` component directly - the same technique
  `InfusionPotionRecipe`/`RecipeTripleMeatTreat` already established.
  `LabelDyeRecipe` matches any blank label plus any single-aspect filled
  phial and derives its output aspect from whichever the phial actually
  carries (one class covers what the original expressed as one recipe per
  aspect); the phial itself comes back unconsumed via a real
  `Recipe#getRemainingItems` override, the same real mechanism vanilla's
  own bucket/glass-bottle recipes use, replacing the original's `grow(1)`-
  before-consumption trick. See commit "Give labels their real dye/clear
  recipe family".

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

- **`effectSap`** — closed 2026-09-15. The user asked directly what this
  block was; explaining it surfaced that its "low priority, no real
  consumer" reasoning was stale - `EntityEldritchWarden` is already a fully
  shipped boss, just missing 2 of its own real mechanics specifically
  because this block didn't exist yet. New `BlockEffectSap` (an invisible,
  self-destroying ground trap - Wither/Slowness/Hunger on touch for any
  real non-Eldritch living entity) replaces the one real, actually-consumed
  slice of the true original's shared `BlockEffect` (`effect_glimmer`
  doesn't need porting - its own 2 real placers already use real vanilla
  `Blocks.LIGHT`, a strictly better modern fit, already documented on those
  classes; `effect_shock` has zero real placers anywhere in the whole true
  original, confirmed by a full-tree search - never wired up even
  upstream). Restored 3 real cuts: `FocusEffectCurse`'s block-hit branch
  (blankets full-cube surfaces near the impact point with sap), the
  Warden's own constant per-tick self-seeding under its feet, and the
  Warden's real one-shot "field frenzy" phase (a real vanilla absorption
  shield - 66% max health, regenerating, automatically soaking damage via
  vanilla's own pipeline - whose first depletion triggers a real 150-tick
  invulnerability window, a real teleport to a safe spot near its own
  restricted home, and a real expanding/thickening sap spiral, exact
  original radius/density formulas). Also found and fixed a real, separate
  bug along the way: the Warden was missing `implements IEldritchMob` (the
  true original always had it), so other Eldritch mobs never recognized it
  as one of their own. See commit "Give effect_sap its real port and
  restore 3 real cuts it was blocking".

- **`TaintHelper#spreadFibres`'s full real branch structure** — closed
  2026-09-15. Investigating the sweep's own "leaf-touch" candidate found
  the true original's real method had 3 whole branches missing, not one:
  leaf-touch (sprout `taint_feature` near a real touching `taint_log`),
  hemmed-in-taint conversion (nearby log/mushroom-ish/ground-ish/rock-ish
  surfaces convert into `taint_log`/`taint_crust`/`taint_soil`/
  `taint_rock` once really surrounded by taint), and taint-seed-edge spawn
  (an existing soil/rock patch at a real seed's own spread-radius fringe
  can sprout a whole new `EntityTaintSeed`). All 3 target dependency sets
  shipped together in the 2026-09-03 taint-terrain arc; nobody had gone
  back to wire the rest of the method up once they landed. New
  `BlockTaintFibre#isHemmedByTaint`. Real, deliberate simplification: the
  original's own 1.12.2 `Material`-based surface classification (no modern
  equivalent) is approximated with real vanilla block tags/identity checks
  of the same rough shape. Found and fixed a real, separate bug while
  chasing a stubborn test flake: `EntityTaintSeed`/`EntityTaintSeedPrime`
  were both missing their real hitbox override (`setSize(1.5, 1.25)`/
  `(2.0, 2.0)` in the true original), silently running on vanilla
  `Monster`'s own default dimensions instead - never caught before because
  nothing had tried to spawn one naturally until this restoration's own new
  test did. See commit "Give TaintHelper#spreadFibres its full real branch
  structure back".

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
