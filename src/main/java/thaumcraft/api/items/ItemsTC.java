package thaumcraft.api.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.items.casters.ItemFocusAir;
import thaumcraft.common.items.casters.ItemFocusBreak;
import thaumcraft.common.items.casters.ItemFocusCurse;
import thaumcraft.common.items.casters.ItemFocusEarth;
import thaumcraft.common.items.casters.ItemFocusFire;
import thaumcraft.common.items.casters.ItemFocusFireball;
import thaumcraft.common.items.casters.ItemFocusFlux;
import thaumcraft.common.items.casters.ItemFocusFireBolt;
import thaumcraft.common.items.casters.ItemFocusFrost;
import thaumcraft.common.items.casters.ItemFocusHeal;
import thaumcraft.common.items.casters.ItemFocusSanctuary;
import thaumcraft.common.items.casters.ItemFocusTrap;
import thaumcraft.common.items.curios.ItemCurio;

/**
 * See {@link BlocksTC} for why this only has a couple of fields so far instead of the ~150 the original
 * had - built out incrementally, one vertical slice at a time.
 */
public final class ItemsTC {

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("thaumcraft");

	public static final DeferredItem<BlockItem> arcaneWorkbench =
			ITEMS.registerSimpleBlockItem("arcane_workbench", BlocksTC.arcaneWorkbench);

	/** Not yet wired up to the real scanning/research system - see the port-tracking memory notes. */
	public static final DeferredItem<Item> thaumometer =
			ITEMS.registerSimpleItem("thaumometer", new Item.Properties().stacksTo(1));

	public static final DeferredItem<ItemCaster> casterBasic =
			ITEMS.registerItem("caster_basic", ItemCaster::new);

	public static final DeferredItem<ItemFocusFire> focus1 =
			ITEMS.registerItem("focus_1", ItemFocusFire::new);

	public static final DeferredItem<BlockItem> crucible =
			ITEMS.registerSimpleBlockItem("crucible", BlocksTC.crucible);

	public static final DeferredItem<BlockItem> oreQuartz =
			ITEMS.registerSimpleBlockItem("ore_quartz", BlocksTC.oreQuartz);

	public static final DeferredItem<BlockItem> oreCinnabar =
			ITEMS.registerSimpleBlockItem("ore_cinnabar", BlocksTC.oreCinnabar);

	public static final DeferredItem<Item> quicksilver =
			ITEMS.registerSimpleItem("quicksilver");

	public static final DeferredItem<ItemGenericEssentiaContainer> crystalEssence =
			ITEMS.registerItem("crystal_essence", props -> new ItemGenericEssentiaContainer(props, 1));

	public static final DeferredItem<ItemFocusFrost> focusFrost =
			ITEMS.registerItem("focus_frost", ItemFocusFrost::new);

	public static final DeferredItem<ItemFocusBreak> focusBreak =
			ITEMS.registerItem("focus_break", ItemFocusBreak::new);

	public static final DeferredItem<ItemFocusFireBolt> focusFireBolt =
			ITEMS.registerItem("focus_fire_bolt", ItemFocusFireBolt::new);

	public static final DeferredItem<ItemFocusHeal> focusHeal =
			ITEMS.registerItem("focus_heal", ItemFocusHeal::new);

	public static final DeferredItem<ItemFocusCurse> focusCurse =
			ITEMS.registerItem("focus_curse", ItemFocusCurse::new);

	public static final DeferredItem<ItemFocusEarth> focusEarth =
			ITEMS.registerItem("focus_earth", ItemFocusEarth::new);

	public static final DeferredItem<ItemFocusFireball> focusFireball =
			ITEMS.registerItem("focus_fireball", ItemFocusFireball::new);

	public static final DeferredItem<ItemFocusFlux> focusFlux =
			ITEMS.registerItem("focus_flux", ItemFocusFlux::new);

	public static final DeferredItem<ItemFocusAir> focusAir =
			ITEMS.registerItem("focus_air", ItemFocusAir::new);

	public static final DeferredItem<ItemFocusTrap> focusTrap =
			ITEMS.registerItem("focus_trap", ItemFocusTrap::new);

	public static final DeferredItem<ItemFocusSanctuary> focusSanctuary =
			ITEMS.registerItem("focus_sanctuary", ItemFocusSanctuary::new);

	public static final DeferredItem<BlockItem> oreAmber =
			ITEMS.registerSimpleBlockItem("ore_amber", BlocksTC.oreAmber);

	/** The raw gem itself - inert for now (the original used it in several not-yet-ported recipes:
	 * warded glass, arcane ear, various golemancy items). */
	public static final DeferredItem<Item> amber =
			ITEMS.registerSimpleItem("amber");

	public static final DeferredItem<ItemCurio> curio =
			ITEMS.registerItem("curio", ItemCurio::new);

	/** Quicksilver's first real use - a reflective glass pane. Inert beyond that for now (the original's
	 * hand mirror/scrying mirror/golem sight devices that consume it aren't ported). */
	public static final DeferredItem<Item> mirroredGlass =
			ITEMS.registerSimpleItem("mirrored_glass");

	public static final DeferredItem<BlockItem> amberBlock =
			ITEMS.registerSimpleBlockItem("amber_block", BlocksTC.amberBlock);

	public static final DeferredItem<BlockItem> amberBrick =
			ITEMS.registerSimpleBlockItem("amber_brick", BlocksTC.amberBrick);

	public static final DeferredItem<BlockItem> stoneArcane =
			ITEMS.registerSimpleBlockItem("stone_arcane", BlocksTC.stoneArcane);

	public static final DeferredItem<BlockItem> stoneArcaneStairs =
			ITEMS.registerSimpleBlockItem("stone_arcane_stairs", BlocksTC.stoneArcaneStairs);

	public static final DeferredItem<BlockItem> stoneArcaneSlab =
			ITEMS.registerSimpleBlockItem("stone_arcane_slab", BlocksTC.stoneArcaneSlab);

	public static final DeferredItem<BlockItem> stoneArcaneBrick =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick", BlocksTC.stoneArcaneBrick);

	public static final DeferredItem<BlockItem> stoneArcaneBrickStairs =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick_stairs", BlocksTC.stoneArcaneBrickStairs);

	public static final DeferredItem<BlockItem> stoneArcaneBrickSlab =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick_slab", BlocksTC.stoneArcaneBrickSlab);

	public static final DeferredItem<BlockItem> logSilverwood =
			ITEMS.registerSimpleBlockItem("log_silverwood", BlocksTC.logSilverwood);

	public static final DeferredItem<BlockItem> plankSilverwood =
			ITEMS.registerSimpleBlockItem("plank_silverwood", BlocksTC.plankSilverwood);

	public static final DeferredItem<BlockItem> leavesSilverwood =
			ITEMS.registerSimpleBlockItem("leaves_silverwood", BlocksTC.leavesSilverwood);

	public static final DeferredItem<BlockItem> saplingSilverwood =
			ITEMS.registerSimpleBlockItem("sapling_silverwood", BlocksTC.saplingSilverwood);

	private ItemsTC() {
	}
}
