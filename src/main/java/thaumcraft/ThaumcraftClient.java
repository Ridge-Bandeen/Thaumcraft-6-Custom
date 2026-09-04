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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ThaumcraftMenus.ARCANE_WORKBENCH.get(), GuiArcaneWorkbench::new);
        event.register(ThaumcraftMenus.FOCAL_MANIPULATOR.get(), thaumcraft.client.gui.GuiFocalManipulator::new);
        event.register(ThaumcraftMenus.RESEARCH_TABLE.get(), thaumcraft.client.gui.GuiResearchTable::new);
        event.register(ThaumcraftMenus.GOLEM_BUILDER.get(), thaumcraft.client.gui.GuiGolemBuilder::new);
        event.register(ThaumcraftMenus.SEAL_CONFIG.get(), thaumcraft.client.gui.SealConfigScreen::new);
        event.register(ThaumcraftMenus.TURRET_CONFIG.get(), thaumcraft.client.gui.TurretConfigScreen::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_PROJECTILE.get(), RenderFocusProjectile::new);
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_MINE.get(), RenderFocusProjectile::new);
        event.registerEntityRenderer(ThaumcraftEntities.FOCUS_CLOUD.get(), RenderFocusProjectile::new);
        // Alumentum renders as a real spinning item icon (vanilla's own snowball/egg renderer), unlike
        // the invisible focus-carrier entities above.
        event.registerEntityRenderer(ThaumcraftEntities.ALUMENTUM.get(), ThrownItemRenderer::new);
        // Real vanilla-item spinning-icon renderer too, matching Alumentum above - the original's own
        // EntityBottleTaint really is an EntityThrowable, no bespoke model.
        event.registerEntityRenderer(ThaumcraftEntities.BOTTLE_TAINT.get(), ThrownItemRenderer::new);
        // Real vanilla ItemEntityRenderer, unmodified - EntityFollowingItem really is a plain ItemEntity
        // subtype in this port (see its own doc comment), so it just gets the exact same real rendering any
        // dropped item does, for free.
        event.registerEntityRenderer(ThaumcraftEntities.FOLLOWING_ITEM.get(), net.minecraft.client.renderer.entity.ItemEntityRenderer::new);
        // No renderer registered at all until now - a real, latent crash risk (any entity type without one
        // throws the first time the client actually tries to render an instance), not just a missing model.
        // The original's own WISP/FLUX_RIFT renderers are both pure billboard/quad or raw-shader client FX
        // with no real 3D model at all (checked directly, not assumed) - same permanent "no client FX"
        // territory this port has kept all session, so these two get the same safe no-visible-model
        // placeholder as FOCUS_PROJECTILE above, not a temporary gap.
        event.registerEntityRenderer(ThaumcraftEntities.WISP.get(), RenderFocusProjectile::new);
        event.registerEntityRenderer(ThaumcraftEntities.FLUX_RIFT.get(), RenderFocusProjectile::new);
        // Real geometry now - see RenderGrapple's own doc comment (the rope/tether visual is dropped, the
        // hook itself is real).
        event.registerEntityRenderer(ThaumcraftEntities.GRAPPLE.get(), thaumcraft.client.render.RenderGrapple::new);
        // Real geometry now - both share ModelFireBat, see that class's own doc comment (matches the
        // original's own real one-model-two-renderers shape).
        event.registerEntityRenderer(ThaumcraftEntities.FIRE_BAT.get(), thaumcraft.client.render.RenderFireBat::new);
        event.registerEntityRenderer(ThaumcraftEntities.SPELL_BAT.get(), thaumcraft.client.render.RenderSpellBat::new);
        // Real geometry now - see RenderEldritchCrab's own doc comment.
        event.registerEntityRenderer(ThaumcraftEntities.ELDRITCH_CRAB.get(), thaumcraft.client.render.RenderEldritchCrab::new);
        // Real, permanent cut, same reasoning as WISP/FLUX_RIFT above - both original portal renderers
        // extend Render directly and hand-draw an animated Tessellator billboard, no 3D model at all.
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
        // No visible model - see RenderFocusProjectile's own doc comment (shared with FOCUS_PROJECTILE/FOCUS_MINE/GOLEM_ORB).
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SWARM.get(), RenderFocusProjectile::new);
        // Real geometry now - see RenderTaintacle/RenderTaintSeed's own doc comments (real per-registration
        // shadow size/chain length, matching the original's own real per-registration shape).
        event.registerEntityRenderer(ThaumcraftEntities.TAINTACLE.get(), context -> new thaumcraft.client.render.RenderTaintacle(context, 0.6f, 10));
        event.registerEntityRenderer(ThaumcraftEntities.TAINTACLE_SMALL.get(), context -> new thaumcraft.client.render.RenderTaintacle(context, 0.2f, 6));
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SEED.get(), context -> new thaumcraft.client.render.RenderTaintSeed(context, 0.4f));
        event.registerEntityRenderer(ThaumcraftEntities.TAINT_SEED_PRIME.get(), context -> new thaumcraft.client.render.RenderTaintSeed(context, 0.6f));
        // No visible model - see RenderFocusProjectile's own doc comment (shared with FOCUS_PROJECTILE/FOCUS_MINE).
        event.registerEntityRenderer(ThaumcraftEntities.GOLEM_ORB.get(), RenderFocusProjectile::new);
        // Real geometry now - see ModelCrossbow/RenderTurretCrossbow's own doc comments.
        event.registerEntityRenderer(ThaumcraftEntities.TURRET_CROSSBOW.get(), thaumcraft.client.render.RenderTurretCrossbow::new);
        // No renderer yet - the original's own real crossbow_advanced.obj mesh needs a genuine custom
        // OBJ-mesh renderer, see EntityTurretCrossbowAdvanced's own doc comment for why.
        event.registerEntityRenderer(ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get(), RenderFocusProjectile::new);
        // Real geometry now - see ModelArcaneBore/RenderArcaneBore's own doc comments.
        event.registerEntityRenderer(ThaumcraftEntities.ARCANE_BORE.get(), thaumcraft.client.render.RenderArcaneBore::new);
        // First real BlockEntityRenderer this whole port has needed - see its own doc comment.
        event.registerBlockEntityRenderer(thaumcraft.common.registry.ThaumcraftBlockEntities.BANNER.get(), thaumcraft.client.render.TileBannerRenderer::new);
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

        // tube_filter_core.json's center node (tintIndex 1) is colored by the tube's own current filter
        // aspect - neutral gray when unset, same "read the real block entity state" idea as the tint
        // handlers above, just via the tile instead of item data.
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) return 0x808080;
            if (!(level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.essentia.TileTubeFilter tile)) return 0x808080;
            thaumcraft.api.aspects.Aspect aspect = tile.getAspectFilter();
            return aspect != null ? aspect.getColor() : 0x808080;
        }, BlocksTC.tubeFilter.get());
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            return block instanceof BlockCandle candle ? candle.dye.getTextureDiffuseColor() : -1;
        }, ItemsTC.candles.values().stream().map(net.neoforged.neoforge.registries.DeferredItem::get)
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
    }
}
