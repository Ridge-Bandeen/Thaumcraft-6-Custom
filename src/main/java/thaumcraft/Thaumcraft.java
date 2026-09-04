package thaumcraft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import thaumcraft.gametest.ThaumcraftGameTests;
import thaumcraft.common.world.aura.AuraHandler;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.ThaumcraftAttributes;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.registry.ThaumcraftMenus;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.common.items.casters.foci.FocusEffectAir;
import thaumcraft.common.items.casters.foci.FocusEffectBreak;
import thaumcraft.common.items.casters.foci.FocusEffectCurse;
import thaumcraft.common.items.casters.foci.FocusEffectEarth;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import thaumcraft.common.items.casters.foci.FocusEffectFrost;
import thaumcraft.common.items.casters.foci.FocusEffectHeal;
import thaumcraft.common.items.casters.foci.FocusMediumBolt;
import thaumcraft.common.items.casters.foci.FocusMediumCloud;
import thaumcraft.common.items.casters.foci.FocusMediumMine;
import thaumcraft.common.items.casters.foci.FocusMediumProjectile;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftResearchCategories;
import thaumcraft.common.registry.ThaumcraftDustTriggers;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.common.ThaumcraftInternalMethodHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import thaumcraft.common.tiles.devices.TileRechargePedestal;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.common.lib.network.fx.PacketFXSlash;
import thaumcraft.common.tiles.devices.TileArcaneEar;

/**
 * NeoForge 1.21.1 entry point.
 *
 * This replaces the 1.12.2 {@code @Mod}/{@code @SidedProxy}/FML-lifecycle-event class of the
 * same name. The bulk of Thaumcraft's logic (registries, world gen, GUIs, networking, etc.) is
 * being ported package by package from the 1.12.2 source tree still sitting alongside this file;
 * see build.gradle's sourceSets include list for what has been wired back into the build so far.
 */
