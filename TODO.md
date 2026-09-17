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
      - **`FXSonic` scoped, deferred** (`EntityEldritchWarden`/
        `EntityEldritchGuardian`'s own real "sonic scream" broadcast): unlike
        every other FX class ported so far, the true original's own real
        renderer loads and draws an actual `.obj` 3D mesh (`hemis.obj`, a
        rippling-dome shockwave shape, animated across 15 real
        `ripple<N>.png` frames) via Thaumcraft's own bespoke model loader -
        not a particle billboard trick at all. Both the mesh and its
        textures already sit unused in this port's resource tree (same
        "assets ready" shape everything else has had), and NeoForge ships
        its own real built-in OBJ model loader (`neoforge:obj`) that's the
        likely real "easy way" here - but integrating a baked OBJ model's
        quads into a `Particle#render()` call is genuinely new rendering-
        pipeline work, not just another particle class. Filed under Custom
        Mob Models/Renderers below instead of this Particle FX arc.
    - Remaining true-original FX classes (`FXBlockRunes`/`FXBreakingFade`/
      `FXDigging`/`FXFireMote`/`FXPlane`/`FXSlimyBubble`/`FXSmokeSpiral`/
      `FXSwarmRunes`/`FXVent`/`FXVent2`/`FXVisSparkle`/`FXWispEG`/
      `FXGenericGui`/`FXGenericP2E`/`FXGenericP2P`, plus `FXEssentiaStream`
      which does have a real call site - `RenderEventHandler`'s own tube-
      network essentia-flow visualization, a bigger separate system) not yet
      scoped in detail - none of these had any real, currently-compiled call
      site found during this pass's scoping search, so likely low-value until
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
      - **Real, confirmed gaps, not yet restored** (same
        `RenderFocusProjectile`-placeholder-hiding-a-real-visual pattern as
        `WISP`/`FOCUS_MINE`/`GOLEM_ORB` above, found via the same direct
        true-original cross-check):
        - `ELDRITCH_ORB` (`EntityEldritchOrb`) - genuinely harder than
          `GOLEM_ORB` turned out to be: the true original's own real
          `RenderEldritchOrb` hand-draws a real procedural geometry, not a
          flat quad - 12 randomly-rotated (reseeded to a fixed `187L` each
          frame, so the shape itself is stable/non-flickering despite the
          per-frame re-randomization) untextured triangle-fan spikes
          radiating from the origin, vertex-coloured white-to-grey, spinning
          with `ticksExisted`, PLUS a second real billboarded glow quad on
          top (same family as `GOLEM_ORB`, off `particles.png`). Needs a
          real `Particle#render(VertexConsumer,Camera,float)` override
          building the spike geometry directly (matching `FXArc`'s own real
          custom-geometry precedent), not just `SingleQuadParticle`'s
          single-quad shape - a real, separate, bigger chunk than the other
          orb.
        - `CULTIST_PORTAL_LESSER`/`CULTIST_PORTAL_GREATER`
          (`EntityCultistPortalLesser`/`EntityCultistPortalGreater`) - true
          original's own real `RenderCultistPortalLesser`/
          `RenderCultistPortalGreater` both draw a real animated billboard
          portal-disc effect off a dedicated `cultist_portal.png` texture (a
          real 16-frame strip), non-uniformly scaled (X/Y scale differ,
          `SingleQuadParticle`'s own single `quadSize` won't cover this
          directly - needs a real custom `render()` override like
          `ELDRITCH_ORB` above, not a plain `FXWispGlow`-style reuse).
          Genuinely bigger than the other 2 orbs/`WISP` for a second real
          reason, checked directly against this port's own current entity
          code (not assumed): the true original's own real animation math
          reads `activeCounter` (real ticks-since-activation, drives scale/
          bob) and `pulse` (a real decaying "just spawned a minion" flash),
          neither of which exist on this port's `EntityCultistPortalLesser`/
          `EntityCultistPortalGreater` yet (only `isActive()`/a differently-
          shaped `stageCounter` do) - restoring this real visual means adding
          2 small real fields to both entity classes first, not just writing
          a renderer. `hurtTime` doesn't need adding - it's a real vanilla
          `LivingEntity` field already available for free.
        - `TURRET_CROSSBOW_ADVANCED` (`EntityTurretCrossbow`, the advanced
          variant) - true original's own real `RenderTurretCrossbowAdvanced`
          loads and draws a real `.obj` mesh via the true original's own
          bespoke `AdvancedModelLoader`/`IModelCustom` - the SAME real OBJ
          model-loading infrastructure gap `FXSonic` already identified
          (NeoForge's own built-in `neoforge:obj` loader is the likely real
          "easy way" for both once someone builds it) - these two should
          likely be scoped/built together as one real "OBJ model loading"
          mini-project rather than separately. The regular (non-advanced)
          `TURRET_CROSSBOW` already has a real, different, already-ported
          renderer - only the advanced variant is the gap.
      - **`FLUX_RIFT` (`EntityFluxRift`) - real, deliberately deferred, not
        a quick win.** True original's own real `RenderFluxRift` is
        genuinely different from every other case here: it renders via a
        real custom GLSL shader (`ARBShaderObjects`/a real shader-callback
        mechanism feeding the player's own yaw/pitch into the shader for a
        camera-relative warp/distortion effect on a starfield texture), not
        a billboard quad or a skeletal model at all. Porting this means real
        modern shader-pipeline work (NeoForge's own `ShaderInstance`/
        `PostChain` or similar), a substantially different kind of task from
        every other item in this section - real, correctly scoped as its own
        separate future chunk once someone deliberately wants to tackle
        shader-based effects, not bundled in here.
    - `FXSonic` (the Eldritch Warden/Guardian's own real sonic-scream
    shockwave, see the Particle FX section above) belongs here too, not in
    Particle FX proper - real `.obj` mesh rendering (`hemis.obj` + 15
    `ripple<N>.png` frames, both already present unused), likely via
    NeoForge's own built-in `neoforge:obj` model loader rather than a
    hand-rolled parser (the same real infrastructure `TURRET_CROSSBOW_
    ADVANCED` above also needs) - integrating a baked model into a
    `Particle#render()`/`EntityRenderer#render()` call is genuinely new
    pipeline work worth its own real scoping pass, not a quick add-on to an
    existing particle class.
    - The much larger remaining bulk of `client/renderers/*` (custom mob
    *models* specifically - `ModelEldritchGolem`-style skeletal meshes,
    armor models, tile-entity renderers) was NOT swept in this pass - this
    scoping pass only checked entities currently hidden behind the
    `RenderFocusProjectile` invisible placeholder, which is a real, bounded,
    high-confidence subset (a wrong/missing renderer is either invisible or
    a hard crash, both cheap to detect by direct cross-check) but not the
    entirety of "Custom Mob Models/Renderers" as a whole - a real full sweep
    of already-registered-but-possibly-simplified renderers (real vs.
    placeholder model detail/animation fidelity) is still a separate,
    larger, not-yet-started piece of work.
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
