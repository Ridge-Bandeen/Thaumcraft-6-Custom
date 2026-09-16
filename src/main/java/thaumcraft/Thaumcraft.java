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
        modEventBus.addListener(this::registerSpawnPlacements);
        modEventBus.addListener(this::registerChampionAttribute);
        NeoForge.EVENT_BUS.register(this);

        ThaumcraftAttributes.ATTRIBUTES.register(modEventBus);
        ThaumcraftArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ThaumcraftDataComponents.DATA_COMPONENTS.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftFluids.FLUID_TYPES.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftFluids.FLUIDS.register(modEventBus);
        BlocksTC.BLOCKS.register(modEventBus);
        ItemsTC.ITEMS.register(modEventBus);
        ThaumcraftBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ThaumcraftMenus.MENUS.register(modEventBus);
        ThaumcraftCapabilities.ATTACHMENT_TYPES.register(modEventBus);
        ThaumcraftEntities.ENTITY_TYPES.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftMobEffects.MOB_EFFECTS.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftFeatures.FEATURES.register(modEventBus);
        thaumcraft.common.registry.ThaumcraftSounds.SOUNDS.register(modEventBus);
        ThaumcraftGameTests.register(modEventBus);

        // Installs the real IInternalMethodHandler over ThaumcraftApi's DummyInternalMethodHandler default -
        // see ThaumcraftInternalMethodHandler's own doc comment for what this actually unlocks (AuraHelper,
        // mainly). Done here (constructor) rather than commonSetup since AuraHelper could plausibly be
        // called very early.
        ThaumcraftApi.internalMethods = new ThaumcraftInternalMethodHandler();

        thaumcraft.common.config.ThaumcraftConfig.register(modContainer);
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
        // The real rareearth-nugget ore-break bonus table - same registry-timing reason.
        thaumcraft.common.lib.events.ToolEvents.initRareearthOreChances();
        // The real Infernal Furnace smelting-bonus table - same registry-timing reason.
        thaumcraft.common.tiles.devices.TileInfernalFurnace.initSmeltingBonuses();

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
        // overall scope of this "headless" (no book UI) slice of the research system. Scanning the Runic
        // Matrix is an invented substitute trigger for BOOTSTRAVELLER (the original granted it via a
        // Thaumonomicon research-tree node, not ported) - it's the one already-shipped recipe this session
        // has that's real but currently unreachable outside creative mode (TileInfusionMatrix#craftingStart
        // checks its research key against player knowledge, and nothing before this granted BOOTSTRAVELLER
        // to anyone).
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

        // ScanSky - the real producer behind ItemCelestialNotes/CardCelestial (see ScanSky's own doc
        // comment). Triggered by the thaumometer's own real "scan nothing" action, restored specifically
        // for this real mechanic - see ItemThaumometer#use's own doc comment.
        thaumcraft.api.research.ScanningManager.addScannableThing(new thaumcraft.common.lib.research.ScanSky());

        // ScanGeneric - the real general-purpose catch-all: anything with real aspects at all grants real
        // OBSERVATION progress toward every real research category on its first scan. See its own doc
        // comment; this is what makes the thaumometer a real research tool rather than a lookup for the
        // handful of hardcoded targets above.
        thaumcraft.api.research.ScanningManager.addScannableThing(new thaumcraft.common.lib.research.ScanGeneric());

        // ScanEnchantment/ScanPotion - real "discovering an enchantment/effect" scan targets, both reachable
        // now that ScanningManager's own real inventory recursion and item resolution are ported. The
        // original registered one instance per enchantment AND per potion; modern enchantments are
        // data-driven (nothing to enumerate at setup) so that one is a single dynamic instance instead,
        // while mob effects are still a real code-registered registry and keep the original's own
        // one-instance-per-effect shape. Both classes' own doc comments cover the difference.
        thaumcraft.api.research.ScanningManager.addScannableThing(new thaumcraft.common.lib.research.ScanEnchantment());
        for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect
                : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.asHolderIdMap()) {
            thaumcraft.api.research.ScanningManager.addScannableThing(new thaumcraft.common.lib.research.ScanPotion(effect));
        }

        // Warp-on-craft costs (thaumcraft.api.ThaumcraftApi#addWarpToItem). Shipped Thaumcraft 6 registers
        // exactly one, and this is it: crafting a Brain in a Jar costs 1 real permanent warp (the original's
        // own ConfigResearch line 282). The mechanic itself is real - see WarpEvents#onItemCrafted for how an
        // earlier pass here talked itself out of that and what reading the original actually showed.
        thaumcraft.api.ThaumcraftApi.addWarpToItem(new net.minecraft.world.item.ItemStack(BlocksTC.jarBrain.get()), 1);

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
        FocusEngine.registerElement(thaumcraft.common.items.casters.foci.FocusEffectRift.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/rift.png"), 3084645);
        FocusEngine.registerElement(thaumcraft.common.items.casters.foci.FocusMediumPlan.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/plan.png"), 8760728);
        FocusEngine.registerElement(thaumcraft.common.items.casters.foci.FocusEffectExchange.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/exchange.png"), 5735255);

        // Theorycraft card registry (thaumcraft.api.research.theorycraft.TheorycraftManager) - see
        // ResearchTableData's own doc comment. The real api.research.theorycraft package (the extensible
        // "core" tier) is now fully ported - all 9 of its cards plus AidBookshelf. What follows is the
        // start of the original's much larger common.lib.research.theorycraft "extra content" tier (~37
        // more cards/aids there alone) - a real, deliberately incremental follow-up, not attempted in one
        // pass. See the port-tracking memory notes for the running scope.
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardStudy.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardAnalyze.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardPonder.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardReject.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardRethink.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardInspired.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardNotation.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardExperimentation.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.api.research.theorycraft.CardBalance.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.api.research.theorycraft.AidBookshelf());

        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardCalibrate.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardMeasure.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardSynergy.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardBeacon.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardDragonEgg.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardEnchantment.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardPortal.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBeacon());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidDragonEgg());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidEnchantmentTable());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidPortal.AidPortalEnd());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidPortal.AidPortalNether());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidPortal.AidPortalCrimson());

        // Second batch of the "extra content" tier - see this method's own doc comment above. ALL 19 of the
        // original's remaining cards and ALL 8 of its remaining aids are now registered. AidGlyphedStone,
        // AidBasicAuromancy and AidBasicEldritch all turned out to be the same real story - their own
        // supposedly-missing blocks (stoneAncientGlyphed/wandWorkbench/eldritch) were each already real and
        // shipped under this port's own modern names (stoneAncientGlyphed as-is, wandWorkbench as
        // focalManipulator, eldritch as cultistRitualAltar - see each aid's own doc comment) - nobody had
        // connected the aid class to its already-built block yet. AidBrainInAJar needed real new work
        // (jarBrain, the "Brain in a Jar" tile - see BlockJarBrain's own doc comment). CardCelestial
        // (this whole tier's last holdout) turned out to NOT be Baubles-gated at all, despite an old,
        // never-re-verified note claiming so - its real dependency chain (ItemCelestialNotes, ScanSky,
        // CELESTIALSCANNING research) has zero Baubles involvement anywhere; see ScanSky's own doc comment.
        // This whole "extra content" tier is now fully closed.
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardCelestial.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardAwareness.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardFocus.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardGlyphs.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardRealization.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardRevelation.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardSculpting.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardSpellbinding.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardTruth.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardDarkWhispers.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardChannel.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardConcentrate.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardReactions.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardSynthesis.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardInfuse.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardTinker.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardMindOverMatter.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardCurio.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerCard(thaumcraft.common.lib.research.theorycraft.CardScripting.class);
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicAlchemy());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicArtifice());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicGolemancy());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicInfusion());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidGlyphedStone());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicAuromancy());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBasicEldritch());
        thaumcraft.api.research.theorycraft.TheorycraftManager.registerAid(new thaumcraft.common.lib.research.theorycraft.AidBrainInAJar());
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
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXBlockArc.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXBlockArc.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleBlockArc(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXWispZap.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXWispZap.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleWispZap(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXZap.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXZap.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleZap(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXBoreDig.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXBoreDig.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleBoreDig(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXFocusEffect.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXFocusEffect.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleFocusEffect(payload));
        registrar.playToClient(thaumcraft.common.lib.network.fx.PacketFXFocusPartImpact.TYPE,
                thaumcraft.common.lib.network.fx.PacketFXFocusPartImpact.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.fx.ClientFxHandlers.handleFocusPartImpact(payload));

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

        // ThaumonomiconMenu's own real per-action sync - see ClientboundPlayerKnowledgePacket's own doc
        // comment. thaumcraft.client.ClientPlayerKnowledgeCache is client-only-safe the same way
        // ClientResearchTableCache is (see that class's own doc comment).
        registrar.playToClient(thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket.TYPE,
                thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket.STREAM_CODEC,
                (payload, context) -> thaumcraft.client.ClientPlayerKnowledgeCache.handle(payload));

        // The real focus-radial HUD's own selection packet - see ServerboundChangeCasterFocusPacket's own
        // doc comment.
        registrar.playToServer(thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket.TYPE,
                thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> thaumcraft.common.items.casters.CasterFocusHelper
                        .handleChangeFocus(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
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
        event.put(ThaumcraftEntities.TAINTACLE_GIANT.get(), thaumcraft.common.entities.monster.boss.EntityTaintacleGiant.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_SEED.get(), thaumcraft.common.entities.monster.tainted.EntityTaintSeed.createAttributes().build());
        event.put(ThaumcraftEntities.TAINT_SEED_PRIME.get(), thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime.createAttributes().build());
        event.put(ThaumcraftEntities.TURRET_CROSSBOW.get(), thaumcraft.common.entities.construct.EntityTurretCrossbow.createAttributes().build());
        event.put(ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get(), thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced.createAttributes().build());
        event.put(ThaumcraftEntities.ARCANE_BORE.get(), thaumcraft.common.entities.construct.EntityArcaneBore.createAttributes().build());
        event.put(ThaumcraftEntities.PECH.get(), thaumcraft.common.entities.monster.EntityPech.createAttributes().build());
        event.put(ThaumcraftEntities.ELDRITCH_GUARDIAN.get(), thaumcraft.common.entities.monster.EntityEldritchGuardian.createAttributes().build());
        event.put(ThaumcraftEntities.ELDRITCH_WARDEN.get(), thaumcraft.common.entities.monster.boss.EntityEldritchWarden.createAttributes().build());
        event.put(ThaumcraftEntities.ELDRITCH_GOLEM.get(), thaumcraft.common.entities.monster.boss.EntityEldritchGolem.createAttributes().build());
    }

    /**
     * Real, small, found-not-built gap this session's own Eerie biome spawn list surfaced: NeoForge warns at
     * startup ("have not registered to the RegisterSpawnPlacementsEvent") for any entity type a real spawn
     * list references without a real registered spawn-placement rule - a real, pre-existing gap for these 6
     * mobs (never fixed because nothing referenced them in a spawn list before now), not something this
     * session's biome work introduced. Real, standard vanilla predicates: {@code Monster#checkMonsterSpawnRules}
     * for the 4 real straightforwardly-hostile ones (matching every vanilla hostile mob's own real default -
     * dark enough, valid ground, difficulty not peaceful), {@code Mob#checkMobSpawnRules} for
     * {@link ThaumcraftEntities#PECH}/{@link ThaumcraftEntities#WISP} (real, more permissive neutral-creature
     * defaults, matching their own real non-hostile-monster nature).
     */
    private void registerSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        event.register(ThaumcraftEntities.BRAINY_ZOMBIE.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(ThaumcraftEntities.INHABITED_ZOMBIE.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(ThaumcraftEntities.ELDRITCH_GUARDIAN.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(ThaumcraftEntities.PECH.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
        event.register(ThaumcraftEntities.WISP.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.AND);
    }

    /**
     * Adds {@link ThaumcraftAttributes#CHAMPION_MOD} to every real hostile creature eligible to roll as a
     * champion (see {@link thaumcraft.common.lib.events.ChampionModEvents}) - vanilla's own real hostile
     * mobs plus this mod's own real roaming hostile creatures. {@link EntityAttributeModificationEvent} is
     * NeoForge's real mechanism for adding a NEW attribute onto an already-registered (including vanilla)
     * entity type's attribute map, replacing the original's 1.12 {@code EntityConstructing} event (which
     * could freely register attributes on any entity instance at construction time - no modern equivalent
     * for arbitrary/dynamic per-instance registration exists, so this port uses an explicit, curated type
     * list instead of the original's blanket "every {@code EntityCreature}").
     *
     * <p>{@code CultistLeader}/{@code EldritchWarden}/{@code EldritchGolem}/{@code TaintacleGiant} ARE
     * included, real parity with the true original's own blanket {@code EntityConstructing} approach (which
     * never excluded bosses) - decided 2026-09-15 after this exclusion was flagged as an unnecessary design
     * fork the sweep introduced, not something the true original ever did. All 4 are real: the true
     * original's own {@code EntityCultistLeader}/{@code EntityEldritchWarden}/{@code EntityEldritchGolem}
     * each override {@code EntityThaumcraftBoss#generateName} to fold a rolled champion mod's name into their
     * own display name, and {@code EntityTaintacleGiant} calls {@code EntityUtils#makeChampion} directly
     * (unconditionally) at spawn - {@code EntityEldritchGolem}'s own tie-in was missed on the first pass of
     * this correction and only found by re-reading its true-original source directly rather than trusting the
     * other 3 bosses' own citations as exhaustive. Excluded on purpose otherwise: this mod's construct
     * entities (turrets, the Arcane Bore, golems - not hostile creatures), and its stationary taint hazards
     * ({@code Taintacle}/{@code TaintSeed} - environmental growths, not roaming creatures a player fights as
     * a champion encounter). {@code TaintCrawler}/{@code TaintSwarm} ARE included - real mobile hostile
     * creatures.
     */
    private void registerChampionAttribute(net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) {
        net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> championMod = ThaumcraftAttributes.CHAMPION_MOD;
        java.util.List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>> vanilla = java.util.List.of(
                net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER,
                net.minecraft.world.entity.EntityType.HUSK, net.minecraft.world.entity.EntityType.DROWNED,
                net.minecraft.world.entity.EntityType.SKELETON, net.minecraft.world.entity.EntityType.STRAY,
                net.minecraft.world.entity.EntityType.WITHER_SKELETON, net.minecraft.world.entity.EntityType.BOGGED,
                net.minecraft.world.entity.EntityType.SPIDER, net.minecraft.world.entity.EntityType.CAVE_SPIDER,
                net.minecraft.world.entity.EntityType.CREEPER, net.minecraft.world.entity.EntityType.ENDERMAN,
                net.minecraft.world.entity.EntityType.ENDERMITE, net.minecraft.world.entity.EntityType.SILVERFISH,
                net.minecraft.world.entity.EntityType.WITCH, net.minecraft.world.entity.EntityType.SLIME,
                net.minecraft.world.entity.EntityType.MAGMA_CUBE, net.minecraft.world.entity.EntityType.BLAZE,
                net.minecraft.world.entity.EntityType.GHAST, net.minecraft.world.entity.EntityType.GUARDIAN,
                net.minecraft.world.entity.EntityType.ELDER_GUARDIAN, net.minecraft.world.entity.EntityType.SHULKER,
                net.minecraft.world.entity.EntityType.PHANTOM, net.minecraft.world.entity.EntityType.VEX,
                net.minecraft.world.entity.EntityType.VINDICATOR, net.minecraft.world.entity.EntityType.EVOKER,
                net.minecraft.world.entity.EntityType.ILLUSIONER, net.minecraft.world.entity.EntityType.PILLAGER,
                net.minecraft.world.entity.EntityType.RAVAGER, net.minecraft.world.entity.EntityType.PIGLIN,
                net.minecraft.world.entity.EntityType.PIGLIN_BRUTE, net.minecraft.world.entity.EntityType.HOGLIN,
                net.minecraft.world.entity.EntityType.ZOGLIN, net.minecraft.world.entity.EntityType.BREEZE);
        for (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type : vanilla) {
            event.add(type, championMod, -2.0);
        }

        event.add(ThaumcraftEntities.WISP.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.THAUMIC_SLIME.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.BRAINY_ZOMBIE.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.FIRE_BAT.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.MIND_SPIDER.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.SPELL_BAT.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.ELDRITCH_CRAB.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.CULTIST_KNIGHT.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.CULTIST_CLERIC.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.INHABITED_ZOMBIE.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.TAINT_CRAWLER.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.TAINT_SWARM.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.CULTIST_LEADER.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.ELDRITCH_WARDEN.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.ELDRITCH_GOLEM.get(), championMod, -2.0);
        event.add(ThaumcraftEntities.TAINTACLE_GIANT.get(), championMod, -2.0);
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

    /** Modern replacement for the original's own {@code WorldEvents#worldUnload} - drops this one
     * dimension's live in-memory aura/seal/task state (real saved data itself is untouched, each system's
     * own {@code SavedData} reloads it fresh on the dimension's next real {@link #onLevelLoad}). Without
     * this, a dimension unloaded and reloaded during a single server run (a dynamically-added datapack
     * dimension, or {@code /forge unloadWorld} on unmodified vanilla multi-world setups) would leak its old
     * aura background thread and stale seal/task maps forever, alongside a fresh set loaded back in. Found
     * and ported while triaging the still-stale {@code common/lib/events} package's real remaining gaps.
     */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AuraHandler.unloadDimension(level.dimension());
            thaumcraft.common.golems.seals.SealHandler.unloadDimension(level.dimension());
        }
    }

    /** Modern replacement for the original's own {@code EntityEvents#itemPickup} - stops the real
     * {@code FakePlayer} {@link thaumcraft.common.golems.GolemInteractionHelper} puppets through a golem's
     * block-break/use action (a real, named "FakeThaumcraftGolem" instance) from also vacuuming up a nearby
     * item drop mid-action. Real logic lives in the static {@link #isFakeThaumcraftPlayer}, the same
     * "extract a static, directly testable helper out of the instance event method" shape
     * {@link #blocksAnvilOperation}/{@link #rollLootBagDrop} already established.
     */
    @SubscribeEvent
    public void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        if (isFakeThaumcraftPlayer(event.getPlayer())) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    /** Same real name-prefix guard the original used (not a hardcoded exact match, so any future
     * "FakeThaumcraft"-prefixed fake player is covered too, matching the original's own intent). */
    public static boolean isFakeThaumcraftPlayer(net.minecraft.world.entity.player.Player player) {
        return player.getGameProfile().getName().startsWith("FakeThaumcraft");
    }

    /** Modern replacement for the original's own {@code EventHandlerNetwork#playerLoggedInEvent} - found
     * while triaging {@code PlayerEvents}. {@link thaumcraft.api.capabilities.ThaumcraftCapabilities#KNOWLEDGE}/
     * {@code WARP} are real NeoForge Data Attachments (real, live {@code .copyOnDeath()} already carries them
     * across respawn for free - the original's own separate {@code PlayerEvents#cloneCapabilitiesEvent} has no
     * real modern equivalent to port, that whole problem is solved a level below this), but neither is
     * registered with {@code .sync(...)}, so nothing pushes them to a client automatically - every other real
     * consumer this session ({@code PassiveResearchEvents}/{@code WarpEvents}/etc.) only calls
     * {@code knowledge.sync(player)}/{@code warp.sync(player)} reactively, on its own real change. Without
     * this, a player with real existing progress would show stale (usually empty-looking) data client-side
     * from login until the next unrelated change happened to trigger a sync. */
    @SubscribeEvent
    public void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ThaumcraftCapabilities.getKnowledge(player).sync(player);
            ThaumcraftCapabilities.getWarp(player).sync(player);
        }
    }

    /** Modern replacement for the original's own {@code PlayerEvents#finishedUsingItem} - found while
     * triaging the same file. While a player suffers {@code Unnatural Hunger} ({@link
     * thaumcraft.common.registry.ThaumcraftMobEffects#UNNATURAL_HUNGER}), finishing a real Rotten Flesh or
     * {@link thaumcraft.api.items.ItemsTC#brain} steps the curse's own amplifier/duration down (matching the
     * original's real {@code -1} amplifier / {@code -600} duration formula, ending the effect outright once
     * either would go negative); eating anything else edible instead just sends the original's own warning
     * message, doing nothing mechanical. Real, minor cut: the original also swapped the re-applied effect's
     * "cured by" item list down to just Rotten Flesh - cosmetic only (which items a status-effect tooltip
     * lists as curing it), not ported.
     */
    @SubscribeEvent
    public void onFinishEating(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        net.minecraft.world.entity.LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && entity instanceof net.minecraft.world.entity.player.Player player) {
            handleFinishEatingUnderUnnaturalHunger(player, event.getItem());
        }
    }

    /** Real logic extracted out of {@link #onFinishEating} for real testability, the same "extract a static,
     * directly testable helper out of the instance event method" shape {@link #blocksAnvilOperation}/
     * {@link #rollLootBagDrop} already established. See {@link #onFinishEating}'s own doc comment for what
     * this real port does and cuts. */
    public static void handleFinishEatingUnderUnnaturalHunger(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack item) {
        if (!player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.UNNATURAL_HUNGER)) return;

        boolean isCurativeFood = item.is(net.minecraft.world.item.Items.ROTTEN_FLESH) || item.is(thaumcraft.api.items.ItemsTC.brain.get());
        if (isCurativeFood) {
            net.minecraft.world.effect.MobEffectInstance current = player.getEffect(thaumcraft.common.registry.ThaumcraftMobEffects.UNNATURAL_HUNGER);
            int amplifier = current.getAmplifier() - 1;
            int duration = current.getDuration() - 600;
            player.removeEffect(thaumcraft.common.registry.ThaumcraftMobEffects.UNNATURAL_HUNGER);
            if (duration > 0 && amplifier >= 0) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        thaumcraft.common.registry.ThaumcraftMobEffects.UNNATURAL_HUNGER, duration, amplifier, true, false));
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("warp.text.hunger.2")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GREEN, net.minecraft.ChatFormatting.ITALIC), false);
        } else if (item.get(net.minecraft.core.component.DataComponents.FOOD) != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("warp.text.hunger.1")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
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

    /** Drives {@link thaumcraft.common.lib.scheduling.BlockScheduler}'s own real per-level queues (delayed
     * block breaking/swapping) - a real, previously-missing wire found 2026-09-15 while restoring
     * {@code FocusEffectExchange}: the scheduler class itself, and {@link thaumcraft.common.items.casters.foci.FocusEffectBreak}'s
     * own real call into it, were both already real and shipped, but nothing ever actually ticked the queue -
     * meaning a real gradual break/swap in live gameplay would queue correctly and then just sit there
     * forever, never actually progressing. Confirmed via a real {@code grep} for any existing caller (found
     * none) before adding this, not assumed. */
    @SubscribeEvent
    public void tickBlockScheduler(LevelTickEvent.Post event) {
        net.minecraft.world.level.Level level = event.getLevel();
        if (!level.isClientSide) {
            thaumcraft.common.lib.scheduling.BlockScheduler.tick(level);
        }
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

    /** Replaces the old (1.12.2) {@code CraftingEvents#onAnvil} - the Primordial Pearl carries real NBT
     * charge state an anvil repair/rename/combine could corrupt or duplicate, so any anvil operation
     * touching it is blocked outright, matching the original exactly. Real logic lives in the static
     * {@link #blocksAnvilOperation}, the same "extract a static, directly testable helper out of the
     * instance event method" shape {@link #rollLootBagDrop} already established. */
    @SubscribeEvent
    public void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        if (blocksAnvilOperation(event.getLeft(), event.getRight())) {
            event.setCanceled(true);
        }
    }

    public static boolean blocksAnvilOperation(net.minecraft.world.item.ItemStack left, net.minecraft.world.item.ItemStack right) {
        return left.is(thaumcraft.api.items.ItemsTC.primordialPearl.get()) || right.is(thaumcraft.api.items.ItemsTC.primordialPearl.get());
    }

    /** A real champion mob's own real guaranteed loot-bag drop - see {@code ItemLootBag}'s own doc comment.
     * Restored 2026-09-15 to the true original's own real gate now that the champion-mod system is real (see
     * the champion-mod correction in the port-tracking memory/TODO.md): matches the true original's own
     * {@code livingDrops} condition exactly - recently hit, a real {@link net.minecraft.world.entity.monster.Monster},
     * NOT a {@link thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss} (bosses get their own real
     * drops instead, same exclusion {@link thaumcraft.common.lib.events.ChampionModEvents#onDrops}'s own
     * paired real XP bonus uses), a real rolled champion mod id (0-13) excluding Tainted (13). No independent
     * chance roll on top - a confirmed real champion always drops one, same as the true original. Tier is
     * still a simple weighted pick (60% common/30% uncommon/10% rare) rather than the original's real
     * looting-level scaling - that stays a real, deliberate simplification, confirmed by reading the modern
     * {@code LivingDropsEvent} class directly: it genuinely has no {@code lootingLevel} field or equivalent
     * at all (unlike the original's own event), not just an oversight.
     *
     * (Zombie -&gt; Brain drops used to be rolled from this same {@code LivingDropsEvent} subscriber too -
     * removed once {@code MobDeathDropEvents#onLivingDrops} shipped a more complete real version, correctly
     * excluding the real {@code EntityBrainyZombie} variant; both existed together for a while and rolled a
     * real duplicate chance on every Zombie kill until this fix, caught while scoping an unrelated later
     * chunk - see the port-tracking memory.)
     */
    @SubscribeEvent
    public void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!event.isRecentlyHit()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster monster)) return;
        if (monster instanceof thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss) return;
        rollLootBagDrop(monster, event.getDrops());
    }

    public static void rollLootBagDrop(net.minecraft.world.entity.monster.Monster monster, java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance championAttr =
                monster.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
        if (championAttr == null) return;
        double championId = championAttr.getValue();
        if (championId < 0.0 || championId == 13.0) return;

        net.minecraft.util.RandomSource random = monster.getRandom();
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