@Mod(Thaumcraft.MODID)
public class Thaumcraft {
    public static final String MODID = "thaumcraft";
    public static final String MODNAME = "Thaumcraft";
    public static final String VERSION = "6.1.BETA26";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Thaumcraft(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegisterGameTests);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerEntityAttributes);
        NeoForge.EVENT_BUS.register(this);

        ThaumcraftAttributes.ATTRIBUTES.register(modEventBus);
        ThaumcraftArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ThaumcraftDataComponents.DATA_COMPONENTS.register(modEventBus);
        BlocksTC.BLOCKS.register(modEventBus);
        ItemsTC.ITEMS.register(modEventBus);
        ThaumcraftBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ThaumcraftMenus.MENUS.register(modEventBus);
        ThaumcraftCapabilities.ATTACHMENT_TYPES.register(modEventBus);
        ThaumcraftEntities.ENTITY_TYPES.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftMobEffects.MOB_EFFECTS.register(modEventBus);
        ThaumcraftGameTests.register(modEventBus);

        // Installs the real IInternalMethodHandler over ThaumcraftApi's DummyInternalMethodHandler default -
        // see ThaumcraftInternalMethodHandler's own doc comment for what this actually unlocks (AuraHelper,
        // mainly). Done here (constructor) rather than commonSetup since AuraHelper could plausibly be
        // called very early.
        ThaumcraftApi.internalMethods = new ThaumcraftInternalMethodHandler();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Thaumcraft {} common setup (1.21.1 port in progress)", VERSION);

        // Research category metadata (thaumcraft.api.research.ResearchCategories) - see
        // ThaumcraftResearchCategories' own doc comment for scope (category tabs only, no research
        // entries/pages yet).
        ThaumcraftResearchCategories.register();

        // The real item-to-aspect object-tag table - see ThaumcraftObjectTags's own doc comment. Moved here
        // (commonSetup, not the constructor) once an entry needed to reference a Thaumcraft-registered item
        // (BlocksTC.cinderpearl) rather than only vanilla Items/Tags.Items constants - a DeferredHolder's
        // #get() throws before its RegisterEvent has actually fired, which hasn't happened yet inside the
        // mod constructor itself.
        thaumcraft.common.lib.crafting.ThaumcraftObjectTags.init();

        // Its entity-tag sibling - see ThaumcraftEntityTags' own doc comment. No registry-timing dependency
        // of its own (every entry keys off a vanilla EntityType constant), but grouped here with the
        // object-tag table it mirrors rather than in the constructor.
        thaumcraft.common.lib.crafting.ThaumcraftEntityTags.init();

        // REFINING's cluster-substitution table - see ToolEvents' own doc comment. Same registry-timing
        // reason as the two calls just above.
        thaumcraft.common.lib.events.ToolEvents.initSpecialMining();

        // The real golem part registry (material/head/arm/leg/addon) - see GolemProperties' own doc
        // comment. Same registry-timing reason as the calls above.
        thaumcraft.common.golems.GolemProperties.init();

        // The real seal type registry - see SealHandler#init's own doc comment. Same registry-timing
        // reason as the calls above.
        thaumcraft.common.golems.seals.SealHandler.init();

        // Mirrors the original's own PotionFluxTaint.instance convention - see MobEffectFluxTaint's own doc
        // comment for why the static field exists at all. Same "needs registries populated" timing as the
        // object-tag table above.
        thaumcraft.api.potions.MobEffectFluxTaint.instance = thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT.get();

        // A small, real scan target for ItemThaumometer - see ScanningManager's own doc comment for the
        // overall scope of this "headless" (no book UI) slice of the research system. ItemThaumometer only
        // has two real dispatch paths right now (interactLivingEntity and a block right-click), so the one
        // registered here has to be block-shaped, not item-shaped, to actually be reachable in-game -
        // ScanItem is still ported and usable by a future block/entity-and-item scan target, just not
        // demonstrated with a live registration yet. Scanning the Runic Matrix is an invented substitute
        // trigger for BOOTSTRAVELLER (the original granted it via a Thaumonomicon research-tree node, not
        // ported) - it's the one already-shipped recipe this session has that's real but currently
        // unreachable outside creative mode (TileInfusionMatrix#craftingStart checks its research key
        // against player knowledge, and nothing before this granted BOOTSTRAVELLER to anyone).
        thaumcraft.api.research.ScanningManager.addScannableThing(
                new thaumcraft.api.research.ScanBlock("BOOTSTRAVELLER", BlocksTC.infusionMatrix.get()));

        // ScanAspect (thaumcraft.api.research) registers itself once per real aspect directly from Aspect's
        // own constructor now - see that class's own doc comment for why, and thaumcraft.api.aspects.Aspect's
        // constructor for the real call site. ScanEntity has no such auto-registration in the original (it
        // was always registered per-mob by hand); two real examples here, both real vanilla mobs
        // ThaumcraftEntityTags already carries real aspect data for.
        thaumcraft.api.research.ScanningManager.addScannableThing(
                new thaumcraft.api.research.ScanEntity("!ZOMBIE", net.minecraft.world.entity.EntityType.ZOMBIE));
        thaumcraft.api.research.ScanningManager.addScannableThing(
                new thaumcraft.api.research.ScanEntity("!PIG", net.minecraft.world.entity.EntityType.PIG));

        // Salis Mundus dust triggers (thaumcraft.api.crafting.IDustTrigger) - see
        // ThaumcraftDustTriggers' own doc comment for what this unlocks and what's still deferred.
        ThaumcraftDustTriggers.register();

        // Focus element registry (thaumcraft.api.casters.FocusEngine) - keyed by class, not a
        // DeferredRegister, matching the original 1.12.2 design.
        FocusEngine.registerElement(FocusMediumTouch.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/touch.png"), 11371909);
        FocusEngine.registerElement(FocusEffectFire.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/fire.png"), 16734721);
        FocusEngine.registerElement(FocusEffectFrost.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/frost.png"), 14811135);
        FocusEngine.registerElement(FocusEffectBreak.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/break.png"), 9063176);
        FocusEngine.registerElement(FocusMediumBolt.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/bolt.png"), 11377029);
        FocusEngine.registerElement(FocusEffectHeal.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/heal.png"), 14548997);
        FocusEngine.registerElement(FocusEffectCurse.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/curse.png"), 6946821);
        FocusEngine.registerElement(FocusEffectEarth.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/earth.png"), 5685248);
        FocusEngine.registerElement(FocusMediumProjectile.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/projectile.png"), 11382149);
        FocusEngine.registerElement(FocusEffectFlux.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/flux.png"), 8388736);
        FocusEngine.registerElement(FocusEffectAir.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/air.png"), 16777086);
        FocusEngine.registerElement(FocusMediumMine.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/mine.png"), 8760709);
        FocusEngine.registerElement(FocusMediumCloud.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/cloud.png"), 10071429);

        // Theorycraft card registry (thaumcraft.api.research.theorycraft.TheorycraftManager) - see
        // ResearchTableData's own doc comment. A first real, representative slice of the original's ~56
        // card/aid classes (a much larger card deck is a real, separately-scoped follow-up, not attempted
        // in one pass - see the port-tracking memory notes).
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardStudy.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardAnalyze.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardPonder.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardReject.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardRethink.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardInspired.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardNotation.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardExperimentation.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardBalance.class);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // First real use of NeoForge's modern capability-registration API in this port (see
        // TileRechargePedestal's own doc comment) - exposes its single item slot to hoppers/pipes, the
        // same way the original's ISidedInventory did.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ThaumcraftBlockEntities.RECHARGE_PEDESTAL.get(),
                (tile, side) -> ((TileRechargePedestal) tile).getItemHandler());

        // Vis Generator exposes energy only on its own output face - see TileVisGenerator's own doc
        // comment for the "push-only" design this mirrors from the original.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ThaumcraftBlockEntities.VIS_GENERATOR.get(),
                (tile, side) -> side == tile.getFacing() ? tile : null);

        // Only exposed on the top face, matching the original - see TileWaterJug's own doc comment.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ThaumcraftBlockEntities.WATER_JUG.get(),
                (tile, side) -> side == net.minecraft.core.Direction.UP ? tile.tank : null);

        // Same "expose the single item slot to hoppers/pipes" precedent as TileRechargePedestal above.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ThaumcraftBlockEntities.FOCAL_MANIPULATOR.get(),
                (tile, side) -> ((thaumcraft.common.tiles.crafting.TileFocalManipulator) tile).getItemHandler());

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ThaumcraftBlockEntities.RESEARCH_TABLE.get(),
                (tile, side) -> ((thaumcraft.common.tiles.crafting.TileResearchTable) tile).getItemHandler());

        thaumcraft.gametest.ThaumcraftGameTests.registerTestCapabilities(event);
    }

    /**
     * First real networking this port has needed - a client-bound "draw a visual effect" packet
     * ({@link PacketFXSlash}, for {@code EnumInfusionEnchantment.ARCING}). {@code RegisterPayloadHandlersEvent}
     * fires on both sides (this method is common code, not client-only) - both sides need the payload's
     * codec registered to en/decode it, even though only the client ever actually invokes the handler
     * ({@code playToClient} guarantees a dedicated server, which only ever sends this payload, never
     * receives it, never runs the handler body). The handler dispatches straight to
     * {@link thaumcraft.client.fx.ClientFxHandlers} (a client-only-safe class - see its own doc comment for
     * why referencing it here, from common code, doesn't break a dedicated server) - nothing client-only
     * ever appears directly in this common class's own bytecode.
     */
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(PacketFXSlash.TYPE, PacketFXSlash.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleSlash(payload));
        // ^ method-reference-style dispatch on purpose (a real reference, ClientFxHandlers::handleSlash,
        // would need a 1-arg IPayloadHandler#handle-compatible signature; wrapped in a 2-arg lambda instead
        // since handleSlash itself doesn't need IPayloadContext) - the safety property is the same either
        // way: ClientFxHandlers loads fine everywhere, only handleSlash's own body (never reached on a
        // dedicated server) touches client-only classes.
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXScanSource.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXScanSource.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleScanSource(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXBlockBamf.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXBlockBamf.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleBlockBamf(payload));

        // First client->server packet this port has needed - see ServerboundSaveFocusGraphPacket's own
        // doc comment. enqueueWork defers the actual tile mutation to the main server thread, same
        // precaution every other payload handler that touches real state should take even though the FX
        // ones above (purely cosmetic, no state mutation) didn't need to.
        registrar.playToServer(thaumcraft.common.lib.network.ServerboundSaveFocusGraphPacket.TYPE,
                thaumcraft.common.lib.network.ServerboundSaveFocusGraphPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> thaumcraft.common.lib.network.ServerFocalManipulatorHandlers
                        .handleSaveFocusGraph(payload, (net.minecraft.server.level.ServerPlayer) context.player())));

        // ResearchTableMenu's own real per-action sync - see ClientboundResearchDataPacket's own doc
        // comment. thaumcraft.client.ClientResearchTableCache is client-only-safe the same way
        // ClientFxHandlers is (see that class's own doc comment) - only its method body, never reached on a
        // dedicated server, touches client-only state.
        registrar.playToClient(thaumcraft.common.lib.network.ClientboundResearchDataPacket.TYPE,
                thaumcraft.common.lib.network.ClientboundResearchDataPacket.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.ClientResearchTableCache.handle(payload));
    }

    /** First real living {@code Mob} this port has registered ({@link thaumcraft.common.entities.monster.EntityWisp})
     * needs its {@code AttributeSupplier} built here - every entity registered before it was a non-living
     * projectile/carrier with no attribute map of its own. */
    private void registerEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ThaumcraftEntities.WISP.get(), thaumcraft.common.entities.monster.EntityWisp.createAttributes().build());
        event.put(ThaumcraftEntities.GOLEM.get(), thaumcraft.common.golems.EntityThaumcraftGolem.createAttributes().build());
        event.put(ThaumcraftEntities.THAUMIC_SLIME.get(), thaumcraft.common.entities.monster.EntityThaumicSlime.createAttributes().build());
        event.put(ThaumcraftEntities.BRAINY_ZOMBIE.get(), thaumcraft.common.entities.monster.EntityBrainyZombie.createAttributes().build());
        event.put(ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), thaumcraft.common.entities.monster.EntityGiantBrainyZombie.createAttributes().build());
        event.put(ThaumcraftEntities.FIRE_BAT.get(), thaumcraft.common.entities.monster.EntityFireBat.createAttributes().build());
        event.put(ThaumcraftEntities.MIND_SPIDER.get(), thaumcraft.common.entities.monster.EntityMindSpider.createAttributes().build());
        event.put(ThaumcraftEntities.SPELL_BAT.get(), thaumcraft.common.entities.monster.EntitySpellBat.createAttributes().build());
        event.put(ThaumcraftEntities.ELDRITCH_CRAB.get(), thaumcraft.common.entities.monster.EntityEldritchCrab.createAttributes().build());
        event.put(ThaumcraftEntities.CULTIST_KNIGHT.get(), thaumcraft.common.entities.monster.cult.EntityCultistKnight.createAttributes().build());
        event.put(ThaumcraftEntities.CULTIST_CLERIC.get(), thaumcraft.common.entities.monster.cult.EntityCultistCleric.createAttributes().build());
        event.put(ThaumcraftEntities.CULTIST_LEADER.get(), thaumcraft.common.entities.monster.boss.EntityCultistLeader.createAttributes().build());
        event.put(ThaumcraftEntities.INHABITED_ZOMBIE.get(), thaumcraft.common.entities.monster.EntityInhabitedZombie.createAttributes().build());
        event.put(ThaumcraftEntities.CULTIST_PORTAL_LESSER.get(), thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser.createAttributes().build());
        event.put(ThaumcraftEntities.CULTIST_PORTAL_GREATER.get(), thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_CRAWLER.get(), thaumcraft.common.entities.monster.tainted.EntityTaintCrawler.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_SWARM.get(), thaumcraft.common.entities.monster.tainted.EntityTaintSwarm.createAttributes().build());
        event.put(ThaumcraftEntities.TAINTACLE.get(), thaumcraft.common.entities.monster.tainted.EntityTaintacle.createAttributes().build());
        event.put(ThaumcraftEntities.TAINTACLE_SMALL.get(), thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_SEED.get(), thaumcraft.common.entities.monster.tainted.EntityTaintSeed.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_SEED_PRIME.get(), thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime.createAttributes().build());
        event.put(ThaumcraftEntities.TURRET_CROSSBOW.get(), thaumcraft.common.entities.construct.EntityTurretCrossbow.createAttributes().build());
        event.put(ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get(), thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced.createAttributes().build());
        event.put(ThaumcraftEntities.ARCANE_BORE.get(), thaumcraft.common.entities.construct.EntityArcaneBore.createAttributes().build());
    }

    private void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Explicit registration rather than relying on @GameTestHolder auto-discovery - see the class
        // doc comment on ThaumcraftGameTests for why this exists and how to run it.
        event.register(ThaumcraftGameTests.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Command registration (CommandThaumcraft) will move here once the commands package
        // is ported.
        AuraHandler.server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AuraHandler.stopAllThreads();
        AuraHandler.server = null;
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AuraHandler.loadForLevel(level);
            thaumcraft.common.golems.seals.SealHandler.loadForLevel(level);
        }
    }

    // Arcane Ear's cross-dimension note-listening mechanic - see TileArcaneEar's own doc comment. Play
    // records every real note block's trigger; Post clears each dimension's list once every Arcane Ear in
    // it has had a chance to check that tick's events.
    @SubscribeEvent
    public void onNoteBlockPlay(NoteBlockEvent.Play event) {
        // BlockEvent#getLevel() is typed as the broader LevelAccessor, but a real note block's own
        // triggerEvent (the only place this event fires from) always passes a real Level.
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            TileArcaneEar.recordNoteEvent(level, event.getPos(), event.getInstrument(), event.getVanillaNoteId());
        }
    }

    @SubscribeEvent
    public void onLevelTickPost(LevelTickEvent.Post event) {
        TileArcaneEar.clearNoteEvents(event.getLevel());
    }

    /** Drives the whole seals/tasks subsystem - was {@code ServerEvents#tickAuraHandler}'s own tail end
     * ({@code SealHandler.tickSealEntities}/{@code TaskHandler.clearSuspendedOrExpiredTasks} calls) in the
     * original. Seal ticking runs every level tick (each {@link thaumcraft.common.golems.seals.SealEntity}
     * paces its own real work internally); task cleanup only every 20 ticks, matching the original's own
     * cadence exactly. */
    @SubscribeEvent
    public void tickGolemSeals(LevelTickEvent.Post event) {
        net.minecraft.world.level.Level level = event.getLevel();
        if (level.isClientSide) return;
        thaumcraft.common.golems.seals.SealHandler.tickSealEntities(level);
        if (level.getGameTime() % 20 == 0) {
            thaumcraft.common.golems.tasks.TaskHandler.clearSuspendedOrExpiredTasks(level);
        }
    }

    /** A real vanilla Zombie kill has a chance to drop a Zombie Brain - see {@code ItemZombieBrain}'s own
     * doc comment. Simplified vs the original: no looting-level scaling (that event field isn't exposed by
     * NeoForge's modern {@code LivingDropsEvent} the same way) and no exclusion for the original's own
     * {@code EntityBrainyZombie} variant (not built) - a flat 15% chance on any recently-hit vanilla Zombie
     * death, roughly the middle of the original's own looting-scaled ~10-30% range.
     */
    @SubscribeEvent
    public void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!event.isRecentlyHit()) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            rollZombieBrainDrop(zombie, event.getDrops());
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Monster monster) {
            rollLootBagDrop(monster, event.getDrops());
        }
    }

    /** The real drop-roll logic, split out so GameTests can call it directly with a fixed random draw
     * rather than depending on a real, statistically-rare combat kill. */
    public static void rollZombieBrainDrop(net.minecraft.world.entity.monster.Zombie zombie, java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        if (zombie.getRandom().nextFloat() >= 0.15f) return;
        drops.add(new net.minecraft.world.entity.item.ItemEntity(
                zombie.level(), zombie.getX(), zombie.getY() + zombie.getEyeHeight(), zombie.getZ(),
                new net.minecraft.world.item.ItemStack(thaumcraft.api.items.ItemsTC.brain.get())));
    }

    /** A recently-hit vanilla hostile mob has a chance to drop a loot bag - see {@code ItemLootBag}'s own
     * doc comment. Simplified vs the original: the real original gate was actually the "champion" mutation
     * system (a whole separate Taint subsystem this session hasn't built - the champion attribute it checks
     * never carries a non-default value here, so translating that condition literally would make loot bags
     * permanently undroppable), replaced with a flat 8% chance on any recently-hit vanilla {@code Monster}.
     * Tier is a simple weighted pick (60% common/30% uncommon/10% rare) rather than the original's real
     * looting-level scaling - that event field isn't exposed by NeoForge's modern {@code LivingDropsEvent}
     * the same way (deriving it from the weapon's own Looting enchantment level is possible but adds real
     * complexity for a flavor-only scaling factor).
     */
    public static void rollLootBagDrop(net.minecraft.world.entity.monster.Monster monster, java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        net.minecraft.util.RandomSource random = monster.getRandom();
        if (random.nextFloat() >= 0.08f) return;
        float roll = random.nextFloat();
        net.neoforged.neoforge.registries.DeferredItem<thaumcraft.common.items.curios.ItemLootBag> tier =
                roll < 0.6f ? thaumcraft.api.items.ItemsTC.lootBagCommon
                        : roll < 0.9f ? thaumcraft.api.items.ItemsTC.lootBagUncommon
                        : thaumcraft.api.items.ItemsTC.lootBagRare;
        drops.add(new net.minecraft.world.entity.item.ItemEntity(
                monster.level(), monster.getX(), monster.getY() + monster.getEyeHeight(), monster.getZ(),
                new net.minecraft.world.item.ItemStack(tier.get())));
    }
}
