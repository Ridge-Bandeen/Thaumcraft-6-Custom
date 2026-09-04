package thaumcraft.common.blocks.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/**
 * Replaces the old (1.12.2) {@code BlockStonePorous} - a plain decorative ore-deposit block, real target of
 * {@link thaumcraft.common.blocks.world.taint.BlockTaint}'s own rock-variant {@code die()} once corruption
 * recedes. Real weighted drop, faithfully ported from the original's own two-stage {@code getDrops}: a real
 * 1-in-15 chance of a "special" item (an iron/gold/cinnabar cluster, a real compound-{@link Aspect}-tagged
 * crystal essence - weighted 100 for {@link Aspect#FLUX}, 1 for every other real compound aspect, matching
 * the original's own {@code isPrimal()?20:1} branch collapsing to always 1 here since {@link
 * Aspect#getCompoundAspects} only ever returns non-primal aspects - or a real handful of vanilla minerals),
 * otherwise plain gravel. Uses {@link #playerWillDestroy} (real, hand-rolled, matching the original's own
 * real Java {@code getDrops} override) rather than a data-driven loot table, same real established pattern
 * {@code BlockTaint}'s own bonus crystal drop already used - a static JSON loot table can't dynamically
 * enumerate real compound aspects the way this needs to.
 *
 * Real, documented cuts vs the original: the copper/tin/silver/lead cluster entries are dropped entirely -
 * this port has no cluster items for those four ores at all yet (only {@code clusterIron}/{@code
 * clusterGold}/{@code clusterCinnabar}/{@code clusterQuartz} are ported, and quartz isn't even in this
 * specific table in the original either), not a simplification of an existing mechanic so much as those
 * four items not existing yet to drop. No fortune scaling - the original read it from the breaking tool's
 * real enchantment level; this port doesn't look that up here, a real, minor, quick-to-restore simplification
 * whenever fortune-aware drops get a real pass across this project (matches the same simplification {@code
 * BlockTaintFibre}'s own drop chance already carries).
 */
public class BlockStonePorous extends Block {

	private static List<WeightedEntry> pool;

	public BlockStonePorous(BlockBehaviour.Properties properties) {
		super(properties);
	}

	private record WeightedEntry(int weight, Supplier<ItemStack> supplier) {
	}

	private static List<WeightedEntry> pool() {
		if (pool == null) {
			List<WeightedEntry> list = new ArrayList<>();
			list.add(new WeightedEntry(20, () -> new ItemStack(ItemsTC.clusterIron.get())));
			list.add(new WeightedEntry(10, () -> new ItemStack(ItemsTC.clusterGold.get())));
			list.add(new WeightedEntry(10, () -> new ItemStack(ItemsTC.clusterCinnabar.get())));
			list.add(new WeightedEntry(2, () -> new ItemStack(Items.DIAMOND)));
			list.add(new WeightedEntry(4, () -> new ItemStack(Items.EMERALD)));
			list.add(new WeightedEntry(8, () -> new ItemStack(Items.REDSTONE)));
			list.add(new WeightedEntry(3, () -> new ItemStack(Items.PRISMARINE_CRYSTALS)));
			list.add(new WeightedEntry(3, () -> new ItemStack(Items.PRISMARINE_SHARD)));
			list.add(new WeightedEntry(30, () -> new ItemStack(Items.CLAY_BALL)));
			list.add(new WeightedEntry(15, () -> new ItemStack(Items.QUARTZ)));
			for (Aspect aspect : Aspect.getCompoundAspects()) {
				int weight = aspect == Aspect.FLUX ? 100 : 1;
				list.add(new WeightedEntry(weight, () -> {
					ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
					((ItemCrystalEssence) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
					return stack;
				}));
			}
			pool = list;
		}
		return pool;
	}

	private static ItemStack rollDrop(net.minecraft.util.RandomSource random) {
		if (random.nextInt(15) <= 13) {
			return new ItemStack(Blocks.GRAVEL);
		}
		List<WeightedEntry> entries = pool();
		int total = entries.stream().mapToInt(WeightedEntry::weight).sum();
		int roll = random.nextInt(total);
		for (WeightedEntry entry : entries) {
			if (roll < entry.weight()) {
				return entry.supplier().get();
			}
			roll -= entry.weight();
		}
		return new ItemStack(Blocks.GRAVEL);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide) {
			popResource(level, pos, rollDrop(level.random));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
