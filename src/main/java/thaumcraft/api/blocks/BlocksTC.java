package thaumcraft.api.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.blocks.crafting.BlockArcaneWorkbench;
import thaumcraft.common.blocks.crafting.BlockCrucible;

/**
 * Registered block references, addon-facing the same way the old (1.12.2) {@code BlocksTC} was - fields
 * are assigned via registration rather than left null, only now via a real {@link DeferredRegister} instead
 * of Registrar assigning static fields after the fact.
 *
 * This is being built out incrementally, one vertical slice at a time, rather than declaring all ~350
 * fields from the original up front with nothing behind most of them - see the port-tracking memory notes
 * for why. Add a field here only once the block it names is actually registered below.
 */
public final class BlocksTC {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("thaumcraft");

	public static final DeferredBlock<BlockArcaneWorkbench> arcaneWorkbench = BLOCKS.registerBlock("arcane_workbench",
			BlockArcaneWorkbench::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));

	public static final DeferredBlock<BlockCrucible> crucible = BLOCKS.registerBlock("crucible",
			BlockCrucible::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noOcclusion());

	/**
	 * First step into world generation - the original's overworld quartz ore variant. No custom Block
	 * subclass needed (unlike {@code BlockOreTC}, which only special-cased amber's bonus curio drop and
	 * variable quantities - neither of which apply to quartz); tool requirement (stone pickaxe) and drops
	 * (vanilla quartz, fortune-scaled, self-dropping on silk touch) are both handled by data
	 * (tags/loot table), matching this project's "full data-driven" preference. See
	 * {@code data/thaumcraft/worldgen/**} and {@code data/thaumcraft/neoforge/biome_modifier/ore_quartz.json}
	 * for the generation side.
	 */
	public static final DeferredBlock<Block> oreQuartz = BLOCKS.registerBlock("ore_quartz",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	/** Drops itself (smelts into quicksilver - see {@code data/thaumcraft/recipe/quicksilver_smelting.json}),
	 * unlike quartz ore which drops a separate item - so its loot table is a plain self-drop, no fortune
	 * scaling, matching the original {@code BlockOreTC.quantityDroppedWithBonus}'s rule that fortune only
	 * applies when the dropped item differs from the block itself. */
	public static final DeferredBlock<Block> oreCinnabar = BLOCKS.registerBlock("ore_cinnabar",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	/** Third ore. The original ({@code BlockOreTC}) special-cased amber alone with a variable 1-2 quantity
	 * and a rare (6.6%) bonus {@code ItemCurio} drop replacing one of the amber items - both reproduced in
	 * {@code data/thaumcraft/loot_table/blocks/ore_amber.json} rather than in a custom Block subclass,
	 * consistent with this project's data-driven-drops preference. Harvest level (stone pickaxe) matches
	 * the original's {@code setHarvestLevel("pickaxe", 1)}. */
	public static final DeferredBlock<Block> oreAmber = BLOCKS.registerBlock("ore_amber",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	private BlocksTC() {
	}
}
