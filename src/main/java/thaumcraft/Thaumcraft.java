package thaumcraft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
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
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusEffectFrost;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

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
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Thaumcraft {} common setup (1.21.1 port in progress)", VERSION);

        // Focus element registry (thaumcraft.api.casters.FocusEngine) - keyed by class, not a
        // DeferredRegister, matching the original 1.12.2 design.
        FocusEngine.registerElement(FocusMediumTouch.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/touch.png"), 11371909);
        FocusEngine.registerElement(FocusEffectFire.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/fire.png"), 16734721);
        FocusEngine.registerElement(FocusEffectFrost.class, ResourceLocation.fromNamespaceAndPath(MODID, "textures/foci/frost.png"), 14811135);
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
