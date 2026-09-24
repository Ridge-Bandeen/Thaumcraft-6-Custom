package thaumcraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.golems.parts.GolemAddon;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemLeg;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.basic.BlockCandle;
import thaumcraft.client.gui.GuiArcaneWorkbench;
import thaumcraft.client.render.RenderFocusProjectile;
import thaumcraft.client.render.RenderThaumcraftGolem;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Client-only entry point (replaces the old {@code ClientProxy}). Does not load on a dedicated
 * server, so client-only classes (renderers, GUIs, etc.) are safe to reference here as they get
 * ported.
 */
@Mod(value = Thaumcraft.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class ThaumcraftClient {
    public ThaumcraftClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(thaumcraft.client.gui.KeyBindings.CHANGE_CASTER_FOCUS);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Thaumcraft.LOGGER.info("Thaumcraft {} client setup", Thaumcraft.VERSION);

        // caster_basic.json's item model switches on this ("predicate": {"focus": 0|1}) - reused
        // unchanged from the original 1.12.2 asset.
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.casterBasic.get(), ResourceLocation.withDefaultNamespace("focus"),
                (stack, level, entity, seed) -> ItemsTC.casterBasic.get().getFocus(stack) != null ? 1.0f : 0.0f));

        // phial.json's model switches on this ("predicate": {"thaumcraft:filled": 0|1}) - same technique as
        // caster_basic's "focus" predicate above.
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.phial.get(), ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "filled"),
                (stack, level, entity, seed) -> stack.has(thaumcraft.api.items.ThaumcraftDataComponents.ASPECTS.get()) ? 1.0f : 0.0f));

        // label.json's model switches on the same predicate, same reasoning as phial.
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.label.get(), ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "filled"),
                (stack, level, entity, seed) -> stack.has(thaumcraft.api.items.ThaumcraftDataComponents.ASPECTS.get()) ? 1.0f : 0.0f));

        // grapple_gun.json's model switches on this ("predicate": {"type": 0|1}), reused unchanged from the
        // original asset - no stored per-stack state needed, EntityGrapple.hasActive already tracks live
        // whether this player currently has an active (fired) grapple, see that class's own doc comment.
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.grappleGun.get(), ResourceLocation.withDefaultNamespace("type"),
                (stack, level, entity, seed) -> entity instanceof net.minecraft.world.entity.LivingEntity living
                        && thaumcraft.common.entities.projectile.EntityGrapple.hasActive(living) ? 1.0f : 0.0f));

        // primordial_pearl.json's model switches on the same "type" predicate, same technique as
        // grapple_gun's above - see ItemPrimordialPearl's own doc comment for the 0/1/2 tier meaning.
        event.enqueueWork(() -> ItemProperties.register(ItemsTC.primordialPearl.get(), ResourceLocation.withDefaultNamespace("type"),
                (stack, level, entity, seed) -> thaumcraft.common.items.curios.ItemPrimordialPearl.typeProperty(stack)));

        // Real translucent render layer for the goo texture's own real alpha - the first custom block this
        // port has needed one for (every other block so far has been either fully opaque or fully cutout).
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                BlocksTC.fluxGoo.get(), net.minecraft.client.renderer.RenderType.translucent()));
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        // Real still/flowing texture for liquid_death - the true original's own real Fluid definition used
        // the same real animatedglow texture for both (see ThaumcraftFluids' own doc comment), no tint.
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "blocks/animatedglow");

            @Override
            public ResourceLocation getStillTexture() {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return TEXTURE;
            }
        }, thaumcraft.common.registry.ThaumcraftFluids.LIQUID_DEATH_TYPE);
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ThaumcraftMenus.ARCANE_WORKBENCH.get(), GuiArcaneWorkbench::new);
        event.register(ThaumcraftMenus.FOCAL_MANIPULATOR.get(), thaumcraft.client.gui.GuiFocalManipulator::new);
        event.register(ThaumcraftMenus.RESEARCH_TABLE.get(), thaumcraft.client.gui.GuiResearchTable::new);
        event.register(ThaumcraftMenus.GOLEM_BUILDER.get(), thaumcraft.client.gui.GuiGolemBuilder::new);
        event.register(ThaumcraftMenus.SEAL_CONFIG.get(), thaumcraft.client.gui.SealConfigScreen::new);
        event.register(ThaumcraftMenus.TURRET_CONFIG.get(), thaumcraft.client.gui.TurretConfigScreen::new);
        event.register(ThaumcraftMenus.THAUMONOMICON.get(), thaumcraft.client.gui.ThaumonomiconScreen::new);
        event.register(ThaumcraftMenus.PECH_TRADE.get(), thaumcraft.client.gui.GuiPechTrade::new);
        event.register(ThaumcraftMenus.SPA.get(), thaumcraft.client.gui.GuiSpa::new);
        event.register(ThaumcraftMenus.FOCUS_POUCH.get(), thaumcraft.client.gui.GuiFocusPouch::new);
        event.register(ThaumcraftMenus.HAND_MIRROR.get(), thaumcraft.client.gui.GuiHandMirror::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_PROJECTILE.get(), RenderFocusProjectile::new);
        // Real caltrop-shaped geometry + pulsing red tint restored 2026-09-16 - see RenderFocusMine's own doc
        // comment. This was a real, previously-undocumented gap (not the same permanent "no client FX" case
        // FOCUS_PROJECTILE/FOCUS_CLOUD below genuinely are), found via a direct cross-check of every entity
        // type hiding behind the RenderFocusProjectile placeholder.
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_MINE.get(), thaumcraft.client.render.RenderFocusMine::new);
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_CLOUD.get(), RenderFocusProjectile::new);
        // Alumentum renders as a real spinning item icon (vanilla's own snowball/egg renderer), unlike
        // the invisible focus-carrier entities above.
        event.registerEntityRenderer(ThaumcraftEntities.ALUMENTUM.get(), ThrownItemRenderer::new);
        // Real vanilla-item spinning-icon renderer too, matching Alumentum above - the original's own
        // EntityBottleTaint really is an EntityThrowable, no bespoke model.
        event.registerEntityRenderer(ThaumcraftEntities.BOTTLE_TAINT.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ThaumcraftEntities.CAUSALITY_COLLAPSER.get(), ThrownItemRenderer::new);
        // Real vanilla ItemEntityRenderer, unmodified - EntityFollowingItem really is a plain ItemEntity
        // subtype in this port (see its own doc comment), so it just gets the exact same real rendering any
        // dropped item does, for free.
        event.registerEntityRenderer(ThaumcraftEntities.FOLLOWING_ITEM.get(), net.minecraft.client.renderer.entity.ItemEntityRenderer::new);
        // No renderer registered at all until now - a real, latent crash risk (any entity type without one
        // throws the first time the client actually tries to render an instance), not just a missing model.
        // The original's own WISP/FLUX_RIFT renderers are both pure billboard/quad or raw-shader client FX
        // with no real 3D model at all (checked directly, not assumed), so neither needs a real
        // EntityRenderer#render override - this no-visible-model placeholder (same as FOCUS_PROJECTILE above)
        // stays correct for both. WISP's own real glow quads are restored 2026-09-16, but as real client-local
        // particles tracking the entity (see FXWispGlow's own doc comment) rather than drawn from here - this
        // placeholder is still the right renderer registration for it, not a leftover gap. FLUX_RIFT's own
        // real raw-shader "warp" visual is still a real, deferred gap (see the Custom Mob Models/Renderers
        // TODO entry - a GLSL shader port, not just another billboard).
        event.registerEntityRenderer(ThaumcraftEntities.WISP.get(), RenderFocusProjectile::new);
        // Real wobbling tendril mesh restored 2026-09-17, replacing the placeholder - see
        // thaumcraft.client.render.RenderFluxRift's own doc comment.
        event.registerEntityRenderer(ThaumcraftEntities.FLUX_RIFT.get(), thaumcraft.client.render.RenderFluxRift::new);
        // Real geometry now - see RenderGrapple's own doc comment (the rope/tether visual is dropped, the
        // hook itself is real).
        event.registerEntityRenderer(ThaumcraftEntities.GRAPPLE.get(), thaumcraft.client.render.RenderGrapple::new);
        // Real geometry now - both share ModelFireBat, see that class's own doc comment (matches the
        // original's own real one-model-two-renderers shape).
        event.registerEntityRenderer(ThaumcraftEntities.FIRE_BAT.get(), thaumcraft.client.render.RenderFireBat::new);
        event.registerEntityRenderer(ThaumcraftEntities.SPELL_BAT.get(), thaumcraft.client.render.RenderSpellBat::new);
        // Real geometry now - see RenderEldritchCrab's own doc comment.
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_CRAB.get(), thaumcraft.client.render.RenderEldritchCrab::new);
        // Real animated portal-disc glow restored 2026-09-16 - see FXCultistPortalLesser's own doc comment.
        // Same "the real visual is entirely client-local tracking particles, not this EntityRenderer" shape
        // WISP/GOLEM_ORB/ELDRITCH_ORB above already established - this placeholder is still correct.
        event.registerEntityRenderer(ThaumcraftEntities.CULTIST_PORTAL_LESSER.get(), RenderFocusProjectile::new);
        event.registerEntityRenderer(ThaumcraftEntities.CULTIST_PORTAL_GREATER.get(), RenderFocusProjectile::new);
        event.registerEntityRenderer(ThaumcraftEntities.GOLEM.get(), RenderThaumcraftGolem::new);
        event.registerEntityRenderer(ThaumcraftEntities.THAUMIC_SLIME.get(), thaumcraft.client.render.RenderThaumicSlime::new);
        event.registerEntityRenderer(ThaumcraftEntities.BRAINY_ZOMBIE.get(), thaumcraft.client.render.RenderBrainyZombie::new);
        event.registerEntityRenderer(ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), thaumcraft.client.render.RenderGiantBrainyZombie::new);
        // Real vanilla spider texture/model reused directly (SpiderRenderer<T extends Spider> is generic,
        // no subclass needed) - see ThaumcraftEntities.MIND_SPIDER's own doc comment for why.
        event.registerEntityRenderer(ThaumcraftEntities.MIND_SPIDER.get(), net.minecraft.client.renderer.entity.SpiderRenderer::new);
        event.registerEntityRenderer(ThaumcraftEntities.CULTIST_KNIGHT.get(), thaumcraft.client.render.RenderCultistKnight::new);
        // Shared renderer, see RenderCultistKnight's own doc comment for why the class name predates this reuse.
        event.registerEntityRenderer(ThaumcraftEntities.CULTIST_CLERIC.get(), thaumcraft.client.render.RenderCultistKnight::new);
        event.registerEntityRenderer(ThaumcraftEntities.CULTIST_LEADER.get(), thaumcraft.client.render.RenderCultistLeader::new);
        event.registerEntityRenderer(ThaumcraftEntities.INHABITED_ZOMBIE.get(), thaumcraft.client.render.RenderInhabitedZombie::new);
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_CRAWLER.get(), thaumcraft.client.render.RenderTaintCrawler::new);
        // No visible model - see RenderFocusProjectile's own doc comment (shared with FOCUS_PROJECTILE/FOCUS_CLOUD/GOLEM_ORB).
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SWARM.get(), RenderFocusProjectile::new);
        // Real geometry now - see RenderTaintacle/RenderTaintSeed's own doc comments (real per-registration
        // shadow size/chain length, matching the original's own real per-registration shape).
        event.registerEntityRenderer(ThaumcraftEntities.TAINTACLE.get(), context -> new thaumcraft.client.render.RenderTaintacle(context, 0.6f, 10));
        event.registerEntityRenderer(ThaumcraftEntities.TAINTACLE_SMALL.get(), context -> new thaumcraft.client.render.RenderTaintacle(context, 0.2f, 6));
        // A real, previously-missing registration, found 2026-09-16 while scoping the client renderer layer -
        // EntityTaintacleGiant (the Taint faction's own boss) extends EntityTaintacle already, so it's real,
        // type-compatible with the same RenderTaintacle used above, just with the true original's own real
        // bigger shadow/chain-length values; without this, spawning one would have thrown a real
        // "no renderer registered" error the first time a client ever saw one.
        event.registerEntityRenderer(ThaumcraftEntities.TAINTACLE_GIANT.get(), context -> new thaumcraft.client.render.RenderTaintacle(context, 1.0f, 14));
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SEED.get(), context -> new thaumcraft.client.render.RenderTaintSeed(context, 0.4f));
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SEED_PRIME.get(), context -> new thaumcraft.client.render.RenderTaintSeed(context, 0.6f));
        // Real billboarded glow quad restored 2026-09-16 - see FXGolemOrb's own doc comment. Turned out
        // simpler than ELDRITCH_ORB below (a single tracking quad, not real procedural spike geometry).
        event.registerEntityRenderer(ThaumcraftEntities.GOLEM_ORB.get(), RenderFocusProjectile::new);
        // Real geometry now - see ModelCrossbow/RenderTurretCrossbow's own doc comments.
        event.registerEntityRenderer(ThaumcraftEntities.TURRET_CROSSBOW.get(), thaumcraft.client.render.RenderTurretCrossbow::new);
        // Real advanced-variant OBJ mesh restored 2026-09-16 - see RenderTurretCrossbowAdvanced's own doc
        // comment for the real modern OBJ-loading path this uses.
        event.registerEntityRenderer(ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get(), thaumcraft.client.render.RenderTurretCrossbowAdvanced::new);
        // Real geometry now - see ModelArcaneBore/RenderArcaneBore's own doc comments.
        event.registerEntityRenderer(ThaumcraftEntities.ARCANE_BORE.get(), thaumcraft.client.render.RenderArcaneBore::new);
        event.registerEntityRenderer(ThaumcraftEntities.PECH.get(), thaumcraft.client.render.RenderPech::new);
        // Real spike-ball + glow visual restored 2026-09-16 - see FXEldritchOrbSpikes's own doc comment. Same
        // "the real visual is entirely client-local tracking particles, not this EntityRenderer" shape
        // WISP/GOLEM_ORB above already established - this placeholder is still the correct registration.
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_ORB.get(), RenderFocusProjectile::new);
        // Real hand-modeled mesh restored 2026-09-24 for all 3 - see ModelEldritchGuardian/ModelEldritchGolem's
        // own doc comments.
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_GUARDIAN.get(), thaumcraft.client.render.RenderEldritchGuardian::new);
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_WARDEN.get(), thaumcraft.client.render.RenderEldritchWarden::new);
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_GOLEM.get(), thaumcraft.client.render.RenderEldritchGolem::new);
        // First real BlockEntityRenderer this whole port has needed - see its own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.BANNER.get(), thaumcraft.client.render.TileBannerRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.PEDESTAL.get(), thaumcraft.client.render.TilePedestalRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.RECHARGE_PEDESTAL.get(), thaumcraft.client.render.TileRechargePedestalRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.RESEARCH_TABLE.get(), thaumcraft.client.render.TileResearchTableRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.THAUMATORIUM.get(), thaumcraft.client.render.TileThaumatoriumRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.INFUSION_MATRIX.get(), thaumcraft.client.render.TileInfusionMatrixRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.FOCAL_MANIPULATOR.get(), thaumcraft.client.render.TileFocalManipulatorRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.CRUCIBLE.get(), thaumcraft.client.render.TileCrucibleRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.ALEMBIC.get(), thaumcraft.client.render.TileAlembicRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.DIOPTRA.get(), thaumcraft.client.render.TileDioptraRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.HUNGRY_CHEST.get(), thaumcraft.client.render.TileHungryChestRenderer::new);
        // Real spinning-axle animation restored 2026-09-16 - see TileCentrifugeRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.CENTRIFUGE.get(), thaumcraft.client.render.TileCentrifugeRenderer::new);
        // Real mode indicator + spinning crank gears restored 2026-09-16 - see TilePatternCrafterRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.PATTERN_CRAFTER.get(), thaumcraft.client.render.TilePatternCrafterRenderer::new);
        // Real spinning valve ring/rod + directional flow indicators restored 2026-09-16 - see
        // TileTubeValveRenderer's own doc comment (shared ModelTubeValve geometry across all 3).
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.TUBE_VALVE.get(), thaumcraft.client.render.TileTubeValveRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.TUBE_ONEWAY.get(), thaumcraft.client.render.TileTubeOnewayRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.TUBE_BUFFER.get(), thaumcraft.client.render.TileTubeBufferRenderer::new);
        // Real OBJ mesh + press-ram animation restored 2026-09-16 - see TileGolemBuilderRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.GOLEM_BUILDER.get(), thaumcraft.client.render.TileGolemBuilderRenderer::new);
        // Real linked-portal tunnel effect restored 2026-09-17, reusing vanilla's own End Portal shader - see
        // TileMirrorRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.MIRROR.get(), thaumcraft.client.render.TileMirrorRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.MIRROR_ESSENTIA.get(), thaumcraft.client.render.TileMirrorRenderer::new);
        // Real breathing-bag animation restored 2026-09-17 - see TileBellowsRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.BELLOWS.get(), thaumcraft.client.render.TileBellowsRenderer::new);
        // Real fill-level liquid, aspect label, lid brace/extension, and brain idle animation restored
        // 2026-09-17 - see TileJarRenderer's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.JAR.get(), thaumcraft.client.render.TileJarRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.JAR_VOID.get(), thaumcraft.client.render.TileJarRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.JAR_BRAIN.get(), thaumcraft.client.render.TileJarRenderer::new);
        // Real starfield display restored 2026-09-17, alongside the new custom "ender" shader - see
        // thaumcraft.client.render.EnderShader's own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.HOLE.get(), thaumcraft.client.render.TileHoleRenderer::new);
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.VOID_SIPHON.get(), thaumcraft.client.render.TileVoidSiphonRenderer::new);
    }

    /** Real port of the true original's own real {@code ender.vert}/{@code ender.frag} shader - see
     * {@link thaumcraft.client.render.EnderShader}'s own doc comment. A load failure here (a malformed shader,
     * or a broken driver) just leaves the shader unregistered - every caller checks for that and skips drawing
     * rather than crashing, same real safety property this port's other client-only construction already has. */
    @SubscribeEvent
    static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            thaumcraft.client.render.EnderShader.register(event);
        } catch (java.io.IOException e) {
            Thaumcraft.LOGGER.error("Failed to load the ender shader", e);
        }
    }

    /** One real "additional" model per {@link GolemMaterial} (the trunk/limbs silhouette every golem has),
     * plus one per assembled head/arm/leg/addon part variant - see {@link RenderThaumcraftGolem}'s own doc
     * comment for why this is the modern replacement for the original's bespoke OBJ loader. Registered here
     * (not tied to any block/item) so they bake through the standard model pipeline like any other model -
     * real texture-atlas stitching included - and can be looked up later via {@code ModelManager#getModel}.
     * {@code GolemMaterial}'s and the part registries' own arrays are populated in {@code commonSetup} (well
     * before model baking starts), so they're real and non-empty by the time this fires. */
    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (GolemMaterial material : GolemMaterial.getMaterials()) {
            if (material == null) continue;
            event.register(golemModelLocation("golem/base_" + material.key.toLowerCase()));
            // The dye-color "flag" - a tiny separate model (golem_base_flag.obj) split out of the trunk
            // so it can be tinted independently via RenderThaumcraftGolem's own (r,g,b) draw params; see
            // that class's own doc comment.
            event.register(golemModelLocation("golem/flag_" + material.key.toLowerCase()));
        }
        for (GolemHead head : GolemHead.getHeads()) {
            registerGolemPart(event, "head", head == null ? null : head.key, head == null ? null : head.model);
        }
        for (GolemArm arm : GolemArm.getArms()) {
            registerGolemPart(event, "arm", arm == null ? null : arm.key, arm == null ? null : arm.model);
        }
        for (GolemLeg leg : GolemLeg.getLegs()) {
            registerGolemPart(event, "leg", leg == null ? null : leg.key, leg == null ? null : leg.model);
        }
        for (GolemAddon addon : GolemAddon.getAddons()) {
            registerGolemPart(event, "addon", addon == null ? null : addon.key, addon == null ? null : addon.model);
        }
    }

    /** {@code key}/{@code part} are null for the placeholder slot 0 of each part registry and for the
     * NONE/FIGHTER addons (no real {@link PartModel}) - skipped, nothing to register. Mirrors the naming
     * scheme the {@code part_*.json} model files were generated with, and that {@code RenderThaumcraftGolem}
     * looks them back up with: one fixed model if the part has its own {@link PartModel#getTexture()}, one
     * per {@link GolemMaterial} if it doesn't (painted with the golem's own material texture instead). */
    private static void registerGolemPart(ModelEvent.RegisterAdditional event, String category, String key, PartModel part) {
        if (key == null || part == null) return;
        String base = "golem/part_" + category + "_" + key.toLowerCase();
        if (part.getTexture() != null) {
            event.register(golemModelLocation(base));
        } else {
            for (GolemMaterial material : GolemMaterial.getMaterials()) {
                if (material == null) continue;
                event.register(golemModelLocation(base + "_" + material.key.toLowerCase()));
            }
        }
    }

    private static ModelResourceLocation golemModelLocation(String path) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path), "standalone");
    }

    /** Candles share one grayscale texture across all 16 colors (no per-color art exists, unlike vanilla's
     * own candles) - tinted per-instance here off each block's own {@code dye} field instead. Both the
     * block and item handlers are needed: {@code BlockColors} only covers the in-world block, not the
     * inventory/hand icon. */
    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            net.minecraft.world.level.block.Block block = state.getBlock();
            return block instanceof BlockCandle candle ? candle.dye.getTextureDiffuseColor() : -1;
        }, BlocksTC.candles.values().stream().map(net.neoforged.neoforge.registries.DeferredBlock::get)
                .toArray(net.minecraft.world.level.block.Block[]::new));

        // Same real "one shared grayscale texture, tinted per-instance" shape as the candles above - see
        // BlockNitor's own doc comment.
        event.register((state, level, pos, tintIndex) -> {
            net.minecraft.world.level.block.Block block = state.getBlock();
            return block instanceof thaumcraft.common.blocks.misc.BlockNitor nitor ? nitor.dye.getTextureDiffuseColor() : -1;
        }, BlocksTC.nitor.values().stream().map(net.neoforged.neoforge.registries.DeferredBlock::get)
                .toArray(net.minecraft.world.level.block.Block[]::new));

        // tube_filter_core.json's center node (tintIndex 1) is colored by the tube's own current filter
        // aspect - neutral gray when unset, same "read the real block entity state" idea as the tint
        // handlers above, just via the tile instead of item data.
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) return 0x808080;
            if (!(level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.essentia.TileTubeFilter tile)) return 0x808080;
            thaumcraft.api.aspects.Aspect aspect = tile.getAspectFilter();
            return aspect != null ? aspect.getColor() : 0x808080;
        }, BlocksTC.tubeFilter.get());

        // Same real "one shared grayscale texture, tinted per-instance" shape as the candles above - all 7
        // real crystal-cluster blocks share crystal.png, differentiated only by their own real Aspect color.
        event.register((state, level, pos, tintIndex) -> {
            net.minecraft.world.level.block.Block block = state.getBlock();
            return block instanceof thaumcraft.common.blocks.world.ore.BlockCrystal crystal ? crystal.aspect.getColor() : -1;
        }, BlocksTC.crystals.values().stream().map(net.neoforged.neoforge.registries.DeferredBlock::get)
                .toArray(net.minecraft.world.level.block.Block[]::new));
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            return block instanceof BlockCandle candle ? candle.dye.getTextureDiffuseColor() : -1;
        }, ItemsTC.candles.values().stream().map(net.neoforged.neoforge.registries.DeferredItem::get)
                .toArray(net.minecraft.world.item.Item[]::new));

        event.register((stack, tintIndex) -> {
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            return block instanceof thaumcraft.common.blocks.misc.BlockNitor nitor ? nitor.dye.getTextureDiffuseColor() : -1;
        }, ItemsTC.nitor.values().stream().map(net.neoforged.neoforge.registries.DeferredItem::get)
                .toArray(net.minecraft.world.item.Item[]::new));

        event.register((stack, tintIndex) -> {
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            return block instanceof thaumcraft.common.blocks.world.ore.BlockCrystal crystal ? crystal.aspect.getColor() : -1;
        }, ItemsTC.crystals.values().stream().map(net.neoforged.neoforge.registries.DeferredItem::get)
                .toArray(net.minecraft.world.item.Item[]::new));

        // phial_filled.json's layer1 (the liquid) is tinted per-instance off the phial's own held aspect,
        // same "one shared texture, tinted by data" idea as the candles above. layer0 (the glass) stays
        // untinted (tintIndex != 1 falls through to -1, i.e. no tint).
        net.minecraft.client.color.item.ItemColor aspectTint = (stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            thaumcraft.api.aspects.AspectList aspects = stack.get(thaumcraft.api.items.ThaumcraftDataComponents.ASPECTS.get());
            return aspects != null && aspects.size() > 0 ? aspects.getAspects()[0].getColor() : -1;
        };
        event.register(aspectTint, ItemsTC.phial.get());
        event.register(aspectTint, ItemsTC.label.get());

        // Real robe/void-robe dye tint, matching the original's own handler exactly (layer 0 tinted, every
        // later layer left alone) - the real color now lives in vanilla's own DYED_COLOR data component
        // instead of the original's NBT display.color tag, with the original's own real default
        // (0x6A3880, the classic purple robe) when undyed. See ItemRobeArmor's own doc comment.
        net.minecraft.client.color.item.ItemColor robeTint = (stack, tintIndex) -> tintIndex > 0 ? -1
                : net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack,
                        thaumcraft.common.items.armor.ItemRobeArmor.DEFAULT_ROBE_COLOR);
        event.register(robeTint, ItemsTC.clothChest.get(), ItemsTC.clothLegs.get(), ItemsTC.clothBoots.get(),
                ItemsTC.voidRobeHelm.get(), ItemsTC.voidRobeChest.get(), ItemsTC.voidRobeLegs.get());

        // banner.json's layer1 (the plain cloth) is tinted per-instance off the placed block's own real dye
        // - same real "one shared texture, tinted per-instance" shape the candles/nitor/crystal families
        // above already use, just read via Block.byItem since BlockBannerTC has no block color handler of
        // its own (RenderShape.INVISIBLE means a block color handler would never actually be reached - see
        // TileBannerRenderer's own doc comment for the real in-world tint instead). layer0 (the wall
        // mount)/layer2 (the crest) stay untinted.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            return block instanceof thaumcraft.common.blocks.misc.BlockBannerTC banner && banner.dye != null
                    ? banner.dye.getTextureDiffuseColor() : -1;
        }, ItemsTC.banners.values().stream().map(net.neoforged.neoforge.registries.DeferredItem::get)
                .toArray(net.minecraft.world.item.Item[]::new));
    }
}
