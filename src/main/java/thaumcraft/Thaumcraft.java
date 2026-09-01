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
        ThaumcraftGameTests.register(modEventBus);

        // Installs the real IInternalMethodHandler over ThaumcraftApi's DummyInternalMethodHandler default -
        // see ThaumcraftInternalMethodHandler's own doc comment for what this actually unlocks (AuraHelper,
        // mainly). Done here (constructor) rather than commonSetup since AuraHelper could plausibly be
        // called very early.
        ThaumcraftApi.internalMethods = new ThaumcraftInternalMethodHandler();

        // The real item-to-aspect object-tag table - see ThaumcraftObjectTags's own doc comment. Safe here
        // (constructor, not commonSetup): every entry references vanilla Items/Tags.Items constants, none
        // of which need a registration event to have fired first.
        thaumcraft.common.lib.crafting.ThaumcraftObjectTags.init();
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

        // Vis Generator exposes energy only on its own output face - see TileVisGenerator's own doc
        // comment for the "push-only" design this mirrors from the original.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ThaumcraftBlockEntities.VIS_GENERATOR.get(),
                (tile, side) -> side == tile.getFacing() ? tile : null);

        // Only exposed on the top face, matching the original - see TileWaterJug's own doc comment.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ThaumcraftBlockEntities.WATER_JUG.get(),
                (tile, side) -> side == net.minecraft.core.Direction.UP ? tile.tank : null);

        thaumcraft.gametest.ThaumcraftGameTests.registerTestCapabilities(event);
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
