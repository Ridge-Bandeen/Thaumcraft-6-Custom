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
        NeoForge.EVENT_BUS.register(this);

        ThaumcraftAttributes.ATTRIBUTES.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ThaumcraftRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ThaumcraftDataComponents.DATA_COMPONENTS.register(modEventBus);
        BlocksTC.BLOCKS.register(modEventBus);
        ItemsTC.ITEMS.register(modEventBus);
        ThaumcraftBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ThaumcraftMenus.MENUS.register(modEventBus);
        ThaumcraftCapabilities.ATTACHMENT_TYPES.register(modEventBus);
        ThaumcraftEntities.ENTITY_TYPES.register(modEventBus);
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
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // First real use of NeoForge's modern capability-registration API in this port (see
        // TileRechargePedestal's own doc comment) - exposes its single item slot to hoppers/pipes, the
        // same way the original's ISidedInventory did.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ThaumcraftBlockEntities.RECHARGE_PEDESTAL.get(),
                (tile, side) -> ((TileRechargePedestal) tile).getItemHandler());
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
        }
    }
}
